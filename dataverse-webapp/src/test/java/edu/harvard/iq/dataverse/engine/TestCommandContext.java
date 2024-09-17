package edu.harvard.iq.dataverse.engine;

import edu.harvard.iq.dataverse.DataFileServiceBean;
import edu.harvard.iq.dataverse.DatasetDao;
import edu.harvard.iq.dataverse.DatasetLinkingServiceBean;
import edu.harvard.iq.dataverse.DataverseDao;
import edu.harvard.iq.dataverse.DataverseFacetServiceBean;
import edu.harvard.iq.dataverse.DataverseFieldTypeInputLevelServiceBean;
import edu.harvard.iq.dataverse.DataverseRoleServiceBean;
import edu.harvard.iq.dataverse.DvObjectServiceBean;
import edu.harvard.iq.dataverse.featured.FeaturedDataverseServiceBean;
import edu.harvard.iq.dataverse.MapLayerMetadataServiceBean;
import edu.harvard.iq.dataverse.PermissionServiceBean;
import edu.harvard.iq.dataverse.RoleAssigneeServiceBean;
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
import edu.harvard.iq.dataverse.dataset.DownloadDatasetLogDao;
import edu.harvard.iq.dataverse.dataset.datasetversion.DatasetVersionServiceBean;
import edu.harvard.iq.dataverse.dataverse.template.TemplateDao;
import edu.harvard.iq.dataverse.engine.command.CommandContext;
import edu.harvard.iq.dataverse.globalid.DOIDataCiteServiceBean;
import edu.harvard.iq.dataverse.globalid.DOIEZIdServiceBean;
import edu.harvard.iq.dataverse.globalid.FakePidProviderServiceBean;
import edu.harvard.iq.dataverse.globalid.GlobalIdServiceBeanResolver;
import edu.harvard.iq.dataverse.globalid.HandlenetServiceBean;
import edu.harvard.iq.dataverse.guestbook.GuestbookResponseServiceBean;
import edu.harvard.iq.dataverse.guestbook.GuestbookServiceBean;
import edu.harvard.iq.dataverse.ingest.IngestServiceBean;
import edu.harvard.iq.dataverse.notification.UserNotificationService;
import edu.harvard.iq.dataverse.privateurl.PrivateUrlServiceBean;
import edu.harvard.iq.dataverse.search.SearchServiceBean;
import edu.harvard.iq.dataverse.search.index.IndexBatchServiceBean;
import edu.harvard.iq.dataverse.search.index.IndexServiceBean;
import edu.harvard.iq.dataverse.search.index.SolrIndexServiceBean;
import edu.harvard.iq.dataverse.search.savedsearch.SavedSearchServiceBean;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import edu.harvard.iq.dataverse.util.SystemConfig;
import edu.harvard.iq.dataverse.validation.DatasetFieldValidationService;
import edu.harvard.iq.dataverse.workflow.WorkflowServiceBean;
import edu.harvard.iq.dataverse.workflow.execution.WorkflowExecutionFacade;

import javax.persistence.EntityManager;

/**
 * A base CommandContext for tests. Provides no-op implementations. Should
 * probably be overridden for actual tests.
 *
 * @author michael
 */
public class TestCommandContext implements CommandContext {

    private TestSettingsServiceBean settings = new TestSettingsServiceBean();

    @Override
    public DatasetDao datasets() {
        return null;
    }

    @Override
    public DataverseDao dataverses() {
        return null;
    }

    @Override
    public DataAccess dataAccess() {
        return null;
    }

    @Override
    public DataverseRoleServiceBean roles() {
        return null;
    }

    @Override
    public BuiltinUserServiceBean builtinUsers() {
        return null;
    }

    @Override
    public IndexServiceBean index() {
        return null;
    }

    @Override
    public IndexBatchServiceBean indexBatch() {
        return null;
    }

    @Override
    public SolrIndexServiceBean solrIndex() {
        return null;
    }

    @Override
    public SearchServiceBean search() {
        return null;
    }

    @Override
    public IngestServiceBean ingest() {
        return null;
    }

    @Override
    public PermissionServiceBean permissions() {
        return null;
    }

    @Override
    public DvObjectServiceBean dvObjects() {
        return null;
    }

    @Override
    public EntityManager em() {
        return null;
    }

    @Override
    public DataverseFacetServiceBean facets() {
        return null;
    }

    @Override
    public FeaturedDataverseServiceBean featuredDataverses() {
        return null;
    }

    @Override
    public TemplateDao templates() {
        return null;
    }

    @Override
    public SavedSearchServiceBean savedSearches() {
        return null;
    }

    @Override
    public DataverseFieldTypeInputLevelServiceBean fieldTypeInputLevels() {
        return null;
    }

    @Override
    public DOIEZIdServiceBean doiEZId() {
        return null;
    }

    @Override
    public DOIDataCiteServiceBean doiDataCite() {
        return null;
    }

    @Override
    public FakePidProviderServiceBean fakePidProvider() {
        return null;
    }

    @Override
    public HandlenetServiceBean handleNet() {
        return null;
    }

    @Override
    public SettingsServiceBean settings() {
        return settings;
    }

    @Override
    public GuestbookServiceBean guestbooks() {
        return null;
    }

    @Override
    public GuestbookResponseServiceBean responses() {
        return null;
    }

    @Override
    public DatasetLinkingServiceBean dsLinking() {
        return null;
    }

    @Override
    public AuthenticationServiceBean authentication() {
        return null;
    }

    @Override
    public DataverseEngine engine() {
        return new TestDataverseEngine(this);
    }

    @Override
    public DataFileServiceBean files() {
        return null;
    }

    @Override
    public ExplicitGroupServiceBean explicitGroups() {
        return null;
    }

    @Override
    public GroupServiceBean groups() {
        return null;
    }

    @Override
    public RoleAssigneeServiceBean roleAssignees() {
        return null;
    }

    @Override
    public UserNotificationService notifications() {
        return null;
    }

    @Override
    public SystemConfig systemConfig() {
        return null;
    }

    @Override
    public PrivateUrlServiceBean privateUrl() {
        return null;
    }

    @Override
    public DatasetVersionServiceBean datasetVersion() {
        return null;
    }

    @Override
    public WorkflowServiceBean workflows() {
        return null;
    }

    @Override
    public WorkflowExecutionFacade workflowExecutions() {
        return null;
    }

    @Override
    public MapLayerMetadataServiceBean mapLayerMetadata() {
        return null;
    }

    @Override
    public DataCaptureModuleServiceBean dataCaptureModule() {
        return null;
    }

    @Override
    public FileDownloadServiceBean fileDownload() {
        return null;
    }

    @Override
    public DataverseTextMessageServiceBean dataverseTextMessages() {
        return null;
    }

    @Override
    public DatasetThumbnailService datasetThumailService() {
        return null;
    }

    @Override
    public CitationFactory citationFactory() {
        return null;
    }

    @Override
    public DownloadDatasetLogDao downloadDatasetDao() {
        return null;
    }

    @Override
    public DatasetFieldValidationService fieldValidationService() {
        return null;
    }

    @Override
    public GlobalIdServiceBeanResolver globalIdServiceBeanResolver() {
        return null;
    }
}
