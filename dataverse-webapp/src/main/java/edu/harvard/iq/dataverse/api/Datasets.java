package edu.harvard.iq.dataverse.api;

import edu.harvard.iq.dataverse.DataFileServiceBean;
import edu.harvard.iq.dataverse.DatasetDao;
import edu.harvard.iq.dataverse.DatasetFieldServiceBean;
import edu.harvard.iq.dataverse.DataverseDao;
import edu.harvard.iq.dataverse.DataverseSession;
import edu.harvard.iq.dataverse.EjbDataverseEngine;
import edu.harvard.iq.dataverse.MetadataBlockDao;
import edu.harvard.iq.dataverse.PermissionServiceBean;
import edu.harvard.iq.dataverse.S3PackageImporter;
import edu.harvard.iq.dataverse.api.dto.SubmitForReviewDataDTO;
import edu.harvard.iq.dataverse.authorization.AuthenticationServiceBean;
import edu.harvard.iq.dataverse.batch.jobs.importer.ImportMode;
import edu.harvard.iq.dataverse.common.BundleUtil;
import edu.harvard.iq.dataverse.dataaccess.DataAccess;
import edu.harvard.iq.dataverse.datacapturemodule.DataCaptureModuleUtil;
import edu.harvard.iq.dataverse.datacapturemodule.ScriptRequestResponse;
import edu.harvard.iq.dataverse.dataset.DatasetService;
import edu.harvard.iq.dataverse.dataset.DatasetThumbnail;
import edu.harvard.iq.dataverse.dataset.DatasetUtil;
import edu.harvard.iq.dataverse.datasetutility.AddReplaceFileHelper;
import edu.harvard.iq.dataverse.datasetutility.DataFileTagException;
import edu.harvard.iq.dataverse.datasetutility.NoFilesException;
import edu.harvard.iq.dataverse.datasetutility.OptionalFileParams;
import edu.harvard.iq.dataverse.engine.command.Command;
import edu.harvard.iq.dataverse.engine.command.DataverseRequest;
import edu.harvard.iq.dataverse.engine.command.exception.CommandException;
import edu.harvard.iq.dataverse.engine.command.exception.NoDatasetFilesException;
import edu.harvard.iq.dataverse.engine.command.exception.PermissionException;
import edu.harvard.iq.dataverse.engine.command.impl.AbstractSubmitToArchiveCommand;
import edu.harvard.iq.dataverse.engine.command.impl.AddLockCommand;
import edu.harvard.iq.dataverse.engine.command.impl.AssignRoleCommand;
import edu.harvard.iq.dataverse.engine.command.impl.CreateDatasetVersionCommand;
import edu.harvard.iq.dataverse.engine.command.impl.CreatePrivateUrlCommand;
import edu.harvard.iq.dataverse.engine.command.impl.CuratePublishedDatasetVersionCommand;
import edu.harvard.iq.dataverse.engine.command.impl.DeleteDatasetCommand;
import edu.harvard.iq.dataverse.engine.command.impl.DeleteDatasetLinkingDataverseCommand;
import edu.harvard.iq.dataverse.engine.command.impl.DeleteDatasetVersionCommand;
import edu.harvard.iq.dataverse.engine.command.impl.DeletePrivateUrlCommand;
import edu.harvard.iq.dataverse.engine.command.impl.DestroyDatasetCommand;
import edu.harvard.iq.dataverse.engine.command.impl.GetDatasetCommand;
import edu.harvard.iq.dataverse.engine.command.impl.GetDraftDatasetVersionCommand;
import edu.harvard.iq.dataverse.engine.command.impl.GetLatestAccessibleDatasetVersionCommand;
import edu.harvard.iq.dataverse.engine.command.impl.GetLatestPublishedDatasetVersionCommand;
import edu.harvard.iq.dataverse.engine.command.impl.GetPrivateUrlCommand;
import edu.harvard.iq.dataverse.engine.command.impl.GetSpecificPublishedDatasetVersionCommand;
import edu.harvard.iq.dataverse.engine.command.impl.ImportFromFileSystemCommand;
import edu.harvard.iq.dataverse.engine.command.impl.LinkDatasetCommand;
import edu.harvard.iq.dataverse.engine.command.impl.ListRoleAssignments;
import edu.harvard.iq.dataverse.engine.command.impl.ListVersionsCommand;
import edu.harvard.iq.dataverse.engine.command.impl.MoveDatasetCommand;
import edu.harvard.iq.dataverse.engine.command.impl.PublishDatasetCommand;
import edu.harvard.iq.dataverse.engine.command.impl.PublishDatasetResult;
import edu.harvard.iq.dataverse.engine.command.impl.RemoveLockCommand;
import edu.harvard.iq.dataverse.engine.command.impl.RequestRsyncScriptCommand;
import edu.harvard.iq.dataverse.engine.command.impl.ReturnDatasetToAuthorCommand;
import edu.harvard.iq.dataverse.engine.command.impl.SetDatasetCitationDateCommand;
import edu.harvard.iq.dataverse.engine.command.impl.SubmitDatasetForReviewCommand;
import edu.harvard.iq.dataverse.engine.command.impl.UpdateDatasetTargetURLCommand;
import edu.harvard.iq.dataverse.engine.command.impl.UpdateDatasetThumbnailCommand;
import edu.harvard.iq.dataverse.engine.command.impl.UpdateDatasetVersionCommand;
import edu.harvard.iq.dataverse.engine.command.impl.UpdateDvObjectPIDMetadataCommand;
import edu.harvard.iq.dataverse.error.DataverseError;
import edu.harvard.iq.dataverse.export.DDIExportServiceBean;
import edu.harvard.iq.dataverse.export.ExportService;
import edu.harvard.iq.dataverse.export.ExporterType;
import edu.harvard.iq.dataverse.ingest.IngestServiceBean;
import edu.harvard.iq.dataverse.license.TermsOfUseFactory;
import edu.harvard.iq.dataverse.license.TermsOfUseFormMapper;
import edu.harvard.iq.dataverse.notification.NotificationObjectType;
import edu.harvard.iq.dataverse.notification.UserNotificationService;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.dataset.ControlledVocabularyValue;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetField;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetFieldType;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetFieldUtil;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetLock;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.dataset.MetadataBlock;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.persistence.user.DataverseRole;
import edu.harvard.iq.dataverse.persistence.user.NotificationType;
import edu.harvard.iq.dataverse.persistence.user.Permission;
import edu.harvard.iq.dataverse.persistence.user.RoleAssignee;
import edu.harvard.iq.dataverse.persistence.user.User;
import edu.harvard.iq.dataverse.privateurl.PrivateUrl;
import edu.harvard.iq.dataverse.search.index.IndexServiceBean;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import edu.harvard.iq.dataverse.util.ArchiverUtil;
import edu.harvard.iq.dataverse.util.EjbUtil;
import edu.harvard.iq.dataverse.util.SystemConfig;
import edu.harvard.iq.dataverse.util.json.JsonParseException;
import io.vavr.control.Either;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;

import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static edu.harvard.iq.dataverse.util.json.JsonPrinter.json;
import static edu.harvard.iq.dataverse.util.json.JsonPrinter.jsonByBlocks;
import static edu.harvard.iq.dataverse.util.json.JsonPrinter.jsonFileMetadatas;
import static edu.harvard.iq.dataverse.util.json.JsonPrinter.toJsonArray;

@Path("datasets")
public class Datasets extends AbstractApiBean {

    private static final Logger logger = Logger.getLogger(Datasets.class.getCanonicalName());

    @Inject
    DataverseSession session;

    @EJB
    DatasetDao datasetDao;

    @EJB
    DataverseDao dataverseDao;

    @EJB
    UserNotificationService userNotificationService;

    @EJB
    PermissionServiceBean permissionService;

    @EJB
    AuthenticationServiceBean authenticationServiceBean;

    @EJB
    DDIExportServiceBean ddiExportService;

    @EJB
    DatasetFieldServiceBean datasetfieldService;

    @EJB
    MetadataBlockDao metadataBlockService;

    @EJB
    DataFileServiceBean fileService;

    @EJB
    IngestServiceBean ingestService;

    @EJB
    EjbDataverseEngine commandEngine;

    @EJB
    IndexServiceBean indexService;

    @EJB
    S3PackageImporter s3PackageImporter;

    @Inject
    SettingsServiceBean settingsService;

    @Inject
    private ExportService exportService;

    @Inject
    private DatasetService datasetSvc;

    /**
     * Used to consolidate the way we parse and handle dataset versions.
     *
     * @param <T>
     */
    private interface DsVersionHandler<T> {
        T handleLatest();

        T handleDraft();

        T handleSpecific(long major, long minor);

        T handleLatestPublished();
    }

    @GET
    @Path("{id}")
    public Response getDataset(@PathParam("id") String id) {
        return response(req -> {
            final Dataset retrieved = execCommand(new GetDatasetCommand(req, findDatasetOrDie(id)));
            final DatasetVersion latest = execCommand(new GetLatestAccessibleDatasetVersionCommand(req, retrieved));
            final JsonObjectBuilder jsonbuilder = json(retrieved);

            return allowCors(ok(jsonbuilder.add("latestVersion", (latest != null) ?
                    json(latest, settingsService.isTrueForKey(SettingsServiceBean.Key.ExcludeEmailFromExport))
                    : null)));
        });
    }

    // TODO: 
    // This API call should, ideally, call findUserOrDie() and the GetDatasetCommand 
    // to obtain the dataset that we are trying to export - which would handle
    // Auth in the process... For now, Auth isn't necessary - since export ONLY 
    // WORKS on published datasets, which are open to the world. -- L.A. 4.5

