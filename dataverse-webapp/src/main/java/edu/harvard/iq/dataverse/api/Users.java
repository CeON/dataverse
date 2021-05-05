package edu.harvard.iq.dataverse.api;

import edu.harvard.iq.dataverse.engine.command.impl.ChangeUserIdentifierCommand;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.persistence.user.User;

import javax.ejb.Stateless;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

/**
 *
 * @author madunlap
 */
@Stateless
@Path("users")
public class Users extends AbstractApiBean {

    @POST
    @Path("{identifier}/changeIdentifier/{newIdentifier}")
    public Response changeAuthenticatedUserIdentifier(@PathParam("identifier") String oldIdentifier, @PathParam("newIdentifier")  String newIdentifier) {
        User user;
        try {
            user = findUserOrDie();
            if(!user.isSuperuser()) {
                throw new WrappedResponse(error(Response.Status.UNAUTHORIZED, "Only superusers can change userIdentifiers"));
            }
        } catch (WrappedResponse ex) {
            return ex.getResponse();
        }

        if(oldIdentifier == null || oldIdentifier.isEmpty()) {
            return error(Response.Status.BAD_REQUEST, "Old identifier provided to change is empty.");
        } else if(newIdentifier == null || newIdentifier.isEmpty()) {
            return error(Response.Status.BAD_REQUEST, "New identifier provided to change is empty.");
        } else if(newIdentifier.equals(oldIdentifier)) {
            return error(Response.Status.BAD_REQUEST, "New identifier must differ from the old");
        }

        AuthenticatedUser authenticatedUser = authSvc.getAuthenticatedUser(oldIdentifier);
        if (authenticatedUser == null) {
            return error(Response.Status.BAD_REQUEST, "User " + oldIdentifier + " not found in AuthenticatedUser");
        }

        try {
            execCommand(new ChangeUserIdentifierCommand(createDataverseRequest(user), authenticatedUser,  newIdentifier));
        } catch (Exception e){
            return error(Response.Status.BAD_REQUEST, "Error calling ChangeUserIdentifierCommand: " + e.getLocalizedMessage());
        }

        return ok("UserIdentifier changed from " + oldIdentifier + " to " + newIdentifier);
    }

}
