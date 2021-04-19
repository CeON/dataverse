package edu.harvard.iq.dataverse;

import edu.harvard.iq.dataverse.actionlogging.ActionLogServiceBean;
import edu.harvard.iq.dataverse.authorization.AuthenticationServiceBean;
import edu.harvard.iq.dataverse.authorization.groups.GroupServiceBean;
import edu.harvard.iq.dataverse.authorization.groups.impl.explicit.ExplicitGroupServiceBean;
import edu.harvard.iq.dataverse.authorization.providers.builtin.BuiltinUserServiceBean;
import edu.harvard.iq.dataverse.bannersandmessages.messages.DataverseTextMessageServiceBean;
import edu.harvard.iq.dataverse.citation.CitationFactory;
import edu.harvard.iq.dataverse.dataaccess.DataAccess;
import edu.harvard.iq.dataverse.datacapturemodule.DataCaptureModuleServiceBean;
import edu.harvard.iq.dataverse.datafile.FileDownloadServiceBean;
import edu.harvard.iq.dataverse.dataset.DatasetThumbnailService;
import edu.harvard.iq.dataverse.dataset.datasetversion.DatasetVersionServiceBean;
import edu.harvard.iq.dataverse.dataverse.template.TemplateDao;
import edu.harvard.iq.dataverse.engine.DataverseEngine;
import edu.harvard.iq.dataverse.engine.command.Command;
import edu.harvard.iq.dataverse.engine.command.CommandContext;
import edu.harvard.iq.dataverse.engine.command.DataverseRequest;
import edu.harvard.iq.dataverse.engine.command.exception.CommandException;
import edu.harvard.iq.dataverse.engine.command.exception.PermissionException;
import edu.harvard.iq.dataverse.guestbook.GuestbookResponseServiceBean;
import edu.harvard.iq.dataverse.guestbook.GuestbookServiceBean;
import edu.harvard.iq.dataverse.ingest.IngestServiceBean;
import edu.harvard.iq.dataverse.notification.UserNotificationService;
import edu.harvard.iq.dataverse.persistence.ActionLogRecord;
import edu.harvard.iq.dataverse.persistence.DvObject;
import edu.harvard.iq.dataverse.persistence.user.Permission;
import edu.harvard.iq.dataverse.pidproviders.FakePidProviderServiceBean;
import edu.harvard.iq.dataverse.privateurl.PrivateUrlServiceBean;
import edu.harvard.iq.dataverse.search.SearchServiceBean;
import edu.harvard.iq.dataverse.search.index.IndexBatchServiceBean;
import edu.harvard.iq.dataverse.search.index.IndexServiceBean;
import edu.harvard.iq.dataverse.search.index.SolrIndexServiceBean;
import edu.harvard.iq.dataverse.search.savedsearch.SavedSearchServiceBean;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import edu.harvard.iq.dataverse.util.SystemConfig;
import edu.harvard.iq.dataverse.workflow.WorkflowServiceBean;
import edu.harvard.iq.dataverse.workflow.execution.WorkflowExecutionFacade;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.SetUtils;

import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.EJBContext;
import javax.ejb.EJBException;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static javax.ejb.TransactionAttributeType.REQUIRES_NEW;

/**
 * An EJB capable of executing {@link Command}s in a JEE environment.
 *
 * @author michael
 */
@Stateless
public class EjbDataverseEngine {

    private static final Logger logger = Logger.getLogger(EjbDataverseEngine.class.getCanonicalName());

    @EJB
    DatasetDao datasetDao;

    @EJB
    DataverseDao dataverseDao;

    @EJB
    DataverseRoleServiceBean rolesService;

    @EJB
    BuiltinUserServiceBean usersService;

    @EJB
    IndexServiceBean indexService;

    @EJB
    IndexBatchServiceBean indexBatchService;

    @EJB
    SolrIndexServiceBean solrIndexService;

    @EJB
    SearchServiceBean searchService;

    @EJB
    IngestServiceBean ingestService;

    @EJB
    PermissionServiceBean permissionService;

    @EJB
    DvObjectServiceBean dvObjectService;

    @EJB
    DataverseFacetServiceBean dataverseFacetService;

    @EJB
    FeaturedDataverseServiceBean featuredDataverseService;

    @EJB
    DataFileServiceBean dataFileService;

    @EJB
    TemplateDao templateDao;

    @EJB
    SavedSearchServiceBean savedSearchService;

    @EJB
    DataverseFieldTypeInputLevelServiceBean fieldTypeInputLevels;

    @EJB
    DOIEZIdServiceBean doiEZId;

    @EJB
    DOIDataCiteServiceBean doiDataCite;

    @EJB
    FakePidProviderServiceBean fakePidProvider;

    @EJB
    HandlenetServiceBean handleNet;

    @Inject
    SettingsServiceBean settings;

    @EJB
    GuestbookServiceBean guestbookService;

    @EJB
    GuestbookResponseServiceBean responses;

    @EJB
    DatasetLinkingServiceBean dsLinking;

    @EJB
    ExplicitGroupServiceBean explicitGroups;