    @GET
    @Path("/export")
    @Produces({"application/xml", "application/json"})
    public Response exportDataset(@QueryParam("persistentId") String persistentId, @QueryParam("exporter") String exporter) {

        Optional<ExporterType> exporterConstant = ExporterType.fromString(exporter);

        if (!exporterConstant.isPresent()) {
            return error(Response.Status.BAD_REQUEST, exporter + " is not a valid exporter");
        }

        Dataset dataset = datasetDao.findByGlobalId(persistentId);
        if (dataset == null) {
            return error(Response.Status.NOT_FOUND,
                         "A dataset with the persistentId " + persistentId + " could not be found.");
        }

        Either<DataverseError, String> exportedDataset = exportService.exportDatasetVersionAsString(dataset.getReleasedVersion(),
                                                                                                    exporterConstant.get());

        if (exportedDataset.isLeft()) {
            return error(Response.Status.FORBIDDEN, exportedDataset.getLeft().getErrorMsg());
        }

        String mediaType = exportService.getMediaType(exporterConstant.get());
        return allowCors(Response.ok()
                                 .entity(exportedDataset.get())
                                 .type(mediaType).
                        build());
    }

    @DELETE
    @Path("{id}")
    public Response deleteDataset(@PathParam("id") String id) {
        // Internally, "DeleteDatasetCommand" simply redirects to "DeleteDatasetVersionCommand"
        // (and there's a comment that says "TODO: remove this command")
        // do we need an exposed API call for it? 
        // And DeleteDatasetVersionCommand further redirects to DestroyDatasetCommand, 
        // if the dataset only has 1 version... In other words, the functionality 
        // currently provided by this API is covered between the "deleteDraftVersion" and
        // "destroyDataset" API calls.  
        // (The logic below follows the current implementation of the underlying 
        // commands!)

        return response(req -> {
            Dataset doomed = findDatasetOrDie(id);
            DatasetVersion doomedVersion = doomed.getLatestVersion();
            User u = findUserOrDie();
            boolean destroy = false;

            if (doomed.getVersions().size() == 1) {
                if (doomed.isReleased() && (!(u instanceof AuthenticatedUser) || !u.isSuperuser())) {
                    throw new WrappedResponse(error(Response.Status.UNAUTHORIZED,
                                                    "Only superusers can delete published datasets"));
                }
                destroy = true;
            } else {
                if (!doomedVersion.isDraft()) {
                    throw new WrappedResponse(error(Response.Status.UNAUTHORIZED,
                                                    "This is a published dataset with multiple versions. This API can only delete the latest version if it is a DRAFT"));
                }
            }

            // Gather the locations of the physical files that will need to be 
            // deleted once the destroy command execution has been finalized:
            Map<Long, String> deleteStorageLocations = fileService.getPhysicalFilesToDelete(doomedVersion, destroy);

            execCommand(new DeleteDatasetCommand(req, findDatasetOrDie(id)));

            // If we have gotten this far, the destroy command has succeeded, 
            // so we can finalize it by permanently deleting the physical files:
            // (DataFileService will double-check that the datafiles no 
            // longer exist in the database, before attempting to delete 
            // the physical files)
            if (!deleteStorageLocations.isEmpty()) {
                fileService.finalizeFileDeletes(deleteStorageLocations);
            }

            return ok("Dataset " + id + " deleted");
        });
    }

    @DELETE
    @Path("{id}/destroy")
    public Response destroyDataset(@PathParam("id") String id) {

        return response(req -> {
            // first check if dataset is released, and if so, if user is a superuser
            Dataset doomed = findDatasetOrDie(id);
            User u = findUserOrDie();

            if (doomed.isReleased() && (!(u instanceof AuthenticatedUser) || !u.isSuperuser())) {
                throw new WrappedResponse(error(Response.Status.UNAUTHORIZED,
                                                "Destroy can only be called by superusers."));
            }

            // Gather the locations of the physical files that will need to be 
            // deleted once the destroy command execution has been finalized:
            Map<Long, String> deleteStorageLocations = fileService.getPhysicalFilesToDelete(doomed);

            execCommand(new DestroyDatasetCommand(doomed, req));

            // If we have gotten this far, the destroy command has succeeded, 
            // so we can finalize permanently deleting the physical files:
            // (DataFileService will double-check that the datafiles no 
            // longer exist in the database, before attempting to delete 
            // the physical files)
            if (!deleteStorageLocations.isEmpty()) {
                fileService.finalizeFileDeletes(deleteStorageLocations);
            }

            return ok("Dataset " + id + " destroyed");
        });
    }

    @DELETE
    @Path("{id}/versions/{versionId}")
    public Response deleteDraftVersion(@PathParam("id") String id, @PathParam("versionId") String versionId) {
        if (!":draft".equals(versionId)) {
            return badRequest("Only the :draft version can be deleted");
        }

        return response(req -> {
            Dataset dataset = findDatasetOrDie(id);
            DatasetVersion doomed = dataset.getLatestVersion();

            if (!doomed.isDraft()) {
                throw new WrappedResponse(error(Response.Status.UNAUTHORIZED, "This is NOT a DRAFT version"));
            }

            // Gather the locations of the physical files that will need to be 
            // deleted once the destroy command execution has been finalized:

            Map<Long, String> deleteStorageLocations = fileService.getPhysicalFilesToDelete(doomed);

            execCommand(new DeleteDatasetVersionCommand(req, dataset));

            // If we have gotten this far, the delete command has succeeded - 
            // by either deleting the Draft version of a published dataset, 
            // or destroying an unpublished one. 
            // This means we can finalize permanently deleting the physical files:
            // (DataFileService will double-check that the datafiles no 
            // longer exist in the database, before attempting to delete 
            // the physical files)
            if (!deleteStorageLocations.isEmpty()) {
                fileService.finalizeFileDeletes(deleteStorageLocations);
            }

            return ok("Draft version of dataset " + id + " deleted");
        });
    }

    @DELETE
    @Path("{datasetId}/deleteLink/{linkedDataverseId}")
    public Response deleteDatasetLinkingDataverse(@PathParam("datasetId") String datasetId, @PathParam("linkedDataverseId") String linkedDataverseId) {
        boolean index = true;
        return response(req -> {
            execCommand(new DeleteDatasetLinkingDataverseCommand(req,
                                                                 findDatasetOrDie(datasetId),
                                                                 findDatasetLinkingDataverseOrDie(datasetId,
                                                                                                  linkedDataverseId),
                                                                 index));
            return ok("Link from Dataset " + datasetId + " to linked Dataverse " + linkedDataverseId + " deleted");
        });
    }

    @PUT
    @Path("{id}/citationdate")
    public Response setCitationDate(@PathParam("id") String id, String dsfTypeName) {
        return response(req -> {
            if (dsfTypeName.trim().isEmpty()) {
                return badRequest("Please provide a dataset field type in the requst body.");
            }
            DatasetFieldType dsfType = null;
            if (!":publicationDate".equals(dsfTypeName)) {
                dsfType = datasetFieldSvc.findByName(dsfTypeName);
                if (dsfType == null) {
                    return badRequest("Dataset Field Type Name " + dsfTypeName + " not found.");
                }
            }

            execCommand(new SetDatasetCitationDateCommand(req, findDatasetOrDie(id), dsfType));
            return ok("Citation Date for dataset " + id + " set to: " + (dsfType != null ?
                    dsfType.getDisplayName() :
                    "default"));
        });
    }

    @DELETE
    @Path("{id}/citationdate")
    public Response useDefaultCitationDate(@PathParam("id") String id) {
        return response(req -> {
            execCommand(new SetDatasetCitationDateCommand(req, findDatasetOrDie(id), null));
            return ok("Citation Date for dataset " + id + " set to default");
        });
    }

    @GET
    @Path("{id}/versions")
    public Response listVersions(@PathParam("id") String id) {
        return allowCors(response(req ->
                                          ok(execCommand(new ListVersionsCommand(req, findDatasetOrDie(id)))
                                                     .stream()
                                                     .map(d -> json(d,
                                                                    settingsService.isTrueForKey(SettingsServiceBean.Key.ExcludeEmailFromExport)))
                                                     .collect(toJsonArray()))));
    }

    @GET
    @Path("{id}/versions/{versionId}")
    public Response getVersion(@PathParam("id") String datasetId, @PathParam("versionId") String versionId) {
        return allowCors(response(req -> {
            DatasetVersion dsv = getDatasetVersionOrDie(req, versionId, findDatasetOrDie(datasetId));
            return (dsv == null || dsv.getId() == null) ? notFound("Dataset version not found")
                    : ok(json(dsv, settingsService.isTrueForKey(SettingsServiceBean.Key.ExcludeEmailFromExport)));
        }));
    }

    @GET
    @Path("{id}/versions/{versionId}/files")
    public Response getVersionFiles(@PathParam("id") String datasetId, @PathParam("versionId") String versionId) {
        return allowCors(response(req -> ok(jsonFileMetadatas(
                getDatasetVersionOrDie(req, versionId, findDatasetOrDie(datasetId)).getFileMetadatas()))));
    }

    @GET
    @Path("{id}/versions/{versionId}/metadata")
    public Response getVersionMetadata(@PathParam("id") String datasetId, @PathParam("versionId") String versionId) {
        return allowCors(response(req -> ok(
                jsonByBlocks(
                        getDatasetVersionOrDie(req, versionId, findDatasetOrDie(datasetId))
                                .getDatasetFields(),
                        settingsService.isTrueForKey(SettingsServiceBean.Key.ExcludeEmailFromExport)))));
    }

