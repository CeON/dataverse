package edu.harvard.iq.dataverse.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import edu.harvard.iq.dataverse.EjbDataverseEngine;
import edu.harvard.iq.dataverse.common.BundleUtil;
import edu.harvard.iq.dataverse.datasetutility.AddReplaceFileHelper;
import edu.harvard.iq.dataverse.datasetutility.DataFileTagException;
import edu.harvard.iq.dataverse.datasetutility.NoFilesException;
import edu.harvard.iq.dataverse.datasetutility.OptionalFileParams;
import edu.harvard.iq.dataverse.engine.command.DataverseRequest;
import edu.harvard.iq.dataverse.engine.command.exception.CommandException;
import edu.harvard.iq.dataverse.engine.command.impl.DeleteMapLayerMetadataCommand;
import edu.harvard.iq.dataverse.engine.command.impl.UningestFileCommand;
import edu.harvard.iq.dataverse.ingest.IngestServiceBean;
import edu.harvard.iq.dataverse.license.TermsOfUseFactory;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.datafile.ingest.IngestRequest;
import edu.harvard.iq.dataverse.persistence.datafile.license.LicenseDAO;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetLock;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.persistence.user.User;
import edu.harvard.iq.dataverse.util.FileUtil;
import edu.harvard.iq.dataverse.util.StringUtil;
import edu.harvard.iq.dataverse.util.SystemConfig;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

@Path("files")
public class Files extends AbstractApiBean {

    @EJB
    IngestServiceBean ingestService;
    @EJB
    EjbDataverseEngine commandEngine;
    @EJB
    SystemConfig systemConfig;
    @Inject
    private OptionalFileParams optionalFileParams;

    @EJB
    private LicenseDAO licenseDAO;
    @EJB
    private TermsOfUseFactory termsOfUseFactory;

    private static final Logger logger = Logger.getLogger(Files.class.getName());


    private void msg(String m) {
        System.out.println(m);
    }

    private void dashes() {
        msg("----------------");
    }

    private void msgt(String m) {
        dashes();
        msg(m);
        dashes();
    }


    /**
     * Replace an Existing File
     *
     * @param
     * @param testFileInputStream
     * @param contentDispositionHeader
     * @param formDataBodyPart
     * @return
     */
    @POST
    @Path("{id}/replace")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response replaceFileInDataset(
            @PathParam("id") String fileIdOrPersistentId,
            @FormDataParam("jsonData") String jsonData,
            @FormDataParam("file") InputStream testFileInputStream,
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
        } catch (AbstractApiBean.WrappedResponse ex) {
            return error(Response.Status.FORBIDDEN,
                         BundleUtil.getStringFromBundle("file.addreplace.error.auth")
            );
        }

        // -------------------------------------
        // (2) Check/Parse the JSON (if uploaded)
        // -------------------------------------        
        Boolean forceReplace = false;
        OptionalFileParams optionalFileParams = null;
        if (jsonData != null) {
            JsonObject jsonObj = null;
            try {
                jsonObj = new Gson().fromJson(jsonData, JsonObject.class);
                // (2a) Check for optional "forceReplace"
                // -------------------------------------
                if ((jsonObj.has("forceReplace")) && (!jsonObj.get("forceReplace").isJsonNull())) {
                    forceReplace = jsonObj.get("forceReplace").getAsBoolean();
                    if (forceReplace == null) {
                        forceReplace = false;
                    }
                }
                try {
                    // (2b) Load up optional params via JSON
                    //  - Will skip extra attributes which includes fileToReplaceId and forceReplace
                    //---------------------------------------
                    optionalFileParams = new OptionalFileParams().create(jsonData);
                } catch (DataFileTagException ex) {
                    return error(Response.Status.BAD_REQUEST, ex.getMessage());
                }
            } catch (ClassCastException ex) {
                logger.info("Exception parsing string '" + jsonData + "': " + ex);
            }
        }

