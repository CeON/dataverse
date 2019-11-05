package edu.harvard.iq.dataverse.managegroups;

import edu.harvard.iq.dataverse.DataverseServiceBean;
import edu.harvard.iq.dataverse.DataverseSession;
import edu.harvard.iq.dataverse.ManageGroupsCRUDService;
import edu.harvard.iq.dataverse.arquillian.arquillianexamples.WebappArquillianDeployment;
import edu.harvard.iq.dataverse.authorization.AuthenticationServiceBean;
import edu.harvard.iq.dataverse.authorization.groups.impl.explicit.ExplicitGroupServiceBean;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.group.ExplicitGroup;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.persistence.user.RoleAssignee;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.transaction.api.annotation.TransactionMode;
import org.jboss.arquillian.transaction.api.annotation.Transactional;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

@Transactional(TransactionMode.ROLLBACK)
@RunWith(Arquillian.class)
public class ManageGroupsCRUDServiceIT extends WebappArquillianDeployment {

    @PersistenceContext(unitName = "VDCNet-ejbPU")
    private EntityManager em;

    @EJB
    private ManageGroupsCRUDService manageGroupsCRUDService;
    @EJB
    private DataverseServiceBean dataverseService;
    @Inject
    private DataverseSession dataverseSession;
    @EJB
    private AuthenticationServiceBean authenticationService;
    @EJB
    private ExplicitGroupServiceBean explicitGroupService;

    @Before
    public void setUp() {
        dataverseSession.setUser(authenticationService.getAdminUser());
    }

    @Test
    public void shouldCreateGroup() {
        // given
        Dataverse dv = dataverseService.findByAlias("ownmetadatablocks");
        RoleAssignee roleAssignee = authenticationService.findAllAuthenticatedUsers().get(0);

        // when
        manageGroupsCRUDService.create(dv, "testGroup", "testGroupId", "testDesc", Collections.singletonList(roleAssignee));

        // then
        ExplicitGroup dbExplicitGroup = explicitGroupService.findByAlias(dv.getId() + "-testGroupId");

        Assert.assertEquals(1, explicitGroupService.findByOwner(dv.getId()).size());
        Assert.assertEquals("testGroup", dbExplicitGroup.getDisplayName());
        Assert.assertEquals(roleAssignee, dbExplicitGroup.getContainedAuthenticatedUsers().iterator().next());
    }

    @Test
    public void shouldUpdateGroup() {
        // given
        Dataverse dv = dataverseService.findByAlias("ownmetadatablocks");
        ExplicitGroup explicitGroup = buildExplicitGroup(dv);

        em.persist(explicitGroup);

        // when
        explicitGroup.setDisplayName("updatedName");

        AuthenticatedUser newRoleAssignee = authenticationService.findAllAuthenticatedUsers().get(1);
        Set<AuthenticatedUser> newRoleAssigneesSet = explicitGroup.getContainedAuthenticatedUsers();
        newRoleAssigneesSet.add(newRoleAssignee);
        List<RoleAssignee> newRoleAssigneesList = new LinkedList<>(newRoleAssigneesSet);

        manageGroupsCRUDService.update(explicitGroup, newRoleAssigneesList);

        // then
        ExplicitGroup dbExplicitGroup = explicitGroupService.findByAlias(dv.getId() + "-explicitGroupIdentifier");
        Assert.assertEquals(1, explicitGroupService.findByOwner(dv.getId()).size());
        Assert.assertEquals(explicitGroup.getId(), dbExplicitGroup.getId());
        Assert.assertEquals("updatedName", dbExplicitGroup.getDisplayName());
        Assert.assertNotEquals("explicitGroupName", dbExplicitGroup.getDisplayName());
        Assert.assertEquals(2, dbExplicitGroup.getContainedAuthenticatedUsers().size());
        Assert.assertEquals(newRoleAssignee.getIdentifier(),
                dbExplicitGroup.getContainedAuthenticatedUsers()
                        .stream()
                        .filter(au -> au.getIdentifier().equals(newRoleAssignee.getIdentifier()))
                        .findAny().get().getIdentifier()
        );
    }

    @Test
    public void shouldDeleteGroup() {
        // given
        Dataverse dv = dataverseService.findByAlias("ownmetadatablocks");
        ExplicitGroup explicitGroup = buildExplicitGroup(dv);

        em.persist(explicitGroup);

        // when
        manageGroupsCRUDService.delete(explicitGroup);

        // then
        Assert.assertEquals(0, explicitGroupService.findByOwner(dv.getId()).size());
        Assert.assertNull(explicitGroupService.findByAlias(dv.getId() + "-explicitGroupIdentifier"));

        List<ExplicitGroup> dbExplicitGroupList = explicitGroupService.findByOwner(dv.getId());
        Assert.assertFalse(dbExplicitGroupList.stream().anyMatch(eg -> eg.getId().equals(explicitGroup.getId())));
        Assert.assertFalse(dbExplicitGroupList.stream().anyMatch(eg -> eg.getDisplayName().equals("explicitGroupName")));
    }

    // -------------------- PRIVATE ---------------------
    private ExplicitGroup buildExplicitGroup(Dataverse groupOwner) {
        ExplicitGroup explicitGroup = explicitGroupService.getProvider().makeGroup();
        explicitGroup.setDisplayName("explicitGroupName");
        explicitGroup.setGroupAliasInOwner("explicitGroupIdentifier");
        explicitGroup.setDescription("explicitGroupDescription");
        explicitGroup.setOwner(groupOwner);

        RoleAssignee roleAssignee = authenticationService.findAllAuthenticatedUsers().get(0);
        explicitGroup.add(roleAssignee);

        return explicitGroup;
    }
}