    @GET
    @Path("{id}/versions/{versionNumber}/metadata/{block}")
    public Response getVersionMetadataBlock(@PathParam("id") String datasetId,
                                            @PathParam("versionNumber") String versionNumber,
                                            @PathParam("block") String blockName) {

        return allowCors(response(req -> {
            DatasetVersion dsv = getDatasetVersionOrDie(req, versionNumber, findDatasetOrDie(datasetId));

            Map<MetadataBlock, List<DatasetField>> fieldsByBlock = DatasetField.groupByBlock(dsv.getDatasetFields());
            for (Map.Entry<MetadataBlock, List<DatasetField>> p : fieldsByBlock.entrySet()) {
                if (p.getKey().getName().equals(blockName)) {
                    return ok(json(p.getKey(),
                                   p.getValue(),
                                   settingsService.isTrueForKey(SettingsServiceBean.Key.ExcludeEmailFromExport)));
                }
            }
            return notFound("metadata block named " + blockName + " not found");
        }));
    }

    @GET
    @Path("{id}/modifyRegistration")
    public Response updateDatasetTargetURL(@PathParam("id") String id) {
        return response(req -> {
            execCommand(new UpdateDatasetTargetURLCommand(findDatasetOrDie(id), req));
            return ok("Dataset " + id + " target url updated");
        });
    }

    @POST
    @Path("/modifyRegistrationAll")
    public Response updateDatasetTargetURLAll() {
        return response(req -> {
            datasetDao.findAll().forEach(ds -> {
                try {
                    execCommand(new UpdateDatasetTargetURLCommand(findDatasetOrDie(ds.getId().toString()), req));
                } catch (WrappedResponse ex) {
                    Logger.getLogger(Datasets.class.getName()).log(Level.SEVERE, null, ex);
                }
            });
            return ok("Update All Dataset target url completed");
        });
    }

    @POST
    @Path("{id}/modifyRegistrationMetadata")
    public Response updateDatasetPIDMetadata(@PathParam("id") String id) {

        try {
            Dataset dataset = findDatasetOrDie(id);
            if (!dataset.isReleased()) {
                return error(Response.Status.BAD_REQUEST,
                             BundleUtil.getStringFromBundle(
                                     "datasets.api.updatePIDMetadata.failure.dataset.must.be.released"));
            }
        } catch (WrappedResponse ex) {
            Logger.getLogger(Datasets.class.getName()).log(Level.SEVERE, null, ex);
        }

        return response(req -> {
            execCommand(new UpdateDvObjectPIDMetadataCommand(findDatasetOrDie(id), req));
            List<String> args = Arrays.asList(id);
            return ok(BundleUtil.getStringFromBundle("datasets.api.updatePIDMetadata.success.for.single.dataset",
                                                     args));
        });
    }

    @GET
    @Path("/modifyRegistrationPIDMetadataAll")
    public Response updateDatasetPIDMetadataAll() {
        return response(req -> {
            datasetDao.findAll().forEach(ds -> {
                try {
                    execCommand(new UpdateDvObjectPIDMetadataCommand(findDatasetOrDie(ds.getId().toString()), req));
                } catch (WrappedResponse ex) {
                    Logger.getLogger(Datasets.class.getName()).log(Level.SEVERE, null, ex);
                }
            });
            return ok(BundleUtil.getStringFromBundle("datasets.api.updatePIDMetadata.success.for.update.all"));
        });
    }

    @PUT
    @Path("{id}/versions/{versionId}")
    public Response updateDraftVersion(String jsonBody, @PathParam("id") String id, @PathParam("versionId") String versionId) {

        if (!":draft".equals(versionId)) {
            return error(Response.Status.BAD_REQUEST, "Only the :draft version can be updated");
        }

        try (StringReader rdr = new StringReader(jsonBody)) {
            DataverseRequest req = createDataverseRequest(findUserOrDie());
            Dataset ds = findDatasetOrDie(id);
            JsonObject json = Json.createReader(rdr).readObject();
            DatasetVersion incomingVersion = jsonParser().parseDatasetVersion(json);

            // clear possibly stale fields from the incoming dataset version.
            // creation and modification dates are updated by the commands.
            incomingVersion.setId(null);
            incomingVersion.setVersionNumber(null);
            incomingVersion.setMinorVersionNumber(null);
            incomingVersion.setVersionState(DatasetVersion.VersionState.DRAFT);
            incomingVersion.setDataset(ds);
            incomingVersion.setCreateTime(null);
            incomingVersion.setLastUpdateTime(null);
            boolean updateDraft = ds.getLatestVersion().isDraft();

            DatasetVersion managedVersion;
            if (updateDraft) {
                final DatasetVersion editVersion = ds.getEditVersion();
                editVersion.setDatasetFields(incomingVersion.getDatasetFields());
                editVersion.setTermsOfUseAndAccess(incomingVersion.getTermsOfUseAndAccess());
                Dataset managedDataset = execCommand(new UpdateDatasetVersionCommand(ds, req));
                managedVersion = managedDataset.getEditVersion();
            } else {
                managedVersion = execCommand(new CreateDatasetVersionCommand(req, ds, incomingVersion));
            }
//            DatasetVersion managedVersion = execCommand( updateDraft
//                                                             ? new UpdateDatasetVersionCommand(req, incomingVersion)
//                                                             : new CreateDatasetVersionCommand(req, ds, incomingVersion));
            return ok(json(managedVersion,
                           settingsService.isTrueForKey(SettingsServiceBean.Key.ExcludeEmailFromExport)));

        } catch (JsonParseException ex) {
            logger.log(Level.SEVERE, "Semantic error parsing dataset version Json: " + ex.getMessage(), ex);
            return error(Response.Status.BAD_REQUEST, "Error parsing dataset version: " + ex.getMessage());

        } catch (WrappedResponse ex) {
            return ex.getResponse();

        }
    }

    @PUT
    @Path("{id}/deleteMetadata")
    public Response deleteVersionMetadata(String jsonBody, @PathParam("id") String id) throws WrappedResponse {

        DataverseRequest req = createDataverseRequest(findUserOrDie());

        return processDatasetFieldDataDelete(jsonBody, id, req);
    }

    @PUT
    @Path("{id}/setEmbargo")
    public Response setEmbargoDate(@PathParam("id") String id, @QueryParam("date") String date) {
        try {
            Dataset dataset = findDatasetOrDie(id);
            SimpleDateFormat dateFormat = new SimpleDateFormat(settingsService.getValueForKey(SettingsServiceBean.Key.DefaultDateFormat));

            if(date == null) {
                throw new WrappedResponse(badRequest(BundleUtil.getStringFromBundle("datasets.api.setEmbargo.failure.badDate.missing",
                        settingsSvc.getValueForKey(SettingsServiceBean.Key.DefaultDateFormat))));
            }

            Date embargoDate = dateFormat.parse(date);
            validateEmbargoDate(embargoDate);

            dataset = datasetSvc.setDatasetEmbargoDate(dataset, embargoDate);

            return ok(BundleUtil.getStringFromBundle("datasets.api.setEmbargo.success", dataset.getGlobalId(), dataset.getEmbargoDate().get().toInstant()));
        } catch (WrappedResponse wr) {
            return wr.getResponse();
        } catch (ParseException pe) {
            return badRequest(BundleUtil.getStringFromBundle("datasets.api.setEmbargo.failure.badDate.format", settingsSvc.getValueForKey(SettingsServiceBean.Key.DefaultDateFormat)));
        } catch (EJBException ise) {
            return badRequest(ise.getCause().getMessage());
        } catch (PermissionException pe) {
            return badRequest(BundleUtil.getStringFromBundle("datasets.api.setEmbargo.failure.missingPermissions", pe.getMissingPermissions().toString()));
        } catch (Exception e) {
            return badRequest(BundleUtil.getStringFromBundle("datasets.api.setEmbargo.failure.unknown", e.getMessage()));
        }
    }

    @PUT
    @Path("{id}/liftEmbargo")
    public Response liftEmbargoDate(@PathParam("id") String id) {
        try {
            Dataset dataset = findDatasetOrDie(id);
            dataset = datasetSvc.liftDatasetEmbargoDate(dataset);

            return ok(BundleUtil.getStringFromBundle("datasets.api.liftEmbargo.success", dataset.getGlobalId()));
        } catch (WrappedResponse wr) {
            return wr.getResponse();
        } catch (EJBException ise) {
            return badRequest(ise.getCause().getMessage());
        } catch (PermissionException pe) {
            return badRequest(BundleUtil.getStringFromBundle("datasets.api.liftEmbargo.failure.missingPermissions", pe.getMissingPermissions().toString()));
        } catch (Exception e) {
            return badRequest(BundleUtil.getStringFromBundle("datasets.api.liftEmbargo.failure.unknown", e.getMessage()));
        }
    }

