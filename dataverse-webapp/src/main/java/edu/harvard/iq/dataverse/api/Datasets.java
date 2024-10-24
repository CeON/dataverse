package edu.harvard.iq.dataverse.api;

import com.amazonaws.services.pi.model.InvalidArgumentException;
import edu.harvard.iq.dataverse.DataFileServiceBean;
import edu.harvard.iq.dataverse.DatasetDao;
import edu.harvard.iq.dataverse.DataverseDao;
import edu.harvard.iq.dataverse.DataverseRoleServiceBean;
import edu.harvard.iq.dataverse.EjbDataverseEngine;
import edu.harvard.iq.dataverse.PermissionServiceBean;
import edu.harvard.iq.dataverse.RoleAssigneeServiceBean;
import edu.harvard.iq.dataverse.S3PackageImporter;
import edu.harvard.iq.dataverse.api.annotations.ApiWriteOperation;
import edu.harvard.iq.dataverse.api.dto.DatasetDTO;
import edu.harvard.iq.dataverse.api.dto.DatasetLockDTO;
import edu.harvard.iq.dataverse.api.dto.DatasetVersionDTO;
import edu.harvard.iq.dataverse.api.dto.FileLabelsChangeOptionsDTO;
import edu.harvard.iq.dataverse.api.dto.FileMetadataDTO;
import edu.harvard.iq.dataverse.api.dto.MetadataBlockWithFieldsDTO;
import edu.harvard.iq.dataverse.api.dto.PrivateUrlDTO;
import edu.harvard.iq.dataverse.api.dto.RoleAssignmentDTO;
import edu.harvard.iq.dataverse.api.dto.SubmitForReviewDataDTO;
import edu.harvard.iq.dataverse.api.dto.UningestRequestDTO;
import edu.harvard.iq.dataverse.api.dto.UningestableItemDTO;
import edu.harvard.iq.dataverse.authorization.AuthenticationServiceBean;
import edu.harvard.iq.dataverse.batch.jobs.importer.ImportMode;
import edu.harvard.iq.dataverse.common.BundleUtil;
import edu.harvard.iq.dataverse.datacapturemodule.DataCaptureModuleUtil;
import edu.harvard.iq.dataverse.datacapturemodule.ScriptRequestResponse;
import edu.harvard.iq.dataverse.datafile.DataFileCreator;
import edu.harvard.iq.dataverse.datafile.file.FileDownloadAPIHandler;
import edu.harvard.iq.dataverse.dataset.DatasetFileDownloadUrlCsvWriter;
import edu.harvard.iq.dataverse.dataset.DatasetService;
import edu.harvard.iq.dataverse.dataset.DatasetThumbnail;
import edu.harvard.iq.dataverse.dataset.DatasetThumbnailService;
import edu.harvard.iq.dataverse.dataset.FileLabelInfo;
import edu.harvard.iq.dataverse.dataset.FileLabelsService;
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
import edu.harvard.iq.dataverse.engine.command.impl.GetDraftVersionIfExists;
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
import edu.harvard.iq.dataverse.export.ExportService;
import edu.harvard.iq.dataverse.export.ExporterType;
import edu.harvard.iq.dataverse.ingest.IngestServiceBean;
import edu.harvard.iq.dataverse.ingest.UningestInfoService;
import edu.harvard.iq.dataverse.ingest.UningestService;
import edu.harvard.iq.dataverse.notification.NotificationObjectType;
import edu.harvard.iq.dataverse.notification.NotificationParameter;
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
import edu.harvard.iq.dataverse.persistence.user.DataverseRole.BuiltInRole;
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
import io.vavr.control.Try;
import org.apache.commons.cli.MissingArgumentException;
import org.apache.commons.io.IOUtils;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;

import javax.ejb.EJBException;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.security.InvalidParameterException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Path("datasets")
public class Datasets extends AbstractApiBean {

    private static final Logger logger = Logger.getLogger(Datasets.class.getCanonicalName());

    private DatasetDao datasetDao;
    private DataverseDao dataverseDao;
    private UserNotificationService userNotificationService;
    private PermissionServiceBean permissionService;
    private AuthenticationServiceBean authenticationServiceBean;
    private DataFileServiceBean fileService;
    private IngestServiceBean ingestService;
    private EjbDataverseEngine commandEngine;
    private IndexServiceBean indexService;
    private S3PackageImporter s3PackageImporter;
    private SettingsServiceBean settingsService;
    private ExportService exportService;
    private DatasetService datasetSvc;
    private DatasetsValidators datasetsValidators;
    private OptionalFileParams optionalFileParamsSvc;
    private DataFileCreator dataFileCreator;
    private DatasetThumbnailService datasetThumbnailService;
    private FileDownloadAPIHandler fileDownloadAPIHandler;
    private DataverseRoleServiceBean rolesSvc;
    private RoleAssigneeServiceBean roleAssigneeSvc;
    private PermissionServiceBean permissionSvc;
    private FileLabelsService fileLabelsService;
    private DatasetFileDownloadUrlCsvWriter fileDownloadUrlCsvWriter;
    private UningestInfoService uningestInfoService;
    private UningestService uningestService;

    // -------------------- CONSTRUCTORS --------------------

    public Datasets() { }

    @Inject
    public Datasets(DatasetDao datasetDao, DataverseDao dataverseDao,
                    UserNotificationService userNotificationService,
                    PermissionServiceBean permissionService, AuthenticationServiceBean authenticationServiceBean,
                    DataFileServiceBean fileService, IngestServiceBean ingestService,
                    EjbDataverseEngine commandEngine, IndexServiceBean indexService,
                    S3PackageImporter s3PackageImporter, SettingsServiceBean settingsService,
                    ExportService exportService, DatasetService datasetSvc,
                    DatasetsValidators datasetsValidators, OptionalFileParams optionalFileParamsSvc,
                    DataFileCreator dataFileCreator, DatasetThumbnailService datasetThumbnailService,
                    FileDownloadAPIHandler fileDownloadAPIHandler, DataverseRoleServiceBean rolesSvc,
                    RoleAssigneeServiceBean roleAssigneeSvc, PermissionServiceBean permissionSvc,
                    FileLabelsService fileLabelsService,
                    DatasetFileDownloadUrlCsvWriter fileDownloadUrlCsvWriter,
                    UningestInfoService uningestInfoService,
                    UningestService uningestService) {
        this.datasetDao = datasetDao;
        this.dataverseDao = dataverseDao;
        this.userNotificationService = userNotificationService;
        this.permissionService = permissionService;
        this.authenticationServiceBean = authenticationServiceBean;
        this.fileService = fileService;
        this.ingestService = ingestService;
        this.commandEngine = commandEngine;
        this.indexService = indexService;
        this.s3PackageImporter = s3PackageImporter;
        this.settingsService = settingsService;
        this.exportService = exportService;
        this.datasetSvc = datasetSvc;
        this.datasetsValidators = datasetsValidators;
        this.optionalFileParamsSvc = optionalFileParamsSvc;
        this.dataFileCreator = dataFileCreator;
        this.datasetThumbnailService = datasetThumbnailService;
        this.fileDownloadAPIHandler = fileDownloadAPIHandler;
        this.rolesSvc = rolesSvc;
        this.roleAssigneeSvc = roleAssigneeSvc;
        this.permissionSvc = permissionSvc;
        this.fileLabelsService = fileLabelsService;
        this.fileDownloadUrlCsvWriter = fileDownloadUrlCsvWriter;
        this.uningestInfoService = uningestInfoService;
        this.uningestService = uningestService;
    }

