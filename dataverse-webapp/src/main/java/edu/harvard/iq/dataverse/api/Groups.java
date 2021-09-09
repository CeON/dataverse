package edu.harvard.iq.dataverse.api;

import edu.harvard.iq.dataverse.api.annotations.ApiWriteOperation;
import edu.harvard.iq.dataverse.authorization.groups.GroupServiceBean;
import edu.harvard.iq.dataverse.authorization.groups.impl.ipaddress.IpGroupProvider;
import edu.harvard.iq.dataverse.authorization.groups.impl.shib.ShibGroupProvider;
import edu.harvard.iq.dataverse.persistence.group.IpGroup;
import edu.harvard.iq.dataverse.persistence.group.ShibGroup;
import edu.harvard.iq.dataverse.util.json.JsonParser;
import edu.harvard.iq.dataverse.util.json.JsonPrinter;

import javax.annotation.PostConstruct;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static org.apache.commons.lang.StringUtils.isNumeric;

/**
 * @author michael
 */
@Path("admin/groups")
@Stateless
public class Groups extends AbstractApiBean {
    private static final Logger logger = Logger.getLogger(Groups.class.getName());

    private IpGroupProvider ipGroupPrv;
    private ShibGroupProvider shibGroupPrv;

    @Inject
    private JsonPrinter jsonPrinter;

    @Inject
    private GroupServiceBean groupSvc;

    Pattern legalGroupName = Pattern.compile("^[-_a-zA-Z0-9]+$");

    @PostConstruct
    void postConstruct() {
        ipGroupPrv = groupSvc.getIpGroupProvider();
        shibGroupPrv = groupSvc.getShibGroupProvider();
    }

    /**
     * Creates a new {@link IpGroup}. The name of the group is based on the
     * {@code alias:} field, but might be changed to ensure uniqueness.
     *
     * @param dto
     * @return Response describing the created group or the error that prevented
     * that group from being created.
     */
    @POST
    @ApiWriteOperation
    @Path("ip")
    public Response postIpGroup(JsonObject dto) {
        try {
            IpGroup grp = new JsonParser().parseIpGroup(dto);
            grp.setPersistedGroupAlias(
                    ipGroupPrv.findAvailableName(
                            grp.getPersistedGroupAlias() == null ? "ipGroup" : grp.getPersistedGroupAlias()));

            grp = ipGroupPrv.store(grp);
            return created("/groups/ip/" + grp.getPersistedGroupAlias(), jsonPrinter.json(grp));

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error while storing a new IP group: " + e.getMessage(), e);
            return error(Response.Status.INTERNAL_SERVER_ERROR, "Error: " + e.getMessage());

        }
    }

    /**
     * Creates or updates the {@link IpGroup} named {@code groupName}.
     *
     * @param groupName Name of the group.
     * @param dto       data of the group.
     * @return Response describing the created group or the error that prevented
     * that group from being created.
     */
    @PUT
    @ApiWriteOperation
    @Path("ip/{groupName}")
    public Response putIpGroups(@PathParam("groupName") String groupName, JsonObject dto) {
        try {
            if (groupName == null || groupName.trim().isEmpty()) {
                return badRequest("Group name cannot be empty");
            }
            if (!legalGroupName.matcher(groupName).matches()) {
                return badRequest("Group name can contain only letters, digits, and the chars '-' and '_'");
            }
            IpGroup grp = new JsonParser().parseIpGroup(dto);
            grp.setPersistedGroupAlias(groupName);
            grp = ipGroupPrv.store(grp);
            return created("/groups/ip/" + grp.getPersistedGroupAlias(), jsonPrinter.json(grp));

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error while storing a new IP group: " + e.getMessage(), e);
            return error(Response.Status.INTERNAL_SERVER_ERROR, "Error: " + e.getMessage());

        }
    }

    @GET
    @Path("ip")
    public Response listIpGroups() {
        return ok(ipGroupPrv.findGlobalGroups().stream()
                .map(g -> jsonPrinter.json(g))
                .collect(jsonPrinter.toJsonArray()));
    }