        // -------------------------------------
        // (3) Get the file name and content type
        // -------------------------------------
        String newFilename = contentDispositionHeader.getFileName();
        String newFileContentType = formDataBodyPart.getMediaType().toString();


        //-------------------
        // (4) Create the AddReplaceFileHelper object
        //-------------------
        msg("REPLACE!");

        DataverseRequest dvRequest2 = createDataverseRequest(authUser);
        AddReplaceFileHelper addFileHelper = new AddReplaceFileHelper(dvRequest2,
                                                                      this.ingestService,
                                                                      this.fileService,
                                                                      this.permissionSvc,
                                                                      this.commandEngine, this.optionalFileParams);

        //-------------------
        // (5) Run "runReplaceFileByDatasetId"
        //-------------------
        long fileToReplaceId = 0;
        try {
            DataFile dataFile = findDataFileOrDie(fileIdOrPersistentId);
            fileToReplaceId = dataFile.getId();

            if (dataFile.isFilePackage()) {
                return error(Response.Status.SERVICE_UNAVAILABLE, BundleUtil.getStringFromBundle("file.api.alreadyHasPackageFile"));
            }
        } catch (WrappedResponse ex) {
            String error = BundleUtil.getStringFromBundle("file.addreplace.error.existing_file_to_replace_not_found_by_id", Arrays.asList(fileIdOrPersistentId));
            // TODO: Some day, return ex.getResponse() instead. Also run FilesIT and updated expected status code and message.
            return error(BAD_REQUEST, error);
        }
        if (forceReplace) {
            addFileHelper.runForceReplaceFile(fileToReplaceId,
                                              newFilename,
                                              newFileContentType,
                                              testFileInputStream,
                                              optionalFileParams);
        } else {
            addFileHelper.runReplaceFile(fileToReplaceId,
                                         newFilename,
                                         newFileContentType,
                                         testFileInputStream,
                                         optionalFileParams);
        }

