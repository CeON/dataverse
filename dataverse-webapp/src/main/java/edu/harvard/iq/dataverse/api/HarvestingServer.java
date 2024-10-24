/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.harvard.iq.dataverse.api;

import edu.harvard.iq.dataverse.api.annotations.ApiWriteOperation;
import edu.harvard.iq.dataverse.common.BundleUtil;
import edu.harvard.iq.dataverse.harvest.server.OAISetServiceBean;
import edu.harvard.iq.dataverse.persistence.harvest.OAISet;
import edu.harvard.iq.dataverse.util.json.JsonParseException;
import org.apache.commons.lang.StringUtils;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static edu.harvard.iq.dataverse.common.NullSafeJsonBuilder.jsonObjectBuilder;

/**
 * @author Leonid Andreev
 */
@Stateless
@Path("harvest/server/oaisets")
public class HarvestingServer extends AbstractApiBean {

    @EJB
    private OAISetServiceBean oaiSetService;

    private static final Logger logger = Logger.getLogger(HarvestingServer.class.getName());

    @GET
    @Path("/")
    public Response oaiSets(@QueryParam("key") String apiKey) throws WrappedResponse {

        findSuperuserOrDie();

        List<OAISet> oaiSets = oaiSetService.findAll();

        JsonArrayBuilder hcArr = Json.createArrayBuilder();

        for (OAISet set : oaiSets) {
            hcArr.add(oaiSetAsJson(set));
        }

        return ok(jsonObjectBuilder().add("oaisets", hcArr));
    }

    @GET
    @Path("{specname}")
    public Response oaiSet(@PathParam("specname") String spec, @QueryParam("key") String apiKey) throws IOException, WrappedResponse {

        findSuperuserOrDie();

        OAISet set;
        try {
            set = oaiSetService.findBySpec(spec);
        } catch (Exception ex) {
            logger.warning("Exception caught looking up OAI set " + spec + ": " + ex.getMessage());
            return error(Response.Status.BAD_REQUEST, "Internal error: failed to look up OAI set " + spec + ".");
        }

        if (set == null) {
            return error(Response.Status.NOT_FOUND, "OAI set " + spec + " not found.");
        }

        try {
            return ok(oaiSetAsJson(set));
        } catch (Exception ex) {
            logger.warning("Unknown exception caught while trying to format OAI set " + spec + " as json: " + ex.getMessage());
            return error(Response.Status.BAD_REQUEST,
                         "Internal error: failed to produce output for OAI set " + spec + ".");
        }
    }

    /**
     * create an OAI set from spec in path and other parameters from POST body
     * (as JSON). {"name":$set_name,
     * "description":$optional_set_description,"definition":$set_search_query_string}.
     */
    @POST
    @ApiWriteOperation
    @Path("{specname}")
    public Response createOaiSet(String jsonBody, @PathParam("specname") String spec, @QueryParam("key") String apiKey) throws WrappedResponse, JsonParseException {

        findSuperuserOrDie();

        StringReader rdr = new StringReader(jsonBody);

        try (JsonReader jrdr = Json.createReader(rdr)) {
            JsonObject json = jrdr.readObject();

            OAISet set = new OAISet();
            //Validating spec
            if (!StringUtils.isEmpty(spec)) {
                if (spec.length() > 30) {
                    return badRequest(BundleUtil.getStringFromBundle("harvestserver.newSetDialog.setspec.sizelimit"));
                }
                if (!Pattern.matches("^[a-zA-Z0-9\\_\\-]+$", spec)) {
                    return badRequest(BundleUtil.getStringFromBundle("harvestserver.newSetDialog.setspec.invalid"));
                    // If it passes the regex test, check
                }
                if (oaiSetService.findBySpec(spec) != null) {
                    return badRequest(BundleUtil.getStringFromBundle("harvestserver.newSetDialog.setspec.alreadyused"));
                }

            } else {
                return badRequest(BundleUtil.getStringFromBundle("harvestserver.newSetDialog.setspec.required"));
            }
            set.setSpec(spec);
            String name, desc, defn;

            try {
                name = json.getString("name");
            } catch (NullPointerException npe_name) {
                return badRequest(BundleUtil.getStringFromBundle("harvestserver.newSetDialog.setspec.required"));
            }
            try {
                defn = json.getString("definition");
            } catch (NullPointerException npe_defn) {
                throw new JsonParseException("definition unspecified");
            }
            try {
                desc = json.getString("description");
            } catch (NullPointerException npe_desc) {
                desc = ""; //treating description as optional
            }
            set.setName(name);
            set.setDescription(desc);
            set.setDefinition(defn);
            oaiSetService.save(set);
            return created("/harvest/server/oaisets" + spec, oaiSetAsJson(set));
        }

    }

    @DELETE
    @ApiWriteOperation
    @Path("{specname}")
    public Response deleteOaiSet(@PathParam("specname") String spec, @QueryParam("key") String apiKey) throws WrappedResponse {

        findSuperuserOrDie();

        OAISet set = null;
        try {
            set = oaiSetService.findBySpec(spec);
        } catch (Exception ex) {
            logger.warning("Exception caught looking up OAI set " + spec + ": " + ex.getMessage());
            return error(Response.Status.BAD_REQUEST, "Internal error: failed to look up OAI set " + spec + ".");
        }

        if (set == null) {
            return error(Response.Status.NOT_FOUND, "OAI set " + spec + " not found.");
        }

        try {
            oaiSetService.setDeleteInProgress(set.getId());
            oaiSetService.remove(set.getId());
        } catch (Exception ex) {
            return error(Response.Status.BAD_REQUEST, "Internal error: failed to delete OAI set " + spec + "; " + ex.getMessage());
        }

        return ok("OAI Set " + spec + " deleted");

    }

    public static JsonObjectBuilder oaiSetAsJson(OAISet set) {
        if (set == null) {
            return null;
        }

        return jsonObjectBuilder().add("name", set.getName()).
                add("spec", set.getSpec()).
                add("description", set.getDescription()).
                add("definition", set.getDefinition()).
                add("version", set.getVersion());
    }

}
