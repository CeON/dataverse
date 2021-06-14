package edu.harvard.iq.dataverse.api.ror;

import edu.harvard.iq.dataverse.api.errorhandlers.ApiErrorResponse;
import edu.harvard.iq.dataverse.ror.RorDataService;
import edu.harvard.iq.dataverse.util.FileUtil;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

@Stateless
@Path("ror")
public class RorDataApi {

    private static final Logger logger = LoggerFactory.getLogger(RorDataApi.class);

    private RorDataService rorDataService;

    // -------------------- CONSTRUCTORS --------------------

    public RorDataApi() { }

    @Inject
    public RorDataApi(RorDataService rorDataService) {
        this.rorDataService = rorDataService;
    }

    // -------------------- LOGIC --------------------

    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadRorData(
            @FormDataParam("file") InputStream inputStream,
            @FormDataParam("file") FormDataContentDisposition contentDispositionHeader) {
        File file;
        RorDataService.UpdateResult result;
        try {
            file = FileUtil.inputStreamToFile(inputStream, 8192);
            result = rorDataService.refreshRorData(file, contentDispositionHeader);
        } catch (IOException ioe) {
            logger.warn("Exception during file upload: ", ioe);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiErrorResponse.errorResponse(Response.Status.BAD_REQUEST.getStatusCode(),
                            "There was an IO error with file being uploaded"))
                    .build();
        } finally {
            close(inputStream);
        }
        return Response.ok(result).build();
    }

    // -------------------- PRIVATE --------------------

    private void close(InputStream inputStream) {
        if (inputStream == null) {
            return;
        }
        try {
            inputStream.close();
        } catch (IOException ioe) {
            logger.warn("Exception while closing stream: ", ioe);
        }
    }
}