    private Response processDatasetFieldDataDelete(String jsonBody, String id, DataverseRequest req) {
        try (StringReader rdr = new StringReader(jsonBody)) {

            Dataset ds = findDatasetOrDie(id);
            JsonObject json = Json.createReader(rdr).readObject();
            DatasetVersion dsv = ds.getEditVersion();

            List<DatasetField> fields;

            JsonArray fieldsJson = json.getJsonArray("fields");
            if (fieldsJson == null) {
                fields = new LinkedList<>(jsonParser().parseField(json, Boolean.FALSE));
            } else {
                fields = jsonParser().parseMultipleFields(json);
            }

            dsv.setVersionState(DatasetVersion.VersionState.DRAFT);

            List<DatasetField> dsfChildsToRemove = new ArrayList<>();

            Map<DatasetFieldType, List<DatasetField>> fieldsToRemoveGroupedByType = fields.stream()
                    .collect(Collectors.groupingBy(DatasetField::getDatasetFieldType));

            Map<DatasetFieldType, List<DatasetField>> oldFieldsGroupedByType = dsv.getDatasetFields().stream()
                    .collect(Collectors.groupingBy(DatasetField::getDatasetFieldType));

            for (Map.Entry<DatasetFieldType, List<DatasetField>> fieldsToRemoveEntry : fieldsToRemoveGroupedByType.entrySet()) {
                for (DatasetField removableField : fieldsToRemoveEntry.getValue()) {
                    boolean valueFound = false;
                    for (DatasetField oldField : oldFieldsGroupedByType.get(fieldsToRemoveEntry.getKey())) {
                        if (oldField.getDatasetFieldType().isControlledVocabulary()) {
                            List<ControlledVocabularyValue> controlledVocabularyItemsToRemove = new ArrayList<>();

                            if (oldField.getDatasetFieldType().isAllowMultiples()) {
                                for (ControlledVocabularyValue cvv : removableField.getControlledVocabularyValues()) {
                                    for (ControlledVocabularyValue existing : oldField.getControlledVocabularyValues()) {
                                        if (existing.getStrValue().equals(cvv.getStrValue())) {
                                            controlledVocabularyItemsToRemove.add(existing);
                                            valueFound = true;
                                        }
                                    }

                                    if (!controlledVocabularyItemsToRemove.contains(cvv)) {
                                        logger.log(Level.SEVERE,
                                                   "Delete metadata failed: " + cvv.getDatasetFieldType().getDisplayName() + ": " + cvv.getStrValue() + " not found.");
                                        return error(Response.Status.BAD_REQUEST,
                                                     "Delete metadata failed: " + cvv.getDatasetFieldType().getDisplayName() + ": " + cvv.getStrValue() + " not found.");

                                    }
                                }

                                for (ControlledVocabularyValue remove : controlledVocabularyItemsToRemove) {
                                    oldField.getControlledVocabularyValues().remove(remove);
                                }

                            } else {
                                if (oldField.getSingleControlledVocabularyValue().getStrValue().equals(
                                        removableField.getSingleControlledVocabularyValue().getStrValue())) {
                                    oldField.setSingleControlledVocabularyValue(null);
                                    valueFound = true;
                                }

                            }
                        } else {

                            if (removableField.getDatasetFieldType().isPrimitive()) {
                                if (oldField.getFieldValue().getOrElse("").equals(removableField.getFieldValue().getOrElse(
                                        ""))) {

                                    oldField.setFieldValue(null);
                                    valueFound = true;
                                }
                            } else {

                                if (DatasetFieldUtil.joinAllValues(removableField).equals(
                                        DatasetFieldUtil.joinAllValues(
                                                oldField))) {

                                    dsfChildsToRemove.addAll(oldField.getDatasetFieldsChildren());
                                    valueFound = true;
                                }
                            }
                        }
                    }

                    if (!valueFound) {
                        String displayValue = !removableField.getDisplayValue().isEmpty() ? removableField.getDisplayValue() : removableField.getCompoundDisplayValue();
                        logger.log(Level.SEVERE, "Delete metadata failed: " + removableField.getDatasetFieldType().getDisplayName() + ": " + displayValue + " not found.");
                        return error(Response.Status.BAD_REQUEST, "Delete metadata failed: " + removableField.getDatasetFieldType().getDisplayName() + ": " + displayValue + " not found.");
                    }

                }
            }


            fields.stream()
                    .map(DatasetField::getDatasetFieldsChildren)
                    .forEach(datasetFields -> datasetFields.removeAll(dsfChildsToRemove));

            boolean updateDraft = ds.getLatestVersion().isDraft();
            DatasetVersion managedVersion = updateDraft
                    ? execCommand(new UpdateDatasetVersionCommand(ds, req)).getEditVersion()
                    : execCommand(new CreateDatasetVersionCommand(req, ds, dsv));
            return ok(json(managedVersion,
                           settingsService.isTrueForKey(SettingsServiceBean.Key.ExcludeEmailFromExport)));

        } catch (
                JsonParseException ex) {
            logger.log(Level.SEVERE, "Semantic error parsing dataset update Json: " + ex.getMessage(), ex);
            return error(Response.Status.BAD_REQUEST, "Error processing metadata delete: " + ex.getMessage());

        } catch (
                WrappedResponse ex) {
            logger.log(Level.SEVERE, "Delete metadata error: " + ex.getMessage(), ex);
            return ex.getResponse();

        }

    }

    @PUT
    @Path("{id}/editMetadata")
    public Response editVersionMetadata(String jsonBody, @PathParam("id") String id, @QueryParam("replace") Boolean replace) throws WrappedResponse {

        Boolean replaceData = replace != null;

        DataverseRequest req = createDataverseRequest(findUserOrDie());

        return processDatasetUpdate(jsonBody, id, req, replaceData);
    }


    private Response processDatasetUpdate(String jsonBody, String id, DataverseRequest req, Boolean replaceData) {
        try (StringReader rdr = new StringReader(jsonBody)) {

            Dataset ds = findDatasetOrDie(id);
            JsonObject json = Json.createReader(rdr).readObject();
            DatasetVersion dsv = ds.getEditVersion();

            List<DatasetField> freshFieldsModel;
            DatasetField singleField = null;

            JsonArray fieldsJson = json.getJsonArray("fields");
            if (fieldsJson == null) {
                freshFieldsModel = new LinkedList<>(jsonParser().parseField(json, Boolean.FALSE));
            } else {
                freshFieldsModel = jsonParser().parseMultipleFields(json);
            }


            String valdationErrors = validateDatasetFieldValues(freshFieldsModel);

            if (!valdationErrors.isEmpty()) {
                logger.log(Level.SEVERE,
                           "Semantic error parsing dataset update Json: " + valdationErrors,
                           valdationErrors);
                return error(Response.Status.BAD_REQUEST, "Error parsing dataset update: " + valdationErrors);
            }

            dsv.setVersionState(DatasetVersion.VersionState.DRAFT);

            //loop through the update fields
            // and compare to the version fields
            //if exist add/replace values
            //if not add entire dsf
            Map<DatasetFieldType, List<DatasetField>> updatedFieldsGroupedByType = freshFieldsModel.stream()
                    .collect(Collectors.groupingBy(DatasetField::getDatasetFieldType));

            Map<DatasetFieldType, List<DatasetField>> oldFieldsGroupedByType = dsv.getDatasetFields().stream()
                    .collect(Collectors.groupingBy(DatasetField::getDatasetFieldType));

            ArrayList<DatasetField> fieldsToAdd = new ArrayList<>();

            for (Map.Entry<DatasetFieldType, List<DatasetField>> updatedFields : updatedFieldsGroupedByType.entrySet()) {
                for (DatasetField updateField : updatedFields.getValue()) {
                    for (DatasetField oldField : oldFieldsGroupedByType.get(updatedFields.getKey())) {
                        if (oldField.isEmpty() || oldField.getDatasetFieldType().isAllowMultiples() || replaceData) {
                            if (replaceData) {
                                if (oldField.getDatasetFieldType().isAllowMultiples()) {
                                    oldField.getControlledVocabularyValues().clear();
                                } else {
                                    oldField.setFieldValue("");
                                    oldField.setSingleControlledVocabularyValue(null);
                                }
                            }
                            if (updateField.getDatasetFieldType().isControlledVocabulary()) {
                                if (oldField.getDatasetFieldType().isAllowMultiples()) {
                                    for (ControlledVocabularyValue cvv : updateField.getControlledVocabularyValues()) {
                                        if (!oldField.getDisplayValue().contains(cvv.getStrValue())) {
                                            oldField.getControlledVocabularyValues().add(cvv);
                                        }
                                    }
                                } else {
                                    oldField.setSingleControlledVocabularyValue(updateField.getSingleControlledVocabularyValue());
                                }
                            } else {
                                if (updateField.getDatasetFieldType().isPrimitive()) {
                                    if (oldField.getDatasetFieldType().isAllowMultiples()) {
                                        if (!oldField.getFieldValue().getOrElse("").equals(updateField.getFieldValue().getOrElse(
                                                ""))) {

                                            updateField.setDatasetVersion(dsv);
                                            fieldsToAdd.add(updateField);
                                        }
                                    } else {
                                        oldField.setFieldValue(updateField.getValue());
                                    }
                                } else {
                                    if (!DatasetFieldUtil.joinAllValues(updateField).equals(
                                            DatasetFieldUtil.joinAllValues(
                                                    oldField))) {

                                        updateField.setDatasetVersion(dsv);
                                        fieldsToAdd.add(updateField);
                                    }
                                }
                            }
                        } else {
                            return error(Response.Status.BAD_REQUEST,
                                         "You may not add data to a field that already has data and does not allow multiples. Use replace=true to replace existing data (" + oldField.getDatasetFieldType().getDisplayName() + ")");
                        }
                        break;
                    }

                    updatedFieldsGroupedByType.entrySet().stream()
                            .filter(fieldTypeListEntry -> !oldFieldsGroupedByType.containsKey(fieldTypeListEntry.getKey()))
                            .map(Map.Entry::getValue)
                            .forEach(fieldNotFound -> {
                                fieldNotFound.forEach(datasetField -> {
                                    datasetField.setDatasetVersion(dsv);
                                    dsv.getDatasetFields().add(datasetField);
                                });
                            });

                    dsv.getDatasetFields().addAll(fieldsToAdd);
                }
            }
            boolean updateDraft = ds.getLatestVersion().isDraft();
            DatasetVersion managedVersion;

            if (updateDraft) {
                managedVersion = execCommand(new UpdateDatasetVersionCommand(ds, req)).getEditVersion();
            } else {
                managedVersion = execCommand(new CreateDatasetVersionCommand(req, ds, dsv));
            }

            return ok(json(managedVersion,
                           settingsService.isTrueForKey(SettingsServiceBean.Key.ExcludeEmailFromExport)));

        } catch (JsonParseException ex) {
            logger.log(Level.SEVERE, "Semantic error parsing dataset update Json: " + ex.getMessage(), ex);
            return error(Response.Status.BAD_REQUEST, "Error parsing dataset update: " + ex.getMessage());

        } catch (WrappedResponse ex) {
            logger.log(Level.SEVERE, "Update metdata error: " + ex.getMessage(), ex);
            return ex.getResponse();

        }
    }