    @EJB
    GroupServiceBean groups;

    @EJB
    RoleAssigneeServiceBean roleAssignees;

    @EJB
    UserNotificationService userNotificationService;

    @EJB
    AuthenticationServiceBean authentication;

    @EJB
    SystemConfig systemConfig;

    @EJB
    PrivateUrlServiceBean privateUrlService;

    @EJB
    DatasetVersionServiceBean datasetVersionService;

    @EJB
    MapLayerMetadataServiceBean mapLayerMetadata;

    @EJB
    DataCaptureModuleServiceBean dataCaptureModule;

    @PersistenceContext(unitName = "VDCNet-ejbPU")
    private EntityManager em;

    @EJB
    ActionLogServiceBean logSvc;

    @Inject
    WorkflowServiceBean workflowService;

    @Inject
    WorkflowExecutionFacade workflowExecutionFacade;

    @EJB
    FileDownloadServiceBean fileDownloadService;

    @EJB
    DataverseTextMessageServiceBean dataverseTextMessageServiceBean;

    @Inject
    DatasetThumbnailService datasetThumbnailService;

    @Inject
    CitationFactory citationFactory;


    @Resource
    EJBContext ejbCtxt;

    private DataAccess dataAccess = DataAccess.dataAccess();

    private CommandContext ctxt;

    @TransactionAttribute(REQUIRES_NEW)
    public <R> R submitInNewTransaction(Command<R> aCommand)  {
        return submit(aCommand);
    }

