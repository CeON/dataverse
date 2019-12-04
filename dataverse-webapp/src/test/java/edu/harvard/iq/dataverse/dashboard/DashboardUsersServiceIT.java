package edu.harvard.iq.dataverse.dashboard;

import edu.harvard.iq.dataverse.DataverseSession;
import edu.harvard.iq.dataverse.RoleAssigneeServiceBean;
import edu.harvard.iq.dataverse.arquillian.arquillianexamples.WebappArquillianDeployment;
import edu.harvard.iq.dataverse.authorization.AuthenticationServiceBean;
import edu.harvard.iq.dataverse.authorization.groups.impl.explicit.ExplicitGroupServiceBean;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.transaction.api.annotation.TransactionMode;
import org.jboss.arquillian.transaction.api.annotation.Transactional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

@RunWith(Arquillian.class)
@Transactional(TransactionMode.ROLLBACK)
public class DashboardUsersServiceIT extends WebappArquillianDeployment {

    @PersistenceContext(unitName = "VDCNet-ejbPU")
    private EntityManager em;

    @Inject
    private DataverseSession dataverseSession;
    @EJB
    private AuthenticationServiceBean authenticationServiceBean;
    @Inject
    private DashboardUsersService dashboardUsersService;
    @Inject
    private ExplicitGroupServiceBean explicitGroupService;
    @Inject
    private RoleAssigneeServiceBean roleAssigneeService;


    @Before
    public void setUp() {
        dataverseSession.setUser(authenticationServiceBean.getAdminUser());
    }

    @Test
    public void shouldRevokeAllRolesForUser() {
        // given
        AuthenticatedUser user = authenticationServiceBean.findByID(2L);

        // when
        dashboardUsersService.revokeAllRolesForUser(user);

        // then
    }
}