    private String validateDatasetFieldValues(List<DatasetField> fields) {
        StringBuilder error = new StringBuilder();

        for (DatasetField dsf : fields) {
            if (dsf.getDatasetFieldType().isAllowMultiples() && dsf.getControlledVocabularyValues().isEmpty()
                    && dsf.getDatasetFieldsChildren().isEmpty() && dsf.getFieldValue().isEmpty()) {
                error.append("Empty multiple value for field: ").append(dsf.getDatasetFieldType().getDisplayName()).append(
                        " ");
            } else if (!dsf.getDatasetFieldType().isAllowMultiples() && dsf.getDatasetFieldsChildren().isEmpty()) {
                error.append("Empty value for field: ").append(dsf.getDatasetFieldType().getDisplayName()).append(" ");
            }
        }

        if (!error.toString().isEmpty()) {
            return (error.toString());
        }
        return "";
    }

    /**
     * @deprecated This was shipped as a GET but should have been a POST, see https://github.com/IQSS/dataverse/issues/2431
     */
    @GET
    @Path("{id}/actions/:publish")
    @Deprecated
    public Response publishDataseUsingGetDeprecated(@PathParam("id") String id, @QueryParam("type") String type) {
        logger.info("publishDataseUsingGetDeprecated called on id " + id + ". Encourage use of POST rather than GET, which is deprecated.");
        return publishDataset(id, type);
    }

    @POST
    @Path("{id}/actions/:publish")
    public Response publishDataset(@PathParam("id") String id, @QueryParam("type") String type) {
        try {
            if (type == null) {
                return error(Response.Status.BAD_REQUEST,
                             "Missing 'type' parameter (either 'major','minor', or 'updatecurrent').");
            }
            boolean updateCurrent = false;
            AuthenticatedUser user = findAuthenticatedUserOrDie();
            type = type.toLowerCase();
            boolean isMinor = false;
            switch (type) {
                case "minor":
                    isMinor = true;
                    break;
                case "major":
                    isMinor = false;
                    break;
                case "updatecurrent":
                    if (user.isSuperuser()) {
                        updateCurrent = true;
                    } else {
                        return error(Response.Status.FORBIDDEN, "Only superusers can update the current version");
                    }
                    break;
                default:
                    return error(Response.Status.BAD_REQUEST,
                                 "Illegal 'type' parameter value '" + type + "'. It needs to be either 'major', 'minor', or 'updatecurrent'.");
            }

            Dataset ds = findDatasetOrDie(id);
            if (updateCurrent) {
                /*
                 * Note: The code here mirrors that in the
                 * edu.harvard.iq.dataverse.DatasetPage:updateCurrentVersion method. Any changes
                 * to the core logic (i.e. beyond updating the messaging about results) should
                 * be applied to the code there as well.
                 */
                String errorMsg = null;
                String successMsg = null;
                try {
                    CuratePublishedDatasetVersionCommand cmd = new CuratePublishedDatasetVersionCommand(ds,
                                                                                                        createDataverseRequest(
                                                                                                                user));
                    ds = commandEngine.submit(cmd);
                    successMsg = BundleUtil.getStringFromBundle("datasetversion.update.success");

                    // If configured, update archive copy as well
                    String className = settingsService.getValueForKey(SettingsServiceBean.Key.ArchiverClassName);
                    DatasetVersion updateVersion = ds.getLatestVersion();
                    AbstractSubmitToArchiveCommand archiveCommand = ArchiverUtil.createSubmitToArchiveCommand(className,
                                                                                                              createDataverseRequest(
                                                                                                                      user),
                                                                                                              updateVersion);
                    if (archiveCommand != null) {
                        // Delete the record of any existing copy since it is now out of date/incorrect
                        updateVersion.setArchivalCopyLocation(null);
                        /*
                         * Then try to generate and submit an archival copy. Note that running this
                         * command within the CuratePublishedDatasetVersionCommand was causing an error:
                         * "The attribute [id] of class
                         * [edu.harvard.iq.dataverse.DatasetFieldCompoundValue] is mapped to a primary
                         * key column in the database. Updates are not allowed." To avoid that, and to
                         * simplify reporting back to the GUI whether this optional step succeeded, I've
                         * pulled this out as a separate submit().
                         */
                        try {
                            updateVersion = commandEngine.submit(archiveCommand);
                            if (updateVersion.getArchivalCopyLocation() != null) {
                                successMsg = BundleUtil.getStringFromBundle("datasetversion.update.archive.success");
                            } else {
                                successMsg = BundleUtil.getStringFromBundle("datasetversion.update.archive.failure");
                            }
                        } catch (CommandException ex) {
                            successMsg = BundleUtil.getStringFromBundle("datasetversion.update.archive.failure") + " - " + ex.toString();
                            logger.severe(ex.getMessage());
                        }
                    }
                } catch (CommandException ex) {
                    errorMsg = BundleUtil.getStringFromBundle("datasetversion.update.failure") + " - " + ex.toString();
                    logger.severe(ex.getMessage());
                }
                if (errorMsg != null) {
                    return error(Response.Status.INTERNAL_SERVER_ERROR, errorMsg);
                } else {
                    return Response.ok(Json.createObjectBuilder()
                                               .add("status", STATUS_OK)
                                               .add("status_details", successMsg)
                                               .add("data", json(ds)).build())
                            .type(MediaType.APPLICATION_JSON)
                            .build();
                }
            } else {
                PublishDatasetResult res = execCommand(new PublishDatasetCommand(ds,
                                                                                 createDataverseRequest(user),
                                                                                 isMinor));
                return res.isCompleted() ? ok(json(res.getDataset())) : accepted(json(res.getDataset()));
            }
        } catch (WrappedResponse ex) {
            return ex.getResponse();
        } catch (NoDatasetFilesException ex) {
            return error(Response.Status.INTERNAL_SERVER_ERROR,
                         "Unable to publish dataset, since there are no files in it.");
        }
    }

    @POST
    @Path("{id}/move/{targetDataverseAlias}")
    public Response moveDataset(@PathParam("id") String id, @PathParam("targetDataverseAlias") String targetDataverseAlias, @QueryParam("forceMove") Boolean force) {
        try {
            User u = findUserOrDie();
            Dataset ds = findDatasetOrDie(id);
            Dataverse target = dataverseDao.findByAlias(targetDataverseAlias);
            if (target == null) {
                return error(Response.Status.BAD_REQUEST, "Target Dataverse not found.");
            }
            //Command requires Super user - it will be tested by the command
            execCommand(new MoveDatasetCommand(
                    createDataverseRequest(u), ds, target, force
            ));
            return ok("Dataset moved successfully");
        } catch (WrappedResponse ex) {
            return ex.getResponse();
        }
    }

    @PUT
    @Path("{linkedDatasetId}/link/{linkingDataverseAlias}")
    public Response linkDataset(@PathParam("linkedDatasetId") String linkedDatasetId, @PathParam("linkingDataverseAlias") String linkingDataverseAlias) {
        try {
            User u = findUserOrDie();
            Dataset linked = findDatasetOrDie(linkedDatasetId);
            Dataverse linking = findDataverseOrDie(linkingDataverseAlias);
            if (linked == null) {
                return error(Response.Status.BAD_REQUEST, "Linked Dataset not found.");
            }
            if (linking == null) {
                return error(Response.Status.BAD_REQUEST, "Linking Dataverse not found.");
            }
            execCommand(new LinkDatasetCommand(
                    createDataverseRequest(u), linking, linked
            ));
            return ok("Dataset " + linked.getId() + " linked successfully to " + linking.getAlias());
        } catch (WrappedResponse ex) {
            return ex.getResponse();
        }
    }

    @GET
    @Path("{id}/links")
    public Response getLinks(@PathParam("id") String idSupplied) {
        try {
            User u = findUserOrDie();
            if (!u.isSuperuser()) {
                return error(Response.Status.FORBIDDEN, "Not a superuser");
            }
            Dataset dataset = findDatasetOrDie(idSupplied);

            long datasetId = dataset.getId();
            List<Dataverse> dvsThatLinkToThisDatasetId = dataverseSvc.findDataversesThatLinkToThisDatasetId(datasetId);
            JsonArrayBuilder dataversesThatLinkToThisDatasetIdBuilder = Json.createArrayBuilder();
            for (Dataverse dataverse : dvsThatLinkToThisDatasetId) {
                dataversesThatLinkToThisDatasetIdBuilder.add(dataverse.getAlias() + " (id " + dataverse.getId() + ")");
            }
            JsonObjectBuilder response = Json.createObjectBuilder();
            response.add("dataverses that link to dataset id " + datasetId, dataversesThatLinkToThisDatasetIdBuilder);
            return ok(response);
        } catch (WrappedResponse wr) {
            return wr.getResponse();
        }
    }

    /**
     * @todo Make this real. Currently only used for API testing. Copied from
     * the equivalent API endpoint for dataverses and simplified with values
     * hard coded.
     */
    @POST
    @Path("{identifier}/assignments")
    public Response createAssignment(String userOrGroup, @PathParam("identifier") String id, @QueryParam("key") String apiKey) {
        boolean apiTestingOnly = true;
        if (apiTestingOnly) {
            return error(Response.Status.FORBIDDEN, "This is only for API tests.");
        }
        try {
            Dataset dataset = findDatasetOrDie(id);
            RoleAssignee assignee = findAssignee(userOrGroup);
            if (assignee == null) {
                return error(Response.Status.BAD_REQUEST, "Assignee not found");
            }
            DataverseRole theRole = rolesSvc.findBuiltinRoleByAlias("admin");
            String privateUrlToken = null;
            return ok(
                    json(execCommand(new AssignRoleCommand(assignee,
                                                           theRole,
                                                           dataset,
                                                           createDataverseRequest(findUserOrDie()),
                                                           privateUrlToken))));
        } catch (WrappedResponse ex) {
            logger.log(Level.WARNING, "Can''t create assignment: {0}", ex.getMessage());
            return ex.getResponse();
        }
    }