    public <R> R submit(Command<R> aCommand)  {

        final ActionLogRecord logRec = new ActionLogRecord(ActionLogRecord.ActionType.Command, aCommand.getClass().getCanonicalName());

        try {
            logRec.setUserIdentifier(aCommand.getRequest().getUser().getIdentifier());

            // Check permissions - or throw an exception
            Map<String, ? extends Set<Permission>> requiredMap = aCommand.getRequiredPermissions();
            if (requiredMap == null) {
                throw new RuntimeException("Command " + aCommand + " does not define required permissions.");
            }

            DataverseRequest dvReq = aCommand.getRequest();

            Map<String, DvObject> affectedDvObjects = aCommand.getAffectedDvObjects();
            logRec.setInfo(aCommand.describe());
            for (Map.Entry<String, ? extends Set<Permission>> pair : requiredMap.entrySet()) {
                String dvName = pair.getKey();
                if (!affectedDvObjects.containsKey(dvName)) {
                    throw new RuntimeException("Command instance " + aCommand + " does not have a DvObject named '" + dvName + "'");
                }
                DvObject dvo = affectedDvObjects.get(dvName);

                Set<Permission> granted = (dvo != null) ? permissionService.permissionsFor(dvReq, dvo)
                        : EnumSet.allOf(Permission.class);
                Set<Permission> required = requiredMap.get(dvName);

                if ((!aCommand.isAllPermissionsRequired() && !CollectionUtils.containsAny(granted, required) ||
                        (aCommand.isAllPermissionsRequired() && !granted.containsAll(required)))) {
                    Set<Permission> missingPermissions = SetUtils.difference(required, granted);
                    logRec.setActionResult(ActionLogRecord.Result.PermissionError);
                    /**
                     * @todo Is there any harm in showing the "granted" set
                     * since we already show "required"? It would help people
                     * reason about the mismatch.
                     */
                    throw new PermissionException("Can't execute command " + aCommand
                                                          + ", because request " + aCommand.getRequest()
                                                          + " is missing permissions " + missingPermissions
                                                          + " on Object " + dvo.accept(DvObject.NamePrinter),
                                                  aCommand,
                                                  missingPermissions, dvo);
                }
            }
            try {
                return aCommand.execute(getContext());

            } catch (EJBException ejbe) {
                throw new CommandException("Command " + aCommand.toString() + " failed: " + ejbe.getMessage(), ejbe.getCausedByException(), aCommand);
            }
        } catch (CommandException cmdEx) {
            if (!(cmdEx instanceof PermissionException)) {
                logRec.setActionResult(ActionLogRecord.Result.InternalError);
            }
            logRec.setInfo(logRec.getInfo() + " (" + cmdEx.getMessage() + ")");
            throw cmdEx;
        } catch (RuntimeException re) {
            logRec.setActionResult(ActionLogRecord.Result.InternalError);
            logRec.setInfo(logRec.getInfo() + " (" + re.getMessage() + ")");

            Throwable cause = re;
            while (cause != null) {
                if (cause instanceof ConstraintViolationException) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Unexpected bean validation constraint exception:");
                    ConstraintViolationException constraintViolationException = (ConstraintViolationException) cause;
                    for (ConstraintViolation<?> violation : constraintViolationException.getConstraintViolations()) {
                        sb.append(" Invalid value: <<<").append(violation.getInvalidValue()).append(">>> for ").append(violation.getPropertyPath()).append(" at ").append(violation.getLeafBean()).append(" - ").append(violation.getMessage());
                    }
                    logger.log(Level.SEVERE, sb.toString());
                    // set this more detailed info in action log
                    logRec.setInfo(logRec.getInfo() + " (" + sb.toString() + ")");
                }
                cause = cause.getCause();
            }

            throw re;

        } finally {
            if (logRec.getActionResult() == null) {
                logRec.setActionResult(ActionLogRecord.Result.OK);
            } else {
                ejbCtxt.setRollbackOnly();
            }
            logRec.setEndTime(new java.util.Date());
            logSvc.log(logRec);
        }

    }

    public CommandContext getContext() {
        if (ctxt == null) {
            ctxt = new CommandContext() {

                @Override
                public DatasetDao datasets() {
                    return datasetDao;
                }

                @Override
                public DataverseDao dataverses() {
                    return dataverseDao;
                }

                @Override
                public DataverseRoleServiceBean roles() {
                    return rolesService;
                }

                @Override
                public BuiltinUserServiceBean builtinUsers() {
                    return usersService;
                }

                @Override
                public IndexServiceBean index() {
                    return indexService;
                }

                @Override
                public IndexBatchServiceBean indexBatch() {
                    return indexBatchService;
                }

                @Override
                public SolrIndexServiceBean solrIndex() {
                    return solrIndexService;
                }

                @Override
                public SearchServiceBean search() {
                    return searchService;
                }

                @Override
                public IngestServiceBean ingest() {
                    return ingestService;
                }

                @Override
                public PermissionServiceBean permissions() {
                    return permissionService;
                }

                @Override
                public DvObjectServiceBean dvObjects() {
                    return dvObjectService;
                }

                @Override
                public DataFileServiceBean files() {
                    return dataFileService;
                }

                @Override
                public EntityManager em() {
                    return em;
                }

                @Override
                public DataAccess dataAccess() {
                    return dataAccess;
                }

                @Override
                public DataverseFacetServiceBean facets() {
                    return dataverseFacetService;
                }

                @Override
                public FeaturedDataverseServiceBean featuredDataverses() {
                    return featuredDataverseService;
                }

                @Override
                public TemplateDao templates() {
                    return templateDao;
                }

                @Override
                public SavedSearchServiceBean savedSearches() {
                    return savedSearchService;
                }

                @Override
                public DataverseFieldTypeInputLevelServiceBean fieldTypeInputLevels() {
                    return fieldTypeInputLevels;
                }

                @Override
                public DOIEZIdServiceBean doiEZId() {
                    return doiEZId;
                }

                @Override
                public DOIDataCiteServiceBean doiDataCite() {
                    return doiDataCite;
                }

                @Override
                public FakePidProviderServiceBean fakePidProvider() {
                    return fakePidProvider;
                }

                @Override
                public HandlenetServiceBean handleNet() {
                    return handleNet;
                }

                @Override
                public SettingsServiceBean settings() {
                    return settings;
                }

                @Override
                public GuestbookServiceBean guestbooks() {
                    return guestbookService;
                }

                @Override
                public GuestbookResponseServiceBean responses() {
                    return responses;
                }

                @Override
                public DatasetLinkingServiceBean dsLinking() {
                    return dsLinking;
                }

                @Override
                public DataverseEngine engine() {
                    return new DataverseEngine() {
                        @Override
                        public <R> R submit(Command<R> aCommand)  {
                            return EjbDataverseEngine.this.submit(aCommand);
                        }
                    };
                }

                @Override
                public ExplicitGroupServiceBean explicitGroups() {
                    return explicitGroups;
                }

                @Override
                public GroupServiceBean groups() {
                    return groups;
                }

                @Override
                public RoleAssigneeServiceBean roleAssignees() {
                    return roleAssignees;
                }

                @Override
                public UserNotificationService notifications() {
                    return userNotificationService;
                }

                @Override
                public AuthenticationServiceBean authentication() {
                    return authentication;
                }

                @Override
                public SystemConfig systemConfig() {
                    return systemConfig;
                }

                @Override
                public PrivateUrlServiceBean privateUrl() {
                    return privateUrlService;
                }

                @Override
                public DatasetVersionServiceBean datasetVersion() {
                    return datasetVersionService;
                }

                @Override
                public WorkflowServiceBean workflows() {
                    return workflowService;
                }

                @Override
                public WorkflowExecutionFacade workflowExecutions() {
                    return workflowExecutionFacade;
                }

                @Override
                public MapLayerMetadataServiceBean mapLayerMetadata() {
                    return mapLayerMetadata;
                }

                @Override
                public DataCaptureModuleServiceBean dataCaptureModule() {
                    return dataCaptureModule;
                }

                @Override
                public FileDownloadServiceBean fileDownload() {
                    return fileDownloadService;
                }

                @Override
                public DataverseTextMessageServiceBean dataverseTextMessages() {
                    return dataverseTextMessageServiceBean;
                }

                @Override
                public DatasetThumbnailService datasetThumailService() {
                    return datasetThumbnailService;
                }

                @Override
                public CitationFactory citationFactory() {
                    return citationFactory;
                }
            };
        }

        return ctxt;
    }

}