    // -------------------- LOGIC --------------------

    @GET
    @Path("{id}")
    public Response getDataset(@PathParam("id") String id) {
        return response(req -> {
            Dataset retrieved = execCommand(new GetDatasetCommand(req, findDatasetOrDie(id)));
            DatasetVersion latest = execCommand(new GetLatestAccessibleDatasetVersionCommand(req, retrieved));
            DatasetDTO dataset = new DatasetDTO.Converter().convert(retrieved);
            if (latest != null) {
                DatasetVersionDTO latestVersion = new DatasetVersionDTO.Converter().convert(latest);
                latestVersion = settingsService.isTrueForKey(SettingsServiceBean.Key.ExcludeEmailFromExport)
                        ? latestVersion.clearEmailFields() : latestVersion;
                Map<String, Object> dto = dataset.asMap();
                dto.put("latestVersion", latestVersion);
                return allowCors(ok(dto));
            } else {
                return allowCors(ok(dataset));
            }
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

        Optional<ExporterType> exporterConstant = ExporterType.fromPrefix(exporter);

        if (!exporterConstant.isPresent()) {
            return error(Response.Status.BAD_REQUEST, exporter + " is not a valid exporter");
        }

        Dataset dataset = datasetDao.findByGlobalId(persistentId);
        if (dataset == null) {
            return error(Response.Status.NOT_FOUND, "A dataset with the persistentId " + persistentId + " could not be found.");
        }

        Either<DataverseError, String> exportedDataset
                = exportService.exportDatasetVersionAsString(dataset.getReleasedVersion(), exporterConstant.get());

        if (exportedDataset.isLeft()) {
            return error(Response.Status.FORBIDDEN, exportedDataset.getLeft().getErrorMsg());
        }

        String mediaType = exportService.getMediaType(exporterConstant.get());
        return allowCors(Response.ok()
                .entity(exportedDataset.get())
                .type(mediaType)
                .build());
    }

    @DELETE
    @ApiWriteOperation
    @Path("{id}")
    public Response deleteDataset(@PathParam("id") String id) {
        // Internally, "DeleteDatasetCommand" simply redirects to "DeleteDatasetVersionCommand"
        // (and there's a comment that says "TODO: remove this command")
        //  do we need an exposed API call for it?
        // And DeleteDatasetVersionCommand further redirects to DestroyDatasetCommand, if the dataset only has 1
        // version... In other words, the functionality currently provided by this API is covered between the
        // "deleteDraftVersion" and "destroyDataset" API calls. (The logic below follows the current implementation of
        // the underlying commands!)

        return response(req -> {
            Dataset doomed = findDatasetOrDie(id);
            DatasetVersion doomedVersion = doomed.getLatestVersion();
            User u = findUserOrDie();
            boolean destroy = false;

            if (doomed.getVersions().size() == 1) {
                if (doomed.isReleased() && (!(u instanceof AuthenticatedUser) || !u.isSuperuser())) {
                    throw new WrappedResponse(
                            error(Response.Status.UNAUTHORIZED, "Only superusers can delete published datasets"));
                }
                destroy = true;
            } else {
                if (!doomedVersion.isDraft()) {
                    throw new WrappedResponse(
                            error(Response.Status.UNAUTHORIZED, "This is a published dataset with multiple versions. " +
                                    "This API can only delete the latest version if it is a DRAFT"));
                }
            }

            // Gather the locations of the physical files that will need to be deleted once the destroy command
            // execution has been finalized:
            Map<Long, String> deleteStorageLocations = fileService.getPhysicalFilesToDelete(doomedVersion, destroy);
            execCommand(new DeleteDatasetCommand(req, findDatasetOrDie(id)));

            // If we have gotten this far, the destroy command has succeeded, so we can finalize it by permanently
            // deleting the physical files: (DataFileService will double-check that the datafiles no longer exist in the
            // database, before attempting to delete the physical files)
            if (!deleteStorageLocations.isEmpty()) {
                fileService.finalizeFileDeletes(deleteStorageLocations);
            }

            return ok("Dataset " + id + " deleted");
        });
    }

    @DELETE
    @ApiWriteOperation
    @Path("{id}/destroy")
    public Response destroyDataset(@PathParam("id") String id) {
        return response(req -> {
            // first check if dataset is released, and if so, if user is a superuser
            Dataset doomed = findDatasetOrDie(id);
            User user = findUserOrDie();

            if (doomed.isReleased() && (!(user instanceof AuthenticatedUser) || !user.isSuperuser())) {
                throw new WrappedResponse(
                        error(Response.Status.UNAUTHORIZED, "Destroy can only be called by superusers."));
            }

            // Gather the locations of the physical files that will need to be deleted once the destroy command
            // execution has been finalized:
            Map<Long, String> deleteStorageLocations = fileService.getPhysicalFilesToDelete(doomed);
            execCommand(new DestroyDatasetCommand(doomed, req));

            // If we have gotten this far, the destroy command has succeeded, so we can finalize permanently deleting
            // the physical files: (DataFileService will double-check that the datafiles no longer exist in the
            // database, before attempting to delete the physical files)
            if (!deleteStorageLocations.isEmpty()) {
                fileService.finalizeFileDeletes(deleteStorageLocations);
            }
            return ok("Dataset " + id + " destroyed");
        });
    }

    @DELETE
    @ApiWriteOperation
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

            // Gather the locations of the physical files that will need to be deleted once the destroy command
            // execution has been finalized:

            Map<Long, String> deleteStorageLocations = fileService.getPhysicalFilesToDelete(doomed);

            execCommand(new DeleteDatasetVersionCommand(req, dataset));

            // If we have gotten this far, the delete command has succeeded - by either deleting the Draft version of a
            // published dataset, or destroying an unpublished one.
            // This means we can finalize permanently deleting the physical files: (DataFileService will double-check
            // that the datafiles no longer exist in the database, before attempting to delete the physical files)
            if (!deleteStorageLocations.isEmpty()) {
                fileService.finalizeFileDeletes(deleteStorageLocations);
            }

            return ok("Draft version of dataset " + id + " deleted");
        });
    }

    @DELETE
    @ApiWriteOperation
    @Path("{datasetId}/deleteLink/{linkedDataverseId}")
    public Response deleteDatasetLinkingDataverse(@PathParam("datasetId") String datasetId, @PathParam("linkedDataverseId") String linkedDataverseId) {
        boolean index = true;
        return response(req -> {
            execCommand(new DeleteDatasetLinkingDataverseCommand(
                    req, findDatasetOrDie(datasetId), findDatasetLinkingDataverseOrDie(datasetId, linkedDataverseId), index));
            return ok("Link from Dataset " + datasetId + " to linked Dataverse " + linkedDataverseId + " deleted");
        });
    }

    @PUT
    @ApiWriteOperation
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
            return ok("Citation Date for dataset " + id + " set to: "
                    + (dsfType != null ? dsfType.getDisplayName() : "default"));
        });
    }

    @DELETE
    @ApiWriteOperation
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
        DatasetVersionDTO.Converter converter = new DatasetVersionDTO.Converter();
        boolean excludeEmails = settingsService.isTrueForKey(SettingsServiceBean.Key.ExcludeEmailFromExport);
        return allowCors(response(req -> ok(
                execCommand(new ListVersionsCommand(req, findDatasetOrDie(id))).stream()
                        .map(v -> {
                            DatasetVersionDTO dto = converter.convert(v);
                            return excludeEmails ? dto.clearEmailFields() : dto;
                        })
                        .collect(Collectors.toList()))));
    }

    @GET
    @Path("{id}/versions/{versionId}")
    public Response getVersion(@PathParam("id") String datasetId, @PathParam("versionId") String versionId) {
        return allowCors(response(req -> {
            DatasetVersion datasetVersion = getDatasetVersionOrDie(req, versionId, findDatasetOrDie(datasetId));
            DatasetVersionDTO dto = new DatasetVersionDTO.Converter().convert(datasetVersion);
            return ok(settingsService.isTrueForKey(SettingsServiceBean.Key.ExcludeEmailFromExport)
                    ? dto.clearEmailFields() : dto);
        }));
    }

    @GET
    @Path("{id}/uningest")
    public Response listUningestableFiles(@PathParam("id") String datasetId) {
        return response(req -> {
            Dataset dataset = findDatasetOrDie(datasetId);

            if (!permissionSvc.requestOn(req, dataset).has(Permission.ViewUnpublishedDataset)) {
                return forbidden("You are not permitted to view unpublished dataset.");
            }

            return ok(uningestInfoService.listUningestableFiles(dataset).stream()
                    .map(UningestableItemDTO::fromDatafile)
                    .collect(Collectors.toList()));
        });
    }

    @POST
    @ApiWriteOperation
    @Path("{id}/uningest")
    public Response uningestFiles(@PathParam("id") String datasetId, JsonObject json) {
        return response(req -> {
            findSuperuserOrDie();

            UningestRequestDTO rq = jsonParser().parseUningestRequest(json);
            Dataset dataset = findDatasetOrDie(datasetId);

            List<DataFile> dataFiles = uningestInfoService.listUningestableFiles(dataset).stream()
                    .filter(df -> rq.getDataFileIds().isEmpty() || rq.getDataFileIds().contains(df.getId()))
                    .collect(Collectors.toList());

            List<String> uningestFailedFileIds = new ArrayList<>();
            for(DataFile df : dataFiles) {
                try {
                    uningestService.uningest(df, req.getAuthenticatedUser());
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error occurred during the uningest of data file: " + df.getId(), e);
                    uningestFailedFileIds.add(df.getId().toString());
                }
            }

            if (uningestFailedFileIds.isEmpty()) {
                return ok("Uningest performed on " + dataFiles.size() + " files.");
            } else {
                return ok("Uningest failed on " + uningestFailedFileIds.size() + " of " + dataFiles.size() +
                        " files. Failed ids: " + String.join(", ", uningestFailedFileIds));
            }
        });
    }

    @GET
    @Path("{id}/versions/{versionId}/files")
    public Response listVersionFiles(@PathParam("id") String datasetId, @PathParam("versionId") String versionId) {
        return allowCors(response(req -> ok(new FileMetadataDTO.Converter().convert(
                getDatasetVersionOrDie(req, versionId, findDatasetOrDie(datasetId)).getFileMetadatas()))));
    }

    @GET
    @Path("{id}/versions/{versionId}/files/download")
    @Produces({"application/zip"})
    public Response getVersionFiles(@PathParam("id") String datasetId, @PathParam("versionId") String versionId, @QueryParam("gbrecs") boolean gbrecs,
                                    @Context HttpServletResponse response, @Context UriInfo uriInfo) {

        User apiTokenUser = Try.of(this::findUserOrDie)
                               .onFailure(throwable -> logger.log(Level.FINE, "Failed finding user for apiToken: ", throwable))
                               .get();

        String finalVersionId = versionId;
        if (!versionId.matches("[0-9]+")) {
            DataverseRequest dataverseRequest = createDataverseRequest(apiTokenUser);
            try {
                Dataset dataset = findDatasetOrDie(datasetId);
                DatasetVersion datasetVersion = getDatasetVersionOrDie(dataverseRequest, versionId, dataset);
                finalVersionId = datasetVersion.getId().toString();
            } catch (WrappedResponse wr) {
                return wr.getResponse();
            }
        }

        boolean originalFormatRequested = isOriginalFormatRequested(uriInfo.getQueryParameters());

        response.setHeader("Content-disposition", "attachment; filename=\"dataverse_files.zip\"");
        response.setHeader("Content-Type", "application/zip; name=\"dataverse_files.zip\"");

        StreamingOutput fileStream = fileDownloadAPIHandler.downloadFiles(apiTokenUser, finalVersionId, originalFormatRequested, gbrecs);
        return Response.ok(fileStream).build();
    }

    @GET
    @Path("{id}/versions/{versionId}/files/urls")
    @Produces({"text/csv"})
    public Response getVersionFilesUrls(@PathParam("id") String datasetId, @PathParam("versionId") String versionId) {
        return allowCors(response(req -> {
            Dataset dataset = findDatasetOrDie(datasetId);
            if (dataset.hasActiveEmbargo()) {
                return badRequest("Requested dataset is under embargo.");
            }

            if (dataset.getGuestbook() != null && dataset.getGuestbook().isEnabled() && dataset.getGuestbook().getDataverse() != null) {
                return badRequest("Requested dataset has guestbook enabled.");
            }

            DatasetVersion datasetVersion = getDatasetVersionOrDie(req, versionId, dataset);
            if (!datasetVersion.isReleased()) {
                return badRequest("Requested version has not been released.");
            }

            StreamingOutput csvContent = output -> fileDownloadUrlCsvWriter.write(output, datasetVersion.getFileMetadatas());

            return Response.ok(csvContent)
                    .header("Content-Disposition", "attachment; filename=\"dataset-file-urls.csv\"")
                    .build();
        }));
    }

    @GET
    @Path("{id}/versions/{versionId}/metadata")
    public Response getVersionMetadata(@PathParam("id") String datasetId, @PathParam("versionId") String versionId) {
        MetadataBlockWithFieldsDTO.Creator creator = new MetadataBlockWithFieldsDTO.Creator();
        return allowCors(response(r -> {
            List<DatasetField> fields = getDatasetVersionOrDie(r, versionId, findDatasetOrDie(datasetId)).getDatasetFields();
            Map<String, MetadataBlockWithFieldsDTO> dto = DatasetField.groupByBlock(fields)
                    .entrySet().stream()
                    .map(e -> creator.create(e.getKey(), e.getValue()))
                    .collect(Collectors.toMap(
                            MetadataBlockWithFieldsDTO::getDisplayName, Function.identity(),
                            (prev, next) -> next, LinkedHashMap::new));
            if (settingsService.isTrueForKey(SettingsServiceBean.Key.ExcludeEmailFromExport)) {
                dto.values().forEach(MetadataBlockWithFieldsDTO::clearEmailFields);
            }
            return ok(dto);
        }));
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
                    MetadataBlockWithFieldsDTO blockWithFields = new MetadataBlockWithFieldsDTO.Creator().create(p.getKey(), p.getValue());
                    if (settingsService.isTrueForKey(SettingsServiceBean.Key.ExcludeEmailFromExport)) {
                        blockWithFields.clearEmailFields();
                    }
                    return ok(blockWithFields);
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
    @ApiWriteOperation
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
    @ApiWriteOperation
    @Path("{id}/modifyRegistrationMetadata")
    public Response updateDatasetPIDMetadata(@PathParam("id") String id) {
        try {
            Dataset dataset = findDatasetOrDie(id);
            if (!dataset.isReleased()) {
                return error(Response.Status.BAD_REQUEST,
                        BundleUtil.getStringFromBundle("datasets.api.updatePIDMetadata.failure.dataset.must.be.released"));
            }
        } catch (WrappedResponse ex) {
            Logger.getLogger(Datasets.class.getName()).log(Level.SEVERE, null, ex);
        }

        return response(req -> {
            execCommand(new UpdateDvObjectPIDMetadataCommand(findDatasetOrDie(id), req));
            List<String> args = Collections.singletonList(id);
            return ok(BundleUtil.getStringFromBundle("datasets.api.updatePIDMetadata.success.for.single.dataset", args));
        });
    }

    @GET
    @ApiWriteOperation
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
    @ApiWriteOperation
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
                Dataset managedDataset = execCommand(new UpdateDatasetVersionCommand(ds, req));
                managedVersion = managedDataset.getEditVersion();
            } else {
                managedVersion = execCommand(new CreateDatasetVersionCommand(req, ds, incomingVersion));
            }
            DatasetVersionDTO dto = new DatasetVersionDTO.Converter().convert(managedVersion);
            return ok(settingsService.isTrueForKey(SettingsServiceBean.Key.ExcludeEmailFromExport)
                    ? dto.clearEmailFields() : dto);
        } catch (JsonParseException ex) {
            logger.log(Level.SEVERE, "Semantic error parsing dataset version Json: " + ex.getMessage(), ex);
            return error(Response.Status.BAD_REQUEST, "Error parsing dataset version: " + ex.getMessage());

        } catch (WrappedResponse ex) {
            return ex.getResponse();
        }
    }

    @PUT
    @ApiWriteOperation
    @Path("{id}/deleteMetadata")
    public Response deleteVersionMetadata(String jsonBody, @PathParam("id") String id) throws WrappedResponse {
        DataverseRequest req = createDataverseRequest(findUserOrDie());
        return processDatasetFieldDataDelete(jsonBody, id, req);
    }

    @PUT
    @ApiWriteOperation
    @Path("{id}/setEmbargo")
    public Response setEmbargoDate(@PathParam("id") String id, @QueryParam("date") String date) {
        try {
            Dataset dataset = findDatasetOrDie(id);
            SimpleDateFormat dateFormat = new SimpleDateFormat(settingsService.getValueForKey(SettingsServiceBean.Key.DefaultDateFormat));
            if(date == null) {
                throw new WrappedResponse(badRequest(BundleUtil.getStringFromBundle(
                        "datasets.api.setEmbargo.failure.badDate.missing",
                        settingsSvc.getValueForKey(SettingsServiceBean.Key.DefaultDateFormat))));
            }
            Date embargoDate = dateFormat.parse(date);
            datasetsValidators.validateEmbargoDate(embargoDate);
            dataset = datasetSvc.setDatasetEmbargoDate(dataset, embargoDate);
            return ok(BundleUtil.getStringFromBundle("datasets.api.setEmbargo.success",
                    dataset.getGlobalId(), dataset.getEmbargoDate().get().toInstant()));
        } catch (WrappedResponse wr) {
            return wr.getResponse();
        } catch (ParseException pe) {
            return badRequest(BundleUtil.getStringFromBundle("datasets.api.setEmbargo.failure.badDate.format",
                    settingsSvc.getValueForKey(SettingsServiceBean.Key.DefaultDateFormat)));
        } catch (InvalidArgumentException iae) {
            return badRequest(iae.getMessage());
        } catch (EJBException ise) {
            return badRequest(ise.getCause().getMessage());
        } catch (PermissionException pe) {
            return badRequest(BundleUtil.getStringFromBundle("datasets.api.setEmbargo.failure.missingPermissions",
                    pe.getMissingPermissions().toString()));
        } catch (Exception e) {
            return badRequest(BundleUtil.getStringFromBundle("datasets.api.setEmbargo.failure.unknown", e.getMessage()));
        }
    }

    @PUT
    @ApiWriteOperation
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

    @PUT
    @ApiWriteOperation
    @Path("{id}/editMetadata")
    public Response editVersionMetadata(String jsonBody, @PathParam("id") String id, @QueryParam("replace") Boolean replace)
            throws WrappedResponse {
        Boolean replaceData = replace != null;
        DataverseRequest req = createDataverseRequest(findUserOrDie());
        return processDatasetUpdate(jsonBody, id, req, replaceData);
    }

    /**
     * @deprecated This was shipped as a GET but should have been a POST, see https://github.com/IQSS/dataverse/issues/2431
     */
    @GET
    @ApiWriteOperation
    @Path("{id}/actions/:publish")
    @Deprecated
    public Response publishDataseUsingGetDeprecated(@PathParam("id") String id, @QueryParam("type") String type) {
        logger.info("publishDataseUsingGetDeprecated called on id " + id + ". Encourage use of POST rather than GET, which is deprecated.");
        return publishDataset(id, type);
    }

    @POST
    @ApiWriteOperation
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
                    CuratePublishedDatasetVersionCommand cmd =
                            new CuratePublishedDatasetVersionCommand(ds, createDataverseRequest(user));
                    ds = commandEngine.submit(cmd);
                    successMsg = BundleUtil.getStringFromBundle("datasetversion.update.success");

                    // If configured, update archive copy as well
                    String className = settingsService.getValueForKey(SettingsServiceBean.Key.ArchiverClassName);
                    DatasetVersion updateVersion = ds.getLatestVersion();
                    AbstractSubmitToArchiveCommand archiveCommand = ArchiverUtil.createSubmitToArchiveCommand(
                            className, createDataverseRequest(user), updateVersion, authenticationServiceBean, Clock.systemUTC());
                    if (archiveCommand != null) {
                        // Delete the record of any existing copy since it is now out of date/incorrect
                        updateVersion.setArchivalCopyLocation(null);

                        // Then try to generate and submit an archival copy. Note that running this command within the
                        // CuratePublishedDatasetVersionCommand was causing an error:
                        // "The attribute [id] of class [edu.harvard.iq.dataverse.DatasetFieldCompoundValue] is mapped
                        // to a primary key column in the database. Updates are not allowed."
                        // To avoid that, and to simplify reporting back to the GUI whether this optional step
                        // succeeded, I've pulled this out as a separate submit().
                        try {
                            updateVersion = commandEngine.submit(archiveCommand);
                            successMsg = BundleUtil.getStringFromBundle(updateVersion.getArchivalCopyLocation() != null
                                    ? "datasetversion.update.archive.success" : "datasetversion.update.archive.failure");
                        } catch (CommandException ex) {
                            successMsg = BundleUtil.getStringFromBundle("datasetversion.update.archive.failure") + " - " + ex.toString();
                            logger.severe(ex.getMessage());
                        }
                    }
                } catch (CommandException ex) {
                    errorMsg = BundleUtil.getStringFromBundle("datasetversion.update.failure") + " - " + ex.toString();
                    logger.severe(ex.getMessage());
                }
                return errorMsg != null
                        ? error(Response.Status.INTERNAL_SERVER_ERROR, errorMsg)
                        : ok(new DatasetDTO.Converter().convert(ds), successMsg);
            } else {
                PublishDatasetResult res = execCommand(new PublishDatasetCommand(ds, createDataverseRequest(user), isMinor));
                DatasetDTO dto = new DatasetDTO.Converter().convert(res.getDataset());
                return res.isCompleted() ? ok(dto) : accepted(dto);
            }
        } catch (WrappedResponse ex) {
            return ex.getResponse();
        } catch (NoDatasetFilesException ex) {
            return error(Response.Status.INTERNAL_SERVER_ERROR, "Unable to publish dataset, since there are no files in it.");
        }
    }

    @POST
    @ApiWriteOperation
    @Path("{id}/move/{targetDataverseAlias}")
    public Response moveDataset(@PathParam("id") String id, @PathParam("targetDataverseAlias") String targetDataverseAlias,
                                @QueryParam("forceMove") Boolean force) {
        try {
            User user = findUserOrDie();
            Dataset dataset = findDatasetOrDie(id);
            Dataverse target = dataverseDao.findByAlias(targetDataverseAlias);
            if (target == null) {
                return error(Response.Status.BAD_REQUEST, "Target Dataverse not found.");
            }
            //Command requires Super user - it will be tested by the command
            execCommand(new MoveDatasetCommand(createDataverseRequest(user), dataset, target, force));
            return ok("Dataset moved successfully");
        } catch (WrappedResponse ex) {
            return ex.getResponse();
        }
    }

    @PUT
    @ApiWriteOperation
    @Path("{linkedDatasetId}/link/{linkingDataverseAlias}")
    public Response linkDataset(@PathParam("linkedDatasetId") String linkedDatasetId,
                                @PathParam("linkingDataverseAlias") String linkingDataverseAlias) {
        try {
            User user = findUserOrDie();
            Dataset linked = findDatasetOrDie(linkedDatasetId);
            Dataverse linking = findDataverseOrDie(linkingDataverseAlias);
            if (linked == null) {
                return error(Response.Status.BAD_REQUEST, "Linked Dataset not found.");
            }
            if (linking == null) {
                return error(Response.Status.BAD_REQUEST, "Linking Dataverse not found.");
            }
            execCommand(new LinkDatasetCommand(createDataverseRequest(user), linking, linked));
            return ok("Dataset " + linked.getId() + " linked successfully to " + linking.getAlias());
        } catch (WrappedResponse ex) {
            return ex.getResponse();
        }
    }

    @GET
    @Path("{id}/links")
    public Response getLinks(@PathParam("id") String idSupplied) {
        try {
            User user = findUserOrDie();
            if (!user.isSuperuser()) {
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
    @ApiWriteOperation
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
            DataverseRole theRole = rolesSvc.findBuiltinRoleByAlias(BuiltInRole.ADMIN);
            String privateUrlToken = null;
            return ok(
                    new RoleAssignmentDTO.Converter().convert(execCommand(
                            new AssignRoleCommand(assignee, theRole, dataset, createDataverseRequest(findUserOrDie()), privateUrlToken))));
        } catch (WrappedResponse ex) {
            logger.log(Level.WARNING, "Can''t create assignment: {0}", ex.getMessage());
            return ex.getResponse();
        }
    }

    @GET
    @Path("{identifier}/assignments")
    public Response getAssignments(@PathParam("identifier") String id) {
        RoleAssignmentDTO.Converter converter = new RoleAssignmentDTO.Converter();
        return response(req -> ok(execCommand(new ListRoleAssignments(req,
                findDatasetOrDie(id))).stream()
                .map(converter::convert)
                .collect(Collectors.toList())));
    }

    @GET
    @Path("{id}/privateUrl")
    public Response getPrivateUrlData(@PathParam("id") String idSupplied) {
        return response(req -> {
            PrivateUrl privateUrl = execCommand(new GetPrivateUrlCommand(req, findDatasetOrDie(idSupplied)));
            return privateUrl != null
                    ? ok(new PrivateUrlDTO.Converter().convert(privateUrl))
                    : error(Response.Status.NOT_FOUND, "Private URL not found.");
        });
    }

    @POST
    @ApiWriteOperation
    @Path("{id}/privateUrl")
    public Response createPrivateUrl(@PathParam("id") String idSupplied) {
        return response(req -> ok(
                new PrivateUrlDTO.Converter().convert(
                        execCommand(new CreatePrivateUrlCommand(req, findDatasetOrDie(idSupplied))))));
    }

    @DELETE
    @ApiWriteOperation
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
                canUpdateThumbnail = permissionSvc.requestOn(createDataverseRequest(findUserOrDie()), dataset)
                        .canIssue(UpdateDatasetThumbnailCommand.class);
            } catch (WrappedResponse ex) {
                logger.info("Exception thrown while trying to figure out permissions while getting thumbnail for dataset id "
                        + dataset.getId() + ": " + ex.getLocalizedMessage());
            }
            if (!canUpdateThumbnail) {
                return error(Response.Status.FORBIDDEN, "You are not permitted to list dataset thumbnail candidates.");
            }
            JsonArrayBuilder data = Json.createArrayBuilder();
            for (DatasetThumbnail datasetThumbnail : datasetThumbnailService.getThumbnailCandidates(dataset, true)) {
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
            InputStream is = datasetThumbnailService.getThumbnailAsInputStream(dataset);
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
    @ApiWriteOperation
    @Path("{id}/thumbnail/{dataFileId}")
    public Response setDataFileAsThumbnail(@PathParam("id") String idSupplied, @PathParam("dataFileId") long dataFileIdSupplied) {
        try {
            DatasetThumbnail datasetThumbnail = execCommand(
                    new UpdateDatasetThumbnailCommand(createDataverseRequest(findUserOrDie()), findDatasetOrDie(idSupplied),
                            UpdateDatasetThumbnailCommand.UserIntent.setDatasetFileAsThumbnail, dataFileIdSupplied, null));
            return ok("Thumbnail set to " + datasetThumbnail.getBase64image());
        } catch (WrappedResponse wr) {
            return wr.getResponse();
        }
    }

    @POST
    @ApiWriteOperation
    @Path("{id}/thumbnail")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response uploadDatasetLogo(@PathParam("id") String idSupplied, @FormDataParam("file") InputStream inputStream
    ) {
        try {
            DatasetThumbnail datasetThumbnail = execCommand(
                    new UpdateDatasetThumbnailCommand(createDataverseRequest(findUserOrDie()), findDatasetOrDie(idSupplied),
                    UpdateDatasetThumbnailCommand.UserIntent.setNonDatasetFileAsThumbnail, null, inputStream));
            return ok("Thumbnail is now " + datasetThumbnail.getBase64image());
        } catch (WrappedResponse wr) {
            return wr.getResponse();
        }
    }

    @DELETE
    @ApiWriteOperation
    @Path("{id}/thumbnail")
    public Response removeDatasetLogo(@PathParam("id") String idSupplied) {
        try {
            execCommand(new UpdateDatasetThumbnailCommand(createDataverseRequest(findUserOrDie()), findDatasetOrDie(idSupplied),
                            UpdateDatasetThumbnailCommand.UserIntent.removeThumbnail, null, null));
            return ok("Dataset thumbnail removed.");
        } catch (WrappedResponse wr) {
            return wr.getResponse();
        }
    }

    @GET
    @ApiWriteOperation
    @Path("{identifier}/dataCaptureModule/rsync")
    public Response getRsync(@PathParam("identifier") String id) {
        //TODO - does it make sense to switch this to dataset identifier for consistency with the rest of the DCM APIs?
        if (!DataCaptureModuleUtil.rsyncSupportEnabled(settingsSvc.getValueForKey(SettingsServiceBean.Key.UploadMethods))) {
            return error(Response.Status.METHOD_NOT_ALLOWED,
                         SettingsServiceBean.Key.UploadMethods + " does not contain " + SystemConfig.FileUploadMethods.RSYNC + ".");
        }
        Dataset dataset;
        try {
            dataset = findDatasetOrDie(id);
            AuthenticatedUser user = findAuthenticatedUserOrDie();
            ScriptRequestResponse scriptRequestResponse = execCommand(
                    new RequestRsyncScriptCommand(createDataverseRequest(user), dataset));

            DatasetLock lock = datasetDao.addDatasetLock(
                    dataset.getId(), DatasetLock.Reason.DcmUpload, user.getId(), "script downloaded");
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
    @ApiWriteOperation
    @Path("{identifier}/dataCaptureModule/checksumValidation")
    public Response receiveChecksumValidationResults(@PathParam("identifier") String id, JsonObject jsonFromDcm) {
        logger.log(Level.FINE, "jsonFromDcm: {0}", jsonFromDcm);
        AuthenticatedUser authenticatedUser;
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

                String storageDriver = (System.getProperty("dataverse.files.storage-driver-id") != null)
                        ? System.getProperty("dataverse.files.storage-driver-id") : "file";
                String uploadFolder = jsonFromDcm.getString("uploadFolder");
                int totalSize = jsonFromDcm.getInt("totalSize");

                if (storageDriver.equals("file")) {
                    logger.log(Level.INFO, "File storage driver used for (dataset id={0})", dataset.getId());

                    ImportMode importMode = ImportMode.MERGE;
                    try {
                        JsonObject jsonFromImportJobKickoff = execCommand(new ImportFromFileSystemCommand(
                                createDataverseRequest(findUserOrDie()), dataset, uploadFolder, (long) totalSize, importMode));
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
                        DataFile packageFile = s3PackageImporter.createPackageDataFile(dataset, uploadFolder, totalSize);

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
                                        new DataverseRequest(authenticatedUser, (HttpServletRequest) null));
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
                distinctAuthors.values().forEach((value) -> userNotificationService.sendNotificationWithEmail(value,
                        new Timestamp(new Date().getTime()), NotificationType.CHECKSUMFAIL, dataset.getId(), NotificationObjectType.DATASET));
                List<AuthenticatedUser> superUsers = authenticationServiceBean.findSuperUsers();
                if (superUsers != null && !superUsers.isEmpty()) {
                    superUsers.forEach((au) -> userNotificationService.sendNotificationWithEmail(au,
                            new Timestamp(new Date().getTime()), NotificationType.CHECKSUMFAIL, dataset.getId(), NotificationObjectType.DATASET));
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
    @ApiWriteOperation
    @Path("{id}/submitForReview")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response submitForReview(@PathParam("id") String idSupplied, SubmitForReviewDataDTO submitForReviewData) {
        try {
            Dataset updatedDataset = execCommand(new SubmitDatasetForReviewCommand(createDataverseRequest(findUserOrDie()),
                    findDatasetOrDie(idSupplied), submitForReviewData.getComment()));
            JsonObjectBuilder result = Json.createObjectBuilder();
            result.add("inReview", updatedDataset.isLockedFor(DatasetLock.Reason.InReview));
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
    @ApiWriteOperation
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
            String reasonForReturn;
            reasonForReturn = json.getString("reasonForReturn");
            // TODO: Once we add a box for the curator to type into, pass the reason for return to the
            //  ReturnDatasetToAuthorCommand and delete this check and call to setReturnReason on the API side.
            if (reasonForReturn == null || reasonForReturn.isEmpty()) {
                return error(Response.Status.BAD_REQUEST,
                             "You must enter a reason for returning a dataset to the author(s).");
            }
            AuthenticatedUser authenticatedUser = findAuthenticatedUserOrDie();
            Map<String, String> params = new HashMap<>();
            params.put(NotificationParameter.MESSAGE.key(), reasonForReturn);
            params.put(NotificationParameter.REPLY_TO.key(), authenticatedUser.getEmail());
            Dataset updatedDataset = execCommand(new ReturnDatasetToAuthorCommand(createDataverseRequest(
                    authenticatedUser), dataset, params));

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
     */
    @POST
    @ApiWriteOperation
    @Path("{id}/add")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response addFileToDataset(@PathParam("id") String idSupplied,
                                     @FormDataParam("jsonData") String jsonData,
                                     @FormDataParam("file") InputStream fileInputStream,
                                     @FormDataParam("file") FormDataContentDisposition contentDispositionHeader,
                                     @FormDataParam("file") final FormDataBodyPart formDataBodyPart) {
        if (!systemConfig.isHTTPUpload()) {
            return error(Response.Status.SERVICE_UNAVAILABLE, BundleUtil.getStringFromBundle("file.api.httpDisabled"));
        }

        // (1) Get the user from the API key
        User authUser;
        try {
            authUser = findUserOrDie();
        } catch (WrappedResponse ex) {
            return error(Response.Status.FORBIDDEN,
                         BundleUtil.getStringFromBundle("file.addreplace.error.auth")
            );
        }

        // (2) Get the Dataset Id
        Dataset dataset;
        try {
            dataset = findDatasetOrDie(idSupplied);
        } catch (WrappedResponse wr) {
            return wr.getResponse();
        }

        // (2a) Make sure dataset does not have package file
        for (DatasetVersion dv : dataset.getVersions()) {
            if (dv.isHasPackageFile()) {
                return error(Response.Status.FORBIDDEN,
                             ResourceBundle.getBundle("Bundle").getString("file.api.alreadyHasPackageFile")
                );
            }
        }

        // (3) Get the file name and content type
        String newFilename = contentDispositionHeader.getFileName();
        String newFileContentType = formDataBodyPart.getMediaType().toString();

        // (2a) Load up optional params via JSON
        OptionalFileParams optionalFileParams;
        logger.fine("Loading (api) jsonData: " + jsonData);

        try {
            optionalFileParams = optionalFileParamsSvc.create(jsonData);
        } catch (DataFileTagException ex) {
            return error(Response.Status.BAD_REQUEST, ex.getMessage());
        }

        try {
            datasetsValidators.validateFileTermsOfUseDTO(optionalFileParams.getFileTermsOfUseDTO());
        } catch (MissingArgumentException | InvalidParameterException pe) {
            return error(Response.Status.BAD_REQUEST, pe.getMessage());
        } catch (EJBException ejbe) {
            return error(Response.Status.BAD_REQUEST, ejbe.getCause().getMessage());
        }

        // (3) Create the AddReplaceFileHelper object
        DataverseRequest dvRequest2 = createDataverseRequest(authUser);
        AddReplaceFileHelper addFileHelper =
                new AddReplaceFileHelper(dvRequest2, ingestService, fileService, dataFileCreator, permissionSvc, commandEngine, optionalFileParamsSvc);

        // (4) Run "runAddFileByDatasetId"
        try {
            addFileHelper.runAddFileByDataset(dataset, newFilename, newFileContentType, fileInputStream, optionalFileParams);
        } finally {
            IOUtils.closeQuietly(fileInputStream);
        }

        if (addFileHelper.hasError()) {
            return error(addFileHelper.getHttpErrorCode(), addFileHelper.getErrorMessagesAsString("\n"));
        } else {
            String successMsg = BundleUtil.getStringFromBundle("file.addreplace.success.add");
            try {
                // Todo We need a consistent, sane way to communicate a human readable message to an API client suitable
                // for human consumption. Imagine if the UI were built in Angular or React and we want to return a
                // message from the API as-is to the user. Human readable.
                logger.fine("successMsg: " + successMsg);
                return ok(addFileHelper.getSuccessResult());
                // "Look at that!  You added a file! (hey hey, it may have worked)");
            } catch (NoFilesException ex) {
                Logger.getLogger(Files.class.getName()).log(Level.SEVERE, null, ex);
                return error(Response.Status.BAD_REQUEST, "NoFileException!  Serious Error! See administrator!");
            }
        }
    }

    @GET
    @Path("{identifier}/locks")
    public Response getLocks(@PathParam("identifier") String id, @QueryParam("type") DatasetLock.Reason lockType) {
        Dataset dataset;
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
            List<DatasetLockDTO> allLocks = locks.stream()
                    .map(l -> new DatasetLockDTO.Converter().convert(l))
                    .collect(Collectors.toList());
            return ok(allLocks);
        } catch (WrappedResponse wr) {
            return wr.getResponse();
        }
    }

    @DELETE
    @ApiWriteOperation
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
                        // kick of dataset reindexing, in case the locks removed affected the search card:
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
                    // ... and kick of dataset reindexing, in case the lock removed affected the search card:
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
    @ApiWriteOperation
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

    @GET
    @Path("{id}/filelabels")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listLabels(@PathParam("id") String datasetId) throws WrappedResponse {
        Dataset dataset = findDatasetOrDie(datasetId);
        return ok(fileLabelsService.prepareFileLabels(dataset, new FileLabelsChangeOptionsDTO()));
    }

    @POST
    @ApiWriteOperation
    @Path("{id}/filelabels")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response changeLabels(@PathParam("id") String datasetId, FileLabelsChangeOptionsDTO options) throws WrappedResponse {
        Dataset dataset = findDatasetOrDie(datasetId);
        List<FileLabelInfo> changedLabels;
        try {
            changedLabels = fileLabelsService.changeLabels(fileLabelsService.prepareFileLabels(dataset, options), options);
            List<FileLabelInfo> result = fileLabelsService.updateDataset(dataset, changedLabels, options);
            return ok(result.stream().filter(FileLabelInfo::isAffected).collect(Collectors.toList()));
        } catch (EJBException ee) {
            if (ee.getCause() instanceof IllegalStateException) {
                throw new WrappedResponse(badRequest("Error occurred – probably input contained duplicated filenames"));
            } else {
                throw ee;
            }
        }
    }

    // -------------------- PRIVATE --------------------

    private Response processDatasetFieldDataDelete(String jsonBody, String id, DataverseRequest req) {
        try (StringReader rdr = new StringReader(jsonBody)) {

            Dataset dataset = findDatasetOrDie(id);
            JsonObject json = Json.createReader(rdr).readObject();
            DatasetVersion dsv = dataset.getEditVersion();

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
                                        logger.log(Level.SEVERE, String.format("Delete metadata failed: %s: %s not found.",
                                                cvv.getDatasetFieldType().getDisplayName(), cvv.getStrValue()));
                                        return error(Response.Status.BAD_REQUEST,
                                                String.format("Delete metadata failed: %s: %s not found.",
                                                        cvv.getDatasetFieldType().getDisplayName(), cvv.getStrValue()));
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
                                if (oldField.getFieldValue().getOrElse("")
                                        .equals(removableField.getFieldValue().getOrElse(""))) {
                                    oldField.setFieldValue(null);
                                    valueFound = true;
                                }
                            } else {
                                if (DatasetFieldUtil.joinAllValues(removableField)
                                        .equals(DatasetFieldUtil.joinAllValues(oldField))) {
                                    dsfChildsToRemove.addAll(oldField.getDatasetFieldsChildren());
                                    valueFound = true;
                                }
                            }
                        }
                    }
                    if (!valueFound) {
                        String displayValue = !removableField.getDisplayValue().isEmpty()
                                ? removableField.getDisplayValue() : removableField.getCompoundDisplayValue();
                        logger.log(Level.SEVERE, String.format("Delete metadata failed: %s: %s not found.",
                                removableField.getDatasetFieldType().getDisplayName(), displayValue));
                        return error(Response.Status.BAD_REQUEST, String.format("Delete metadata failed: %s: %s not found.",
                                removableField.getDatasetFieldType().getDisplayName(), displayValue));
                    }
                }
            }

            fields.stream()
                    .map(DatasetField::getDatasetFieldsChildren)
                    .forEach(datasetFields -> datasetFields.removeAll(dsfChildsToRemove));

            boolean updateDraft = dataset.getLatestVersion().isDraft();
            DatasetVersion managedVersion = updateDraft
                    ? execCommand(new UpdateDatasetVersionCommand(dataset, req)).getEditVersion()
                    : execCommand(new CreateDatasetVersionCommand(req, dataset, dsv));
            DatasetVersionDTO dto = new DatasetVersionDTO.Converter().convert(managedVersion);
            return ok(settingsService.isTrueForKey(SettingsServiceBean.Key.ExcludeEmailFromExport)
                    ? dto.clearEmailFields() : dto);
        } catch (JsonParseException ex) {
            logger.log(Level.SEVERE, "Semantic error parsing dataset update Json: " + ex.getMessage(), ex);
            return error(Response.Status.BAD_REQUEST, "Error processing metadata delete: " + ex.getMessage());
        } catch (WrappedResponse ex) {
            logger.log(Level.SEVERE, "Delete metadata error: " + ex.getMessage(), ex);
            return ex.getResponse();
        }
    }

    private Response processDatasetUpdate(String jsonBody, String id, DataverseRequest req, Boolean replaceData) {
        try (StringReader rdr = new StringReader(jsonBody)) {

            Dataset ds = findDatasetOrDie(id);
            JsonObject json = Json.createReader(rdr).readObject();
            DatasetVersion dsv = ds.getEditVersion();

            List<DatasetField> freshFieldsModel;

            JsonArray fieldsJson = json.getJsonArray("fields");
            freshFieldsModel = fieldsJson == null
                    ? new LinkedList<>(jsonParser().parseField(json, Boolean.FALSE))
                    : jsonParser().parseMultipleFields(json);

            String valdationErrors = validateDatasetFieldValues(freshFieldsModel);

            if (!valdationErrors.isEmpty()) {
                logger.log(Level.SEVERE, "Semantic error parsing dataset update Json: " + valdationErrors, valdationErrors);
                return error(Response.Status.BAD_REQUEST, "Error parsing dataset update: " + valdationErrors);
            }

            dsv.setVersionState(DatasetVersion.VersionState.DRAFT);

            // loop through the update fields and compare to the version fields
            // if exist add/replace values if not add entire dsf
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
                                        if (!oldField.getFieldValue().getOrElse("")
                                                .equals(updateField.getFieldValue().getOrElse(""))) {
                                            updateField.setDatasetVersion(dsv);
                                            fieldsToAdd.add(updateField);
                                        }
                                    } else {
                                        oldField.setFieldValue(updateField.getValue());
                                    }
                                } else {
                                    if (!DatasetFieldUtil.joinAllValues(updateField)
                                            .equals(DatasetFieldUtil.joinAllValues(oldField))) {
                                        updateField.setDatasetVersion(dsv);
                                        fieldsToAdd.add(updateField);
                                    }
                                }
                            }
                        } else {
                            return error(Response.Status.BAD_REQUEST, String.format("You may not add data to a field that " +
                                    "already has data and does not allow multiples. Use replace=true to replace existing data (%s)",
                                    oldField.getDatasetFieldType().getDisplayName()));
                        }
                        break;
                    }

                    updatedFieldsGroupedByType.entrySet().stream()
                            .filter(fieldTypeListEntry -> !oldFieldsGroupedByType.containsKey(fieldTypeListEntry.getKey()))
                            .map(Map.Entry::getValue)
                            .forEach(fieldNotFound -> fieldNotFound.forEach(
                                    datasetField -> {
                                        datasetField.setDatasetVersion(dsv);
                                        dsv.getDatasetFields().add(datasetField);
                                    }));
                    dsv.getDatasetFields().addAll(fieldsToAdd);
                }
            }
            boolean updateDraft = ds.getLatestVersion().isDraft();
            DatasetVersion managedVersion = updateDraft
                    ? execCommand(new UpdateDatasetVersionCommand(ds, req)).getEditVersion()
                    : execCommand(new CreateDatasetVersionCommand(req, ds, dsv));
            DatasetVersionDTO dto = new DatasetVersionDTO.Converter().convert(managedVersion);
            return ok(settingsService.isTrueForKey(SettingsServiceBean.Key.ExcludeEmailFromExport)
                    ? dto.clearEmailFields() : dto);
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
                error.append("Empty multiple value for field: ")
                        .append(dsf.getDatasetFieldType().getDisplayName())
                        .append(" ");
            } else if (!dsf.getDatasetFieldType().isAllowMultiples() && dsf.getDatasetFieldsChildren().isEmpty()) {
                error.append("Empty value for field: ")
                        .append(dsf.getDatasetFieldType().getDisplayName())
                        .append(" ");
            }
        }
        return !error.toString().isEmpty() ? error.toString() : "";
    }

    private DatasetVersion getDatasetVersionOrDie(final DataverseRequest req, String versionNumber, final Dataset ds) throws WrappedResponse {
        DatasetVersion dsv = execCommand(chooseCommandForVersionFinding(versionNumber, ds, req));
        if (dsv == null || dsv.getId() == null) {
            throw new WrappedResponse(notFound(String.format("Dataset version %s of dataset %d not found", versionNumber, ds.getId())));
        }
        return dsv;
    }

    private Command<DatasetVersion> chooseCommandForVersionFinding(String versionId, Dataset ds, DataverseRequest req)
            throws WrappedResponse {
        switch (versionId) {
            case ":latest":
                return new GetLatestAccessibleDatasetVersionCommand(req, ds);
            case ":draft":
                return new GetDraftVersionIfExists(req, ds);
            case ":latest-published":
                return new GetLatestPublishedDatasetVersionCommand(req, ds);
            default:
                try {
                    String[] versions = versionId.split("\\.");
                    if (versions.length == 1) {
                        return new GetSpecificPublishedDatasetVersionCommand(req, ds, Long.parseLong(versions[0]), 0L);
                    } else if (versions.length == 2) {
                        return new GetSpecificPublishedDatasetVersionCommand(req, ds, Long.parseLong(versions[0]), Long.parseLong(versions[1]));
                    }
                    throw new WrappedResponse(error(Response.Status.BAD_REQUEST, "Illegal version identifier '" + versionId + "'"));
                } catch (NumberFormatException nfe) {
                    throw new WrappedResponse(error(Response.Status.BAD_REQUEST, "Illegal version identifier '" + versionId + "'"));
                }
        }
    }

    private boolean isOriginalFormatRequested(MultivaluedMap<String, String> queryParameters) {
        return queryParameters
                .keySet().stream()
                .filter("format"::equals)
                .map(queryParameters::getFirst)
                .anyMatch("original"::equals);
    }

    private RoleAssignee findAssignee(String identifier) {
        try {
            return roleAssigneeSvc.getRoleAssignee(identifier);
        } catch (EJBException ex) {
            Throwable cause = ex;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            logger.log(Level.INFO, "Exception caught looking up RoleAssignee based on identifier ''{0}'': {1}",
                    new Object[] {identifier, cause.getMessage()});
            return null;
        }
    }
}