    @GET
    @Path("{identifier}/assignments")
    public Response getAssignments(@PathParam("identifier") String id) {
        return response(req ->
                                ok(execCommand(
                                        new ListRoleAssignments(req, findDatasetOrDie(id)))
                                           .stream().map(ra -> json(ra)).collect(toJsonArray())));
    }

    @GET
    @Path("{id}/privateUrl")
    public Response getPrivateUrlData(@PathParam("id") String idSupplied) {
        return response(req -> {
            PrivateUrl privateUrl = execCommand(new GetPrivateUrlCommand(req, findDatasetOrDie(idSupplied)));
            return (privateUrl != null) ? ok(json(privateUrl))
                    : error(Response.Status.NOT_FOUND, "Private URL not found.");
        });
    }

    @POST
    @Path("{id}/privateUrl")
    public Response createPrivateUrl(@PathParam("id") String idSupplied) {
        return response(req ->
                                ok(json(execCommand(
                                        new CreatePrivateUrlCommand(req, findDatasetOrDie(idSupplied))))));
    }

    @DELETE
    @Path("{id}/privateUrl")
    public Response deletePrivateUrl(@PathParam("id") String idSupplied) {
        return response(req -> {
            Dataset dataset = findDatasetOrDie(idSupplied);
            PrivateUrl privateUrl = execCommand(new GetPrivateUrlCommand(req, dataset));
            if (privateUrl != null) {
                execCommand(new DeletePrivateUrlCommand(req, dataset));
                return ok("Private URL deleted.");
            } else {
                return notFound("No Private URL to delete.");
            }
        });
    }

    @GET
    @Path("{id}/thumbnail/candidates")
    public Response getDatasetThumbnailCandidates(@PathParam("id") String idSupplied) {
        try {
            Dataset dataset = findDatasetOrDie(idSupplied);
            boolean canUpdateThumbnail = false;
            try {
                canUpdateThumbnail = permissionSvc.requestOn(createDataverseRequest(findUserOrDie()), dataset).canIssue(
                        UpdateDatasetThumbnailCommand.class);
            } catch (WrappedResponse ex) {
                logger.info(
                        "Exception thrown while trying to figure out permissions while getting thumbnail for dataset id " + dataset.getId() + ": " + ex.getLocalizedMessage());
            }
            if (!canUpdateThumbnail) {
                return error(Response.Status.FORBIDDEN, "You are not permitted to list dataset thumbnail candidates.");
            }
            JsonArrayBuilder data = Json.createArrayBuilder();
            boolean considerDatasetLogoAsCandidate = true;
            for (DatasetThumbnail datasetThumbnail : DatasetUtil.getThumbnailCandidates(dataset,
                                                                                        considerDatasetLogoAsCandidate,
                                                                                        new DataAccess())) {
                JsonObjectBuilder candidate = Json.createObjectBuilder();
                String base64image = datasetThumbnail.getBase64image();
                if (base64image != null) {
                    logger.fine("found a candidate!");
                    candidate.add("base64image", base64image);
                }
                DataFile dataFile = datasetThumbnail.getDataFile();
                if (dataFile != null) {
                    candidate.add("dataFileId", dataFile.getId());
                }
                data.add(candidate);
            }
            return ok(data);
        } catch (WrappedResponse ex) {
            return error(Response.Status.NOT_FOUND, "Could not find dataset based on id supplied: " + idSupplied + ".");
        }
    }

    @GET
    @Produces({"image/png"})
    @Path("{id}/thumbnail")
    public Response getDatasetThumbnail(@PathParam("id") String idSupplied) {
        try {
            Dataset dataset = findDatasetOrDie(idSupplied);
            InputStream is = DatasetUtil.getThumbnailAsInputStream(dataset);
            if (is == null) {
                return notFound("Thumbnail not available");
            }
            return Response.ok(is).build();
        } catch (WrappedResponse wr) {
            return notFound("Thumbnail not available");
        }
    }

    // TODO: Rather than only supporting looking up files by their database IDs (dataFileIdSupplied), consider supporting persistent identifiers.
    @POST
    @Path("{id}/thumbnail/{dataFileId}")
    public Response setDataFileAsThumbnail(@PathParam("id") String idSupplied, @PathParam("dataFileId") long dataFileIdSupplied) {
        try {
            DatasetThumbnail datasetThumbnail = execCommand(new UpdateDatasetThumbnailCommand(createDataverseRequest(
                    findUserOrDie()),
                                                                                              findDatasetOrDie(
                                                                                                      idSupplied),
                                                                                              UpdateDatasetThumbnailCommand.UserIntent.setDatasetFileAsThumbnail,
                                                                                              dataFileIdSupplied,
                                                                                              null));
            return ok("Thumbnail set to " + datasetThumbnail.getBase64image());
        } catch (WrappedResponse wr) {
            return wr.getResponse();
        }
    }

    @POST
    @Path("{id}/thumbnail")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response uploadDatasetLogo(@PathParam("id") String idSupplied, @FormDataParam("file") InputStream inputStream
    ) {
        try {
            DatasetThumbnail datasetThumbnail = execCommand(new UpdateDatasetThumbnailCommand(createDataverseRequest(
                    findUserOrDie()),
                                                                                              findDatasetOrDie(
                                                                                                      idSupplied),
                                                                                              UpdateDatasetThumbnailCommand.UserIntent.setNonDatasetFileAsThumbnail,
                                                                                              null,
                                                                                              inputStream));
            return ok("Thumbnail is now " + datasetThumbnail.getBase64image());
        } catch (WrappedResponse wr) {
            return wr.getResponse();
        }
    }

    @DELETE
    @Path("{id}/thumbnail")
    public Response removeDatasetLogo(@PathParam("id") String idSupplied) {
        try {
            DatasetThumbnail datasetThumbnail = execCommand(new UpdateDatasetThumbnailCommand(createDataverseRequest(
                    findUserOrDie()),
                                                                                              findDatasetOrDie(
                                                                                                      idSupplied),
                                                                                              UpdateDatasetThumbnailCommand.UserIntent.removeThumbnail,
                                                                                              null,
                                                                                              null));
            return ok("Dataset thumbnail removed.");
        } catch (WrappedResponse wr) {
            return wr.getResponse();
        }
    }

    @GET
    @Path("{identifier}/dataCaptureModule/rsync")
    public Response getRsync(@PathParam("identifier") String id) {
        //TODO - does it make sense to switch this to dataset identifier for consistency with the rest of the DCM APIs?
        if (!DataCaptureModuleUtil.rsyncSupportEnabled(settingsSvc.getValueForKey(SettingsServiceBean.Key.UploadMethods))) {
            return error(Response.Status.METHOD_NOT_ALLOWED,
                         SettingsServiceBean.Key.UploadMethods + " does not contain " + SystemConfig.FileUploadMethods.RSYNC + ".");
        }
        Dataset dataset = null;
        try {
            dataset = findDatasetOrDie(id);
            AuthenticatedUser user = findAuthenticatedUserOrDie();
            ScriptRequestResponse scriptRequestResponse = execCommand(new RequestRsyncScriptCommand(
                    createDataverseRequest(user),
                    dataset));

            DatasetLock lock = datasetDao.addDatasetLock(dataset.getId(),
                                                         DatasetLock.Reason.DcmUpload,
                                                         user.getId(),
                                                         "script downloaded");
            if (lock == null) {
                logger.log(Level.WARNING, "Failed to lock the dataset (dataset id={0})", dataset.getId());
                return error(Response.Status.FORBIDDEN,
                             "Failed to lock the dataset (dataset id=" + dataset.getId() + ")");
            }
            return ok(scriptRequestResponse.getScript(), MediaType.valueOf(MediaType.TEXT_PLAIN));
        } catch (WrappedResponse wr) {
            return wr.getResponse();
        } catch (EJBException ex) {
            return error(Response.Status.INTERNAL_SERVER_ERROR,
                         "Something went wrong attempting to download rsync script: " + EjbUtil.ejbExceptionToString(ex));
        }
    }

