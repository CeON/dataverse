package edu.harvard.iq.dataverse.dashboard;

import com.rometools.utils.Lists;
import edu.harvard.iq.dataverse.DataverseSession;
import edu.harvard.iq.dataverse.RoleAssigneeServiceBean;
import edu.harvard.iq.dataverse.UserServiceBean;
import edu.harvard.iq.dataverse.arquillian.arquillianexamples.WebappArquillianDeployment;
import edu.harvard.iq.dataverse.authorization.AuthenticationServiceBean;
import edu.harvard.iq.dataverse.engine.command.exception.PermissionException;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.transaction.api.annotation.TransactionMode;
import org.jboss.arquillian.transaction.api.annotation.Transactional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.platform.commons.util.StringUtils;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import javax.ejb.EJB;
import javax.inject.Inject;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(Arquillian.class)
@Transactional(TransactionMode.ROLLBACK)
public class DashboardUsersServiceIT extends WebappArquillianDeployment {

    @Inject
    private DataverseSession dataverseSession;
    @EJB
    private AuthenticationServiceBean authenticationServiceBean;
    @Inject
    private DashboardUsersService dashboardUsersService;
    @Inject
    private RoleAssigneeServiceBean roleAssigneeService;
    @Inject
    private UserServiceBean userServiceBean;

    @Before
    public void setUp() {
        dataverseSession.setUser(authenticationServiceBean.getAdminUser());
    }

    @Test
    public void shouldRevokeAllRolesForUser() {
        // given
        AuthenticatedUser userWithRoles = userServiceBean.getAuthenticatedUserList("","",100, 0)
                .stream()
                .filter(u -> u.getId().equals(2L))
                .findFirst()
                .get();

        // when
        dashboardUsersService.revokeAllRolesForUser(userWithRoles);

        // then
        AuthenticatedUser dbUserWithoutRoles = userServiceBean.getAuthenticatedUserList("","",100, 0)
                .stream()
                .filter(u -> u.getId().equals(2L))
                .findFirst()
                .get();

        assertTrue(StringUtils.isBlank(dbUserWithoutRoles.getRoles()));
        assertNull(roleAssigneeService.getUserExplicitGroups(dbUserWithoutRoles)
                .stream()
                .filter(s -> "1-rootgroup".equals(s))
                .findAny()
                .orElse(null));
        assertTrue(Lists.isEmpty(roleAssigneeService.getAssignmentsFor(dbUserWithoutRoles.getUserIdentifier())));
    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void shouldRevokeAllRolesForUser_withPermissionsException() {
        // given
        AuthenticatedUser user = authenticationServiceBean.findByID(2L);
        dataverseSession.setUser(authenticationServiceBean.findByID(4L));

        // when & then
        thrown.expect(PermissionException.class);
        dashboardUsersService.revokeAllRolesForUser(user);
    }

    @Test
    public void shouldChangeSuperuserStatus_revokeSuperuserStatus() {
        // given
        AuthenticatedUser user = authenticationServiceBean.findByID(2L);

        // when
        AuthenticatedUser dbUser =  dashboardUsersService.changeSuperuserStatus(user);

        // then
        assertNotNull(dbUser);
        assertFalse(dbUser.isSuperuser());
    }

    @Test
    public void shouldChangeSuperuserStatus_grantSuperuserStatus() {
        // given
        AuthenticatedUser user = authenticationServiceBean.findByID(3L);

        // when
        AuthenticatedUser dbUser =  dashboardUsersService.changeSuperuserStatus(user);

        // then
        assertNotNull(dbUser);
        assertTrue(dbUser.isSuperuser());
    }

    @Test
    public void shouldChangeSuperuserStatus_revokeSuperuserStatus_withPermissionException() {
        // given
        AuthenticatedUser user = authenticationServiceBean.findByID(2L);
        dataverseSession.setUser(authenticationServiceBean.findByID(4L));

        // when & then
        thrown.expect(PermissionException.class);
        dashboardUsersService.changeSuperuserStatus(user);
    }

    @Test
    public void shouldChangeSuperuserStatus_grantSuperuserStatus_withPermissionException() {
        // given
        AuthenticatedUser user = authenticationServiceBean.findByID(3L);
        dataverseSession.setUser(authenticationServiceBean.findByID(4L));

        // when & then
        thrown.expect(PermissionException.class);
        dashboardUsersService.changeSuperuserStatus(user);
    }
}
