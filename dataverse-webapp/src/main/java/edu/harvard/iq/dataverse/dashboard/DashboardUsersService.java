package edu.harvard.iq.dataverse.dashboard;

import edu.harvard.iq.dataverse.DataverseRequestServiceBean;
import edu.harvard.iq.dataverse.EjbDataverseEngine;
import edu.harvard.iq.dataverse.engine.command.impl.GrantSuperuserStatusCommand;
import edu.harvard.iq.dataverse.engine.command.impl.RevokeAllRolesCommand;
import edu.harvard.iq.dataverse.engine.command.impl.RevokeSuperuserStatusCommand;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import io.vavr.control.Try;

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

    public Try<AuthenticatedUser> changeSuperuserStatus(AuthenticatedUser user) {
        logger.fine("Toggling user's " + user.getIdentifier() + " superuser status; (current status: " + user.isSuperuser() + ")");
        logger.fine("Attempting to save user " + user.getIdentifier());
        logger.fine("selectedUserPersistent info: " + user.getId() + " set to: " + user.isSuperuser());

        if (!user.isSuperuser()) {
            return Try.of(() -> revokeSuperuserStatus(user));
        } else {
            return Try.of(() -> grantSuperuserStatus(user));
        }
    }

    public AuthenticatedUser revokeAllRolesForUser(AuthenticatedUser user) {
        user.setRoles(null);
        return commandEngine.submit(new RevokeAllRolesCommand(user, dvRequestService.getDataverseRequest()));
    }

    // -------------------- PRIVATE ---------------------

    private AuthenticatedUser grantSuperuserStatus(AuthenticatedUser user) {
        return commandEngine.submit(new GrantSuperuserStatusCommand(user, dvRequestService.getDataverseRequest()));
    }

    private AuthenticatedUser revokeSuperuserStatus(AuthenticatedUser user) {
        return commandEngine.submit(new RevokeSuperuserStatusCommand(user, dvRequestService.getDataverseRequest()));
    }
}