    /**
     * This api endpoint triggers the creation of a "package" file in a dataset
     * after that package has been moved onto the same filesystem via the Data Capture Module.
     * The package is really just a way that Dataverse interprets a folder created by DCM, seeing it as just one file.
     * The "package" can be downloaded over RSAL.
     * <p>
     * This endpoint currently supports both posix file storage and AWS s3 storage in Dataverse, and depending on which one is active acts accordingly.
     * <p>
     * The initial design of the DCM/Dataverse interaction was not to use packages, but to allow import of all individual files natively into Dataverse.
     * But due to the possibly immense number of files (millions) the package approach was taken.
     * This is relevant because the posix ("file") code contains many remnants of that development work.
     * The s3 code was written later and is set to only support import as packages. It takes a lot from FileRecordWriter.
     * -MAD 4.9.1
     */
    @POST
    @Path("{identifier}/dataCaptureModule/checksumValidation")
    public Response receiveChecksumValidationResults(@PathParam("identifier") String id, JsonObject jsonFromDcm) {
        logger.log(Level.FINE, "jsonFromDcm: {0}", jsonFromDcm);
        AuthenticatedUser authenticatedUser = null;
        try {
            authenticatedUser = findAuthenticatedUserOrDie();
        } catch (WrappedResponse ex) {
            return error(Response.Status.BAD_REQUEST, "Authentication is required.");
        }
        if (!authenticatedUser.isSuperuser()) {
            return error(Response.Status.FORBIDDEN, "Superusers only.");
        }
        String statusMessageFromDcm = jsonFromDcm.getString("status");
        try {
            Dataset dataset = findDatasetOrDie(id);
            if ("validation passed".equals(statusMessageFromDcm)) {
                logger.log(Level.INFO, "Checksum Validation passed for DCM.");

                String storageDriver = (System.getProperty("dataverse.files.storage-driver-id") != null) ?
                        System.getProperty("dataverse.files.storage-driver-id") :
                        "file";
                String uploadFolder = jsonFromDcm.getString("uploadFolder");
                int totalSize = jsonFromDcm.getInt("totalSize");

                if (storageDriver.equals("file")) {
                    logger.log(Level.INFO, "File storage driver used for (dataset id={0})", dataset.getId());

                    ImportMode importMode = ImportMode.MERGE;
                    try {
                        JsonObject jsonFromImportJobKickoff = execCommand(new ImportFromFileSystemCommand(
                                createDataverseRequest(findUserOrDie()),
                                dataset,
                                uploadFolder,
                                new Long(totalSize),
                                importMode));
                        long jobId = jsonFromImportJobKickoff.getInt("executionId");
                        String message = jsonFromImportJobKickoff.getString("message");
                        JsonObjectBuilder job = Json.createObjectBuilder();
                        job.add("jobId", jobId);
                        job.add("message", message);
                        return ok(job);
                    } catch (WrappedResponse wr) {
                        String message = wr.getMessage();
                        return error(Response.Status.INTERNAL_SERVER_ERROR,
                                     "Uploaded files have passed checksum validation but something went wrong while attempting to put the files into Dataverse. Message was '" + message + "'.");
                    }
                } else if (storageDriver.equals("s3")) {

                    logger.log(Level.INFO, "S3 storage driver used for DCM (dataset id={0})", dataset.getId());
                    try {

                        //Where the lifting is actually done, moving the s3 files over and having dataverse know of the existance of the package
                        s3PackageImporter.copyFromS3(dataset, uploadFolder);
                        DataFile packageFile = s3PackageImporter.createPackageDataFile(dataset,
                                                                                       uploadFolder,
                                                                                       new Long(totalSize));

                        if (packageFile == null) {
                            logger.log(Level.SEVERE, "S3 File package import failed.");
                            return error(Response.Status.INTERNAL_SERVER_ERROR, "S3 File package import failed.");
                        }
                        DatasetLock dcmLock = dataset.getLockFor(DatasetLock.Reason.DcmUpload);
                        if (dcmLock == null) {
                            logger.log(Level.WARNING, "Dataset not locked for DCM upload");
                        } else {
                            datasetDao.removeDatasetLocks(dataset, DatasetLock.Reason.DcmUpload);
                            dataset.removeLock(dcmLock);
                        }

                        // update version using the command engine to enforce user permissions and constraints
                        if (dataset.getVersions().size() == 1 && dataset.getLatestVersion().getVersionState() == DatasetVersion.VersionState.DRAFT) {
                            try {
                                Command<Dataset> cmd;
                                cmd = new UpdateDatasetVersionCommand(dataset,
                                                                      new DataverseRequest(authenticatedUser,
                                                                                           (HttpServletRequest) null));
                                commandEngine.submit(cmd);
                            } catch (CommandException ex) {
                                return error(Response.Status.INTERNAL_SERVER_ERROR,
                                             "CommandException updating DatasetVersion from batch job: " + ex.getMessage());
                            }
                        } else {
                            String constraintError = "ConstraintException updating DatasetVersion form batch job: dataset must be a "
                                    + "single version in draft mode.";
                            logger.log(Level.SEVERE, constraintError);
                        }

                        JsonObjectBuilder job = Json.createObjectBuilder();
                        return ok(job);

                    } catch (IOException e) {
                        String message = e.getMessage();
                        return error(Response.Status.INTERNAL_SERVER_ERROR,
                                     "Uploaded files have passed checksum validation but something went wrong while attempting to move the files into Dataverse. Message was '" + message + "'.");
                    }
                } else {
                    return error(Response.Status.INTERNAL_SERVER_ERROR,
                                 "Invalid storage driver in Dataverse, not compatible with dcm");
                }
            } else if ("validation failed".equals(statusMessageFromDcm)) {
                Map<String, AuthenticatedUser> distinctAuthors = permissionService.getDistinctUsersWithPermissionOn(
                        Permission.EditDataset,
                        dataset);
                distinctAuthors.values().forEach((value) -> {
                    userNotificationService.sendNotificationWithEmail(value,
                                                                      new Timestamp(new Date().getTime()),
                                                                      NotificationType.CHECKSUMFAIL,
                                                                      dataset.getId(),
                                                                      NotificationObjectType.DATASET);
                });
                List<AuthenticatedUser> superUsers = authenticationServiceBean.findSuperUsers();
                if (superUsers != null && !superUsers.isEmpty()) {
                    superUsers.forEach((au) -> {
                        userNotificationService.sendNotificationWithEmail(au,
                                                                          new Timestamp(new Date().getTime()),
                                                                          NotificationType.CHECKSUMFAIL,
                                                                          dataset.getId(),
                                                                          NotificationObjectType.DATASET);
                    });
                }
                return ok("User notified about checksum validation failure.");
            } else {
                return error(Response.Status.BAD_REQUEST,
                             "Unexpected status cannot be processed: " + statusMessageFromDcm);
            }
        } catch (WrappedResponse ex) {
            return ex.getResponse();
        }
    }


    @POST
    @Path("{id}/submitForReview")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response submitForReview(@PathParam("id") String idSupplied, SubmitForReviewDataDTO submitForReviewData) {
        try {
            Dataset updatedDataset = execCommand(new SubmitDatasetForReviewCommand(createDataverseRequest(findUserOrDie()),
                    findDatasetOrDie(idSupplied), submitForReviewData.getComment()));
            JsonObjectBuilder result = Json.createObjectBuilder();

            boolean inReview = updatedDataset.isLockedFor(DatasetLock.Reason.InReview);

            result.add("inReview", inReview);
            result.add("message", "Dataset id " + updatedDataset.getId() + " has been submitted for review.");
            return ok(result);
        } catch (WrappedResponse wr) {
            return wr.getResponse();
        } catch (NoDatasetFilesException ex) {
            return error(Response.Status.INTERNAL_SERVER_ERROR,
                         "Unable to submit dataset for review, since there are no files in it.");
        }

    }

    @POST
    @Path("{id}/returnToAuthor")
    public Response returnToAuthor(@PathParam("id") String idSupplied, String jsonBody) {

        if (jsonBody == null || jsonBody.isEmpty()) {
            return error(Response.Status.BAD_REQUEST,
                         "You must supply JSON to this API endpoint and it must contain a reason for returning the dataset (field: reasonForReturn).");
        }
        StringReader rdr = new StringReader(jsonBody);
        JsonObject json = Json.createReader(rdr).readObject();
        try {
            Dataset dataset = findDatasetOrDie(idSupplied);
            String reasonForReturn = null;
            reasonForReturn = json.getString("reasonForReturn");
            // TODO: Once we add a box for the curator to type into, pass the reason for return to the ReturnDatasetToAuthorCommand and delete this check and call to setReturnReason on the API side.
            if (reasonForReturn == null || reasonForReturn.isEmpty()) {
                return error(Response.Status.BAD_REQUEST,
                             "You must enter a reason for returning a dataset to the author(s).");
            }
            AuthenticatedUser authenticatedUser = findAuthenticatedUserOrDie();
            Dataset updatedDataset = execCommand(new ReturnDatasetToAuthorCommand(createDataverseRequest(
                    authenticatedUser), dataset, reasonForReturn));

            JsonObjectBuilder result = Json.createObjectBuilder();
            result.add("inReview", false);
            result.add("message", "Dataset id " + updatedDataset.getId() + " has been sent back to the author(s).");
            return ok(result);
        } catch (WrappedResponse wr) {
            return wr.getResponse();
        }
    }

