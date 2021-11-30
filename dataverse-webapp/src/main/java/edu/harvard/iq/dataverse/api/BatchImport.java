package edu.harvard.iq.dataverse.api;

import edu.harvard.iq.dataverse.api.annotations.ApiWriteOperation;
import edu.harvard.iq.dataverse.api.imports.ImportException;
import edu.harvard.iq.dataverse.api.imports.ImportServiceBean;
import edu.harvard.iq.dataverse.api.imports.ImportUtil.ImportType;
import edu.harvard.iq.dataverse.engine.command.DataverseRequest;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.json.JsonObjectBuilder;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.PrintWriter;

import static org.apache.commons.lang.StringUtils.isNumeric;

@Stateless
@Path("batch")
public class BatchImport extends AbstractApiBean {

    @EJB
    ImportServiceBean importService;

    /**
     * Import a new Dataset with DDI xml data posted in the request
     *
     * @param body       the xml
     * @param parentIdtf the dataverse to import into (id or alias)
     * @param apiKey     user's api key
     * @return import status (including id of the dataset created)
     */
    @POST
    @ApiWriteOperation
    @Path("import")
    public Response postImport(String body, @QueryParam("dv") String parentIdtf, @QueryParam("key") String apiKey) {

        DataverseRequest dataverseRequest;
        try {
            dataverseRequest = createDataverseRequest(findAuthenticatedUserOrDie());
        } catch (WrappedResponse wr) {
            return wr.getResponse();
        }

        if (parentIdtf == null) {
            parentIdtf = "root";
        }
        Dataverse owner = findDataverse(parentIdtf);
        if (owner == null) {
            return error(Response.Status.NOT_FOUND, "Can't find dataverse with identifier='" + parentIdtf + "'");
        }
        try {
            PrintWriter cleanupLog = null; // Cleanup log isn't needed for ImportType == NEW. We don't do any data cleanup in this mode.
            String filename = null;  // Since this is a single input from a POST, there is no file that we are reading from.
            JsonObjectBuilder status = importService.doImport(dataverseRequest, owner, body, filename, ImportType.NEW, cleanupLog);
            return this.ok(status);
        } catch (ImportException | IOException e) {
            return error(Response.Status.BAD_REQUEST, e.getMessage());
        }
    }

    private Dataverse findDataverse(String idtf) {
        return isNumeric(idtf) ? dataverseSvc.find(Long.parseLong(idtf))
                : dataverseSvc.findByAlias(idtf);
    }
}