        msg("we're back.....");
        if (addFileHelper.hasError()) {
            msg("yes, has error");
            return error(addFileHelper.getHttpErrorCode(), addFileHelper.getErrorMessagesAsString("\n"));

        } else {
            msg("no error");
            String successMsg = BundleUtil.getStringFromBundle("file.addreplace.success.replace");

            try {
                msgt("as String: " + addFileHelper.getSuccessResult());
                /**
                 * @todo We need a consistent, sane way to communicate a human
                 * readable message to an API client suitable for human
                 * consumption. Imagine if the UI were built in Angular or React
                 * and we want to return a message from the API as-is to the
                 * user. Human readable.
                 */
                logger.fine("successMsg: " + successMsg);
                return ok(addFileHelper.getSuccessResultAsJsonObjectBuilder());
                //return okResponseGsonObject(successMsg,
                //        addFileHelper.getSuccessResultAsGsonObject());
                //"Look at that!  You added a file! (hey hey, it may have worked)");
            } catch (NoFilesException ex) {
                Logger.getLogger(Files.class.getName()).log(Level.SEVERE, null, ex);
                return error(Response.Status.BAD_REQUEST, "NoFileException!  Serious Error! See administrator!");

            }
        }

    } // end: replaceFileInDataset

    // TODO: Rather than only supporting looking up files by their database IDs, consider supporting persistent identifiers.
    // TODO: Rename this start with "delete" rather than "get".
    @DELETE
    @Path("{id}/map")
    public Response getMapLayerMetadatas(@PathParam("id") Long idSupplied) {
        DataverseRequest dataverseRequest = null;
        try {
            dataverseRequest = createDataverseRequest(findUserOrDie());
        } catch (WrappedResponse wr) {
            return error(BAD_REQUEST, "Couldn't find user to execute command: " + wr.getLocalizedMessage());
        }
        DataFile dataFile = fileService.find(idSupplied);
        try {
            boolean deleted = engineSvc.submit(new DeleteMapLayerMetadataCommand(dataverseRequest, dataFile));
            if (deleted) {
                return ok("Map deleted from file id " + dataFile.getId());
            } else {
                return error(BAD_REQUEST, "Could not delete map from file id " + dataFile.getId());
            }
        } catch (CommandException ex) {
            return error(BAD_REQUEST, "Problem trying to delete map from file id " + dataFile.getId() + ": " + ex.getLocalizedMessage());
        }
    }

    @Path("{id}/uningest")
    @POST
    public Response uningestDatafile(@PathParam("id") String id) {

        DataFile dataFile;
        try {
            dataFile = findDataFileOrDie(id);
        } catch (WrappedResponse ex) {
            return error(BAD_REQUEST, "Could not find datafile with id " + id);
        }
        if (dataFile == null) {
            return error(Response.Status.NOT_FOUND, "File not found for given id.");
        }

        if (!dataFile.isTabularData()) {
            return error(Response.Status.BAD_REQUEST, "Cannot uningest non-tabular file.");
        }

        try {
            DataverseRequest req = createDataverseRequest(findUserOrDie());
            execCommand(new UningestFileCommand(req, dataFile));
            Long dataFileId = dataFile.getId();
            return ok("Datafile " + dataFileId + " uningested.");
        } catch (WrappedResponse wr) {
            return wr.getResponse();
        }

    }

    // reingest attempts to queue an *existing* DataFile 
    // for tabular ingest. It can be used on non-tabular datafiles; to try to 
    // ingest a file that has previously failed ingest, or to ingest a file of a
    // type for which ingest was not previously supported. 
    // We are considering making it possible, in the future, to reingest 
    // a datafile that's already ingested as Tabular; for example, to address a 
    // bug that has been found in an ingest plugin. 

    @Path("{id}/reingest")
    @POST
    public Response reingest(@PathParam("id") String id) {

        AuthenticatedUser u;
        try {
            u = findAuthenticatedUserOrDie();
            if (!u.isSuperuser()) {
                return error(Response.Status.FORBIDDEN, "This API call can be used by superusers only");
            }
        } catch (WrappedResponse wr) {
            return wr.getResponse();
        }

        DataFile dataFile;
        try {
            dataFile = findDataFileOrDie(id);
        } catch (WrappedResponse ex) {
            return error(Response.Status.NOT_FOUND, "File not found for given id.");
        }

        Dataset dataset = dataFile.getOwner();

        if (dataset == null) {
            return error(Response.Status.BAD_REQUEST, "Failed to locate the parent dataset for the datafile.");
        }

        if (dataFile.isTabularData()) {
            return error(Response.Status.BAD_REQUEST, "The datafile is already ingested as Tabular.");
        }

        boolean ingestLock = dataset.isLockedFor(DatasetLock.Reason.Ingest);

        if (ingestLock) {
            return error(Response.Status.FORBIDDEN, "Dataset already locked with an Ingest lock");
        }

        if (!FileUtil.canIngestAsTabular(dataFile)) {
            return error(Response.Status.BAD_REQUEST, "Tabular ingest is not supported for this file type (id: " + id + ", type: " + dataFile.getContentType() + ")");
        }

        dataFile.SetIngestScheduled();

        if (dataFile.getIngestRequest() == null) {
            dataFile.setIngestRequest(new IngestRequest(dataFile));
        }

        dataFile.getIngestRequest().setForceTypeCheck(true);

        // update the datafile, to save the newIngest request in the database:
        dataFile = fileService.save(dataFile);

        // queue the data ingest job for asynchronous execution: 
        String status = ingestService.startIngestJobs(new ArrayList<>(Arrays.asList(dataFile)), u);

        if (!StringUtil.isEmpty(status)) {
            // This most likely indicates some sort of a problem (for example, 
            // the ingest job was not put on the JMS queue because of the size
            // of the file). But we are still returning the OK status - because
            // from the point of view of the API, it's a success - we have 
            // successfully gone through the process of trying to schedule the 
            // ingest job...

            return ok(status);
        }
        return ok("Datafile " + id + " queued for ingest");

    }

}