    /**
     * Add a File to an existing Dataset
     *
     * @param idSupplied
     * @param jsonData
     * @param fileInputStream
     * @param contentDispositionHeader
     * @param formDataBodyPart
     * @return
     */
    @POST
    @Path("{id}/add")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response addFileToDataset(@PathParam("id") String idSupplied,
                                     @FormDataParam("jsonData") String jsonData,
                                     @FormDataParam("file") InputStream fileInputStream,
                                     @FormDataParam("file") FormDataContentDisposition contentDispositionHeader,
                                     @FormDataParam("file") final FormDataBodyPart formDataBodyPart
    ) {

        if (!systemConfig.isHTTPUpload()) {
            return error(Response.Status.SERVICE_UNAVAILABLE, BundleUtil.getStringFromBundle("file.api.httpDisabled"));
        }

        // -------------------------------------
        // (1) Get the user from the API key
        // -------------------------------------
        User authUser;
        try {
            authUser = findUserOrDie();
        } catch (WrappedResponse ex) {
            return error(Response.Status.FORBIDDEN,
                         BundleUtil.getStringFromBundle("file.addreplace.error.auth")
            );
        }


        // -------------------------------------
        // (2) Get the Dataset Id
        //
        // -------------------------------------
        Dataset dataset;

        try {
            dataset = findDatasetOrDie(idSupplied);
        } catch (WrappedResponse wr) {
            return wr.getResponse();
        }

        //------------------------------------
        // (2a) Make sure dataset does not have package file
        //
        // --------------------------------------

        for (DatasetVersion dv : dataset.getVersions()) {
            if (dv.isHasPackageFile()) {
                return error(Response.Status.FORBIDDEN,
                             ResourceBundle.getBundle("Bundle").getString("file.api.alreadyHasPackageFile")
                );
            }
        }


        // -------------------------------------
        // (3) Get the file name and content type
        // -------------------------------------
        String newFilename = contentDispositionHeader.getFileName();
        String newFileContentType = formDataBodyPart.getMediaType().toString();


        // (2a) Load up optional params via JSON
        //---------------------------------------
        OptionalFileParams optionalFileParams = null;
        msgt("(api) jsonData: " + jsonData);

        try {
            optionalFileParams = new OptionalFileParams(jsonData);
        } catch (DataFileTagException ex) {
            return error(Response.Status.BAD_REQUEST, ex.getMessage());
        }


        //-------------------
        // (3) Create the AddReplaceFileHelper object
        //-------------------
        msg("ADD!");

        DataverseRequest dvRequest2 = createDataverseRequest(authUser);
        AddReplaceFileHelper addFileHelper = new AddReplaceFileHelper(dvRequest2,
                                                                      ingestService,
                                                                      fileService,
                                                                      permissionSvc,
                                                                      commandEngine);


        //-------------------
        // (4) Run "runAddFileByDatasetId"
        //-------------------
        addFileHelper.runAddFileByDataset(dataset,
                                          newFilename,
                                          newFileContentType,
                                          fileInputStream,
                                          optionalFileParams);


        if (addFileHelper.hasError()) {
            return error(addFileHelper.getHttpErrorCode(), addFileHelper.getErrorMessagesAsString("\n"));
        } else {
            String successMsg = BundleUtil.getStringFromBundle("file.addreplace.success.add");
            try {
                //msgt("as String: " + addFileHelper.getSuccessResult());
                /**
                 * @todo We need a consistent, sane way to communicate a human
                 * readable message to an API client suitable for human
                 * consumption. Imagine if the UI were built in Angular or React
                 * and we want to return a message from the API as-is to the
                 * user. Human readable.
                 */
                logger.fine("successMsg: " + successMsg);
                return ok(addFileHelper.getSuccessResultAsJsonObjectBuilder());
                //"Look at that!  You added a file! (hey hey, it may have worked)");
            } catch (NoFilesException ex) {
                Logger.getLogger(Files.class.getName()).log(Level.SEVERE, null, ex);
                return error(Response.Status.BAD_REQUEST, "NoFileException!  Serious Error! See administrator!");

            }
        }

    } // end: addFileToDataset


    private void msg(String m) {
        //System.out.println(m);
        logger.fine(m);
    }

    private void dashes() {
        msg("----------------");
    }

    private void msgt(String m) {
        dashes();
        msg(m);
        dashes();
    }


    private <T> T handleVersion(String versionId, DsVersionHandler<T> hdl)
            throws WrappedResponse {
        switch (versionId) {
            case ":latest":
                return hdl.handleLatest();
            case ":draft":
                return hdl.handleDraft();
            case ":latest-published":
                return hdl.handleLatestPublished();
            default:
                try {
                    String[] versions = versionId.split("\\.");
                    switch (versions.length) {
                        case 1:
                            return hdl.handleSpecific(Long.parseLong(versions[0]), (long) 0.0);
                        case 2:
                            return hdl.handleSpecific(Long.parseLong(versions[0]), Long.parseLong(versions[1]));
                        default:
                            throw new WrappedResponse(error(Response.Status.BAD_REQUEST,
                                                            "Illegal version identifier '" + versionId + "'"));
                    }
                } catch (NumberFormatException nfe) {
                    throw new WrappedResponse(error(Response.Status.BAD_REQUEST,
                                                    "Illegal version identifier '" + versionId + "'"));
                }
        }
    }

    private DatasetVersion getDatasetVersionOrDie(final DataverseRequest req, String versionNumber, final Dataset ds) throws WrappedResponse {
        DatasetVersion dsv = execCommand(handleVersion(versionNumber, new DsVersionHandler<Command<DatasetVersion>>() {

            @Override
            public Command<DatasetVersion> handleLatest() {
                return new GetLatestAccessibleDatasetVersionCommand(req, ds);
            }

            @Override
            public Command<DatasetVersion> handleDraft() {
                return new GetDraftDatasetVersionCommand(req, ds);
            }

            @Override
            public Command<DatasetVersion> handleSpecific(long major, long minor) {
                return new GetSpecificPublishedDatasetVersionCommand(req, ds, major, minor);
            }

            @Override
            public Command<DatasetVersion> handleLatestPublished() {
                return new GetLatestPublishedDatasetVersionCommand(req, ds);
            }
        }));
        if (dsv == null || dsv.getId() == null) {
            throw new WrappedResponse(notFound("Dataset version " + versionNumber + " of dataset " + ds.getId() + " not found"));
        }
        return dsv;
    }

    @GET
    @Path("{identifier}/locks")
    public Response getLocks(@PathParam("identifier") String id, @QueryParam("type") DatasetLock.Reason lockType) {

        Dataset dataset = null;
        try {
            dataset = findDatasetOrDie(id);
            Set<DatasetLock> locks;
            if (lockType == null) {
                locks = dataset.getLocks();
            } else {
                // request for a specific type lock:
                DatasetLock lock = dataset.getLockFor(lockType);

                locks = new HashSet<>();
                if (lock != null) {
                    locks.add(lock);
                }
            }

            return ok(locks.stream().map(lock -> json(lock)).collect(toJsonArray()));

        } catch (WrappedResponse wr) {
            return wr.getResponse();
        }
    }

    @DELETE
    @Path("{identifier}/locks")
    public Response deleteLocks(@PathParam("identifier") String id, @QueryParam("type") DatasetLock.Reason lockType) {

        return response(req -> {
            try {
                AuthenticatedUser user = findAuthenticatedUserOrDie();
                if (!user.isSuperuser()) {
                    return error(Response.Status.FORBIDDEN, "This API end point can be used by superusers only.");
                }
                Dataset dataset = findDatasetOrDie(id);

                if (lockType == null) {
                    Set<DatasetLock.Reason> locks = new HashSet<>();
                    for (DatasetLock lock : dataset.getLocks()) {
                        locks.add(lock.getReason());
                    }
                    if (!locks.isEmpty()) {
                        for (DatasetLock.Reason locktype : locks) {
                            execCommand(new RemoveLockCommand(req, dataset, locktype));
                            // refresh the dataset:
                            dataset = findDatasetOrDie(id);
                        }
                        // kick of dataset reindexing, in case the locks removed
                        // affected the search card:
                        indexService.indexDataset(dataset, true);
                        return ok("locks removed");
                    }
                    return ok("dataset not locked");
                }
                // request for a specific type lock:
                DatasetLock lock = dataset.getLockFor(lockType);
                if (lock != null) {
                    execCommand(new RemoveLockCommand(req, dataset, lock.getReason()));
                    // refresh the dataset:
                    dataset = findDatasetOrDie(id);
                    // ... and kick of dataset reindexing, in case the lock removed
                    // affected the search card:
                    indexService.indexDataset(dataset, true);
                    return ok("lock type " + lock.getReason() + " removed");
                }
                return ok("no lock type " + lockType + " on the dataset");
            } catch (WrappedResponse wr) {
                return wr.getResponse();
            }

        });

    }

    @POST
    @Path("{identifier}/lock/{type}")
    public Response lockDataset(@PathParam("identifier") String id, @PathParam("type") DatasetLock.Reason lockType) {
        return response(req -> {
            try {
                AuthenticatedUser user = findAuthenticatedUserOrDie();
                if (!user.isSuperuser()) {
                    return error(Response.Status.FORBIDDEN, "This API end point can be used by superusers only.");
                }
                Dataset dataset = findDatasetOrDie(id);
                DatasetLock lock = dataset.getLockFor(lockType);
                if (lock != null) {
                    return error(Response.Status.FORBIDDEN, "dataset already locked with lock type " + lockType);
                }
                lock = new DatasetLock(lockType, user);
                execCommand(new AddLockCommand(req, dataset, lock));
                // refresh the dataset:
                dataset = findDatasetOrDie(id);
                // ... and kick of dataset reindexing:
                indexService.indexDataset(dataset, true);
                return ok("dataset locked with lock type " + lockType);
            } catch (WrappedResponse wr) {
                return wr.getResponse();
            }

        });
    }

    // -------------------- PRIVATE ---------------------
    private void validateEmbargoDate(Date embargoDate) throws WrappedResponse {
        if (embargoDate.toInstant().isBefore(getTomorrowsDateInstant())) {
            throw new WrappedResponse(badRequest(BundleUtil.getStringFromBundle("datasets.api.setEmbargo.failure.badDate.notFuture")));
        }
        if (isMaximumEmbargoLengthSet() && embargoDate.toInstant().isAfter(getMaximumEmbargoDate())) {
            throw new WrappedResponse(badRequest(BundleUtil.getStringFromBundle("datasets.api.setEmbargo.failure.badDate.tooLong",
                    settingsSvc.getValueForKey(SettingsServiceBean.Key.MaximumEmbargoLength))));
        }
    }

    private Instant getTomorrowsDateInstant() {
        return Date.from(Instant.now().truncatedTo(ChronoUnit.DAYS).plus(1, ChronoUnit.DAYS)).toInstant();
    }

    private boolean isMaximumEmbargoLengthSet() {
        return settingsService.getValueForKeyAsInt(SettingsServiceBean.Key.MaximumEmbargoLength) > 0;
    }

    private Instant getMaximumEmbargoDate() {
        return Date.from(Instant
                .now().atOffset(ZoneOffset.UTC)
                .plus(settingsService.getValueForKeyAsLong(SettingsServiceBean.Key.MaximumEmbargoLength), ChronoUnit.MONTHS)
                .toInstant()).toInstant();
    }

}

