package edu.harvard.iq.dataverse.dashboard;

import edu.harvard.iq.dataverse.DataverseRequestServiceBean;
import edu.harvard.iq.dataverse.EjbDataverseEngine;
import edu.harvard.iq.dataverse.engine.command.impl.GrantSuperuserStatusCommand;
import edu.harvard.iq.dataverse.engine.command.impl.RevokeAllRolesCommand;
import edu.harvard.iq.dataverse.engine.command.impl.RevokeSuperuserStatusCommand;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;

import javax.ejb.Stateless;
import javax.inject.Inject;

import static edu.harvard.iq.dataverse.GlobalIdServiceBean.logger;

@Stateless
public class DashboardUsersService {

    private EjbDataverseEngine commandEngine;
    private DataverseRequestServiceBean dvRequestService;

    // -------------------- CONSTRUCTORS --------------------

    @Deprecated
    public DashboardUsersService() {
    }

    @Inject
    public DashboardUsersService(EjbDataverseEngine commandEngine, DataverseRequestServiceBean dvRequestService) {
        this.commandEngine = commandEngine;
        this.dvRequestService = dvRequestService;
    }

    // -------------------- LOGIC --------------------

    public AuthenticatedUser changeSuperuserStatus(AuthenticatedUser user) {
        if (user != null) {
            logger.fine("Toggling user's " + user.getIdentifier() + " superuser status; (current status: " + user.isSuperuser() + ")");
            logger.fine("Attempting to save user " + user.getIdentifier());

            logger.fine("selectedUserPersistent info: " + user.getId() + " set to: " + user.isSuperuser());
            user.setSuperuser(user.isSuperuser());

            // Using the new commands for granting and revoking the superuser status:
            try {
                if (!user.isSuperuser()) {
                    // We are revoking the status:
                    return commandEngine.submit(new RevokeSuperuserStatusCommand(user, dvRequestService.getDataverseRequest()));
                } else {
                    // granting the status:
                    return commandEngine.submit(new GrantSuperuserStatusCommand(user, dvRequestService.getDataverseRequest()));
                }
            } catch (Exception ex) {
                logger.warning("Failed to permanently toggle the superuser status for user " + user.getIdentifier() + ": " + ex.getMessage());
            }
        } else {
            logger.warning("selectedUserPersistent is null.  AuthenticatedUser not found for id: " + user.getId());
        }
        return user;
    }

    public AuthenticatedUser revokeAllRolesForUser(AuthenticatedUser user) {
        user.setRoles(null);
        return commandEngine.submit(new RevokeAllRolesCommand(user, dvRequestService.getDataverseRequest()));
    }
}