    @GET
    @Path("ip/{groupIdtf}")
    public Response getIpGroup(@PathParam("groupIdtf") String groupIdtf) {
        IpGroup grp;
        if (isNumeric(groupIdtf)) {
            grp = ipGroupPrv.get(Long.parseLong(groupIdtf));
        } else {
            grp = ipGroupPrv.get(groupIdtf);
        }

        return (grp == null)
                ? notFound("Group " + groupIdtf + " not found")
                : ok(jsonPrinter.json(grp));
    }

    @DELETE
    @ApiWriteOperation
    @Path("ip/{groupIdtf}")
    public Response deleteIpGroup(@PathParam("groupIdtf") String groupIdtf) {
        IpGroup grp;
        if (isNumeric(groupIdtf)) {
            grp = ipGroupPrv.get(Long.parseLong(groupIdtf));
        } else {
            grp = ipGroupPrv.get(groupIdtf);
        }

        if (grp == null) {
            return notFound("Group " + groupIdtf + " not found");
        }

        try {
            ipGroupPrv.deleteGroup(grp);
            return ok("Group " + grp.getAlias() + " deleted.");
        } catch (Exception topExp) {
            // get to the cause (unwraps EJB exception wrappers).
            Throwable e = topExp;
            while (e.getCause() != null) {
                e = e.getCause();
            }

            if (e instanceof IllegalArgumentException) {
                return error(Response.Status.BAD_REQUEST, e.getMessage());
            } else {
                throw topExp;
            }
        }
    }

    @GET
    @Path("shib")
    public Response listShibGroups() {
        JsonArrayBuilder arrBld = Json.createArrayBuilder();
        for (ShibGroup g : shibGroupPrv.findGlobalGroups()) {
            arrBld.add(jsonPrinter.json(g));
        }
        return ok(arrBld);
    }

    @POST
    @ApiWriteOperation
    @Path("shib")
    public Response createShibGroup(JsonObject shibGroupInput) {
        String expectedNameKey = "name";
        JsonString name = shibGroupInput.getJsonString(expectedNameKey);
        if (name == null) {
            return error(Response.Status.BAD_REQUEST, "required field missing: " + expectedNameKey);
        }
        String expectedAttributeKey = "attribute";
        JsonString attribute = shibGroupInput.getJsonString(expectedAttributeKey);
        if (attribute == null) {
            return error(Response.Status.BAD_REQUEST, "required field missing: " + expectedAttributeKey);
        }
        String expectedPatternKey = "pattern";
        JsonString pattern = shibGroupInput.getJsonString(expectedPatternKey);
        if (pattern == null) {
            return error(Response.Status.BAD_REQUEST, "required field missing: " + expectedPatternKey);
        }
        ShibGroup shibGroupToPersist = new ShibGroup(name.getString(), attribute.getString(), pattern.getString());
        ShibGroup persitedShibGroup = shibGroupPrv.persist(shibGroupToPersist);
        if (persitedShibGroup != null) {
            return ok("Shibboleth group persisted: " + persitedShibGroup);
        } else {
            return error(Response.Status.BAD_REQUEST, "Could not persist Shibboleth group");
        }
    }

    @DELETE
    @ApiWriteOperation
    @Path("shib/{primaryKey}")
    public Response deleteShibGroup(@PathParam("primaryKey") String id) {
        ShibGroup doomed = shibGroupPrv.get(id);
        if (doomed != null) {
            boolean deleted;
            try {
                deleted = shibGroupPrv.delete(doomed);
            } catch (Exception ex) {
                return error(Response.Status.BAD_REQUEST, ex.getMessage());
            }
            if (deleted) {
                return ok("Shibboleth group " + id + " deleted");
            } else {
                return error(Response.Status.BAD_REQUEST, "Could not delete Shibboleth group with an id of " + id);
            }
        } else {
            return error(Response.Status.BAD_REQUEST, "Could not find Shibboleth group with an id of " + id);
        }
    }

}
