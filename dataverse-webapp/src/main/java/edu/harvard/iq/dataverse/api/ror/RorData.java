package edu.harvard.iq.dataverse.api.ror;

import edu.harvard.iq.dataverse.ror.RorDataService;
import edu.harvard.iq.dataverse.util.FileUtil;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;

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
public class RorData {

    private RorDataService rorDataService;

    // -------------------- CONSTRUCTORS --------------------

    public RorData() { }

    @Inject
    public RorData(RorDataService rorDataService) {
        this.rorDataService = rorDataService;
    }

    // -------------------- LOGIC --------------------

    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadRorData(
            @FormDataParam("file") InputStream fileInputStream,
            @FormDataParam("file") FormDataContentDisposition contentDispositionHeader) {
        File file = null;
        try {
            file = FileUtil.inputStreamToFile(fileInputStream, 8192);
            rorDataService.refreshRorData(file, contentDispositionHeader);
        } catch (IOException e) {
            // TODO
        } finally {
            if (fileInputStream != null) {
                try {
                    fileInputStream.close();
                } catch (IOException e) {
                    // TODO
                }
            }
        }
        return Response.ok(contentDispositionHeader).build();
    }
}
