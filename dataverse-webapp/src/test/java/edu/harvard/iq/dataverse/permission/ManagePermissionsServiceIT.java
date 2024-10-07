package edu.harvard.iq.dataverse.permission;

import edu.harvard.iq.dataverse.DataverseDao;
import edu.harvard.iq.dataverse.DataverseRoleServiceBean;
import edu.harvard.iq.dataverse.DataverseSession;
import edu.harvard.iq.dataverse.arquillian.arquillianexamples.WebappArquillianDeployment;
import edu.harvard.iq.dataverse.authorization.AuthenticationServiceBean;
import edu.harvard.iq.dataverse.engine.command.exception.PermissionException;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.user.DataverseRole;
import edu.harvard.iq.dataverse.persistence.user.DataverseRole.BuiltInRole;
import edu.harvard.iq.dataverse.persistence.user.GuestUser;
import edu.harvard.iq.dataverse.persistence.user.RoleAssignment;
import edu.harvard.iq.dataverse.persistence.user.RoleAssignmentRepository;
import org.jboss.arquillian.transaction.api.annotation.TransactionMode;
import org.jboss.arquillian.transaction.api.annotation.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ManagePermissionsServiceIT extends WebappArquillianDeployment {
    @PersistenceContext(unitName = "VDCNet-ejbPU")
    private EntityManager em;

    @Inject
    private ManagePermissionsService managePermissionsService;
    @Inject
    private DataverseDao dataverseDao;
    @Inject
    private DataverseSession dataverseSession;
    @EJB
    private AuthenticationServiceBean authenticationService;
    @Inject
    private DataverseRoleServiceBean roleService;
    @Inject
    private RoleAssignmentRepository roleAssignmentRepository;

    @BeforeEach
    public void setUp() {
        dataverseSession.setUser(authenticationService.getAdminUser());
    }

    @Test
    public void shouldAssignRoleWithNotification() {
        // given
        dataverseSession.setUser(authenticationService.findByID(1L));
        String userEmail = dataverseSession.getUser().getDisplayInfo().getEmailAddress();
        Dataverse dataverse = dataverseDao.find(1L);
        DataverseRole roleToBeAssigned = roleService.findBuiltinRoleByAlias(BuiltInRole.EDITOR);

        // when
        managePermissionsService.assignRoleWithNotification(roleToBeAssigned, dataverseSession.getUser(), dataverse);

        // then
        TypedQuery<RoleAssignment> query = em.createNamedQuery(
                "RoleAssignment.listByAssigneeIdentifier_DefinitionPointId_RoleId",
                RoleAssignment.class);
        query.setParameter("assigneeIdentifier", dataverseSession.getUser().getIdentifier());
        query.setParameter("definitionPointId", dataverse.getId());
        query.setParameter("roleId", roleToBeAssigned.getId());
        List<RoleAssignment> roles = query.getResultList();

        assertEquals(1, roles.size());
        RoleAssignment role = roles.get(0);
        assertEquals(dataverseSession.getUser().getIdentifier(), role.getAssigneeIdentifier());
        assertEquals(dataverse.getId(), role.getDefinitionPoint().getId());
        assertEquals(roleToBeAssigned.getId(), role.getRole().getId());

        await()
                .atMost(Duration.ofSeconds(5L))
                .until(() -> smtpServer.getMails().stream()
                        .anyMatch(emailModel -> emailModel.getTo().equals(userEmail)));

    }

    @Test
    @Transactional(TransactionMode.ROLLBACK)
    public void shouldAssignRoleWithNotification_withPermissionsException() {
        // given
        Dataverse dataverse = dataverseDao.find(1L);
        DataverseRole roleToBeAssigned = roleService.findBuiltinRoleByAlias(BuiltInRole.EDITOR);
        dataverseSession.setUser(GuestUser.get());

        // when&then
        assertThrows(PermissionException.class, () -> managePermissionsService.assignRoleWithNotification(roleToBeAssigned, dataverseSession.getUser(), dataverse));
    }

    @Test
    public void removeRoleAssignmentWithNotification() {
        // given
        Dataverse dataverse = dataverseDao.find(1L);
        String userEmail = dataverseSession.getUser().getDisplayInfo().getEmailAddress();
        RoleAssignment toBeRemoved = new RoleAssignment(roleService.findBuiltinRoleByAlias(BuiltInRole.EDITOR), dataverseSession.getUser(), dataverse, null);
        toBeRemoved = roleAssignmentRepository.save(toBeRemoved);
        Long toBeRemovedId = toBeRemoved.getId();

        // when
        managePermissionsService.removeRoleAssignmentWithNotification(toBeRemoved);

        // then
        assertFalse(roleAssignmentRepository.findById(toBeRemovedId).isPresent());

        await()
                .atMost(Duration.ofSeconds(5L))
                .until(() -> smtpServer.getMails().stream()
                        .anyMatch(emailModel -> emailModel.getTo().equals(userEmail)));
    }

    @Test
    @Transactional(TransactionMode.ROLLBACK)
    public void removeRoleAssignmentWithNotification_withPermissionsException() {
        // given
        Dataverse dataverse = dataverseDao.find(1L);
        RoleAssignment toBeRemoved = new RoleAssignment(roleService.findBuiltinRoleByAlias(BuiltInRole.EDITOR), dataverseSession.getUser(), dataverse, null);
        em.persist(toBeRemoved);
        em.flush();

        dataverseSession.setUser(GuestUser.get());

        // when&then
        assertThrows(PermissionException.class, () -> managePermissionsService.removeRoleAssignmentWithNotification(toBeRemoved));
    }

    @Test
    @Transactional(TransactionMode.ROLLBACK)
    public void shouldSaveOrUpdateRole() {
        // given
        Dataverse dataverse = dataverseDao.find(19L);
        DataverseRole toBeSaved = new DataverseRole();
        toBeSaved.setOwner(dataverse);
        toBeSaved.setName("newRoleName");
        toBeSaved.setAlias("newRoleAlias");
        toBeSaved.setDescription("newRoleDesc");

        // when
        managePermissionsService.saveOrUpdateRole(toBeSaved);

        // then
        DataverseRole dbRole = roleService.findRoleByAliasAssignableInDataverse("newRoleAlias", dataverse.getId());
        assertTrue(dataverse.getRoles()
                .stream()
                .map(DataverseRole::getId)
                .collect(Collectors.toList())
                .contains(dbRole.getId()));
        assertTrue(dataverse.getRoles()
                .stream()
                .map(DataverseRole::getAlias)
                .collect(Collectors.toList())
                .contains("newRoleAlias"));
    }

    @Test
    @Transactional(TransactionMode.ROLLBACK)
    public void shouldSaveOrUpdateRole_withPermissionsException() {
        // given
        Dataverse dataverse = dataverseDao.find(19L);
        DataverseRole toBeSaved = new DataverseRole();
        toBeSaved.setOwner(dataverse);
        toBeSaved.setName("newRoleName");
        toBeSaved.setAlias("newRoleAlias");
        toBeSaved.setDescription("newRoleDesc");

        dataverseSession.setUser(GuestUser.get());

        // when&then
        assertThrows(PermissionException.class, () -> managePermissionsService.saveOrUpdateRole(toBeSaved));
    }

    @Test
    @Transactional(TransactionMode.ROLLBACK)
    public void shouldSetDataverseDefaultContributorRole() {
        // given
        Dataverse dataverse = dataverseDao.find(19L);
        DataverseRole toBeSetDefault = new DataverseRole();
        toBeSetDefault.setOwner(dataverse);
        toBeSetDefault.setName("newRoleName");
        toBeSetDefault.setAlias("newRoleAlias");
        toBeSetDefault.setDescription("newRoleDesc");
        em.persist(toBeSetDefault);
        em.flush();

        // when
        managePermissionsService.setDataverseDefaultContributorRole(toBeSetDefault, dataverse);

        // then
        assertEquals(toBeSetDefault.getId(), dataverse.getDefaultContributorRole().getId());
        assertEquals(toBeSetDefault.getAlias(), dataverse.getDefaultContributorRole().getAlias());
    }

    @Test
    @Transactional(TransactionMode.ROLLBACK)
    public void shouldSetDataverseDefaultContributorRole_withPermissionsException() {
        // given
        Dataverse dataverse = dataverseDao.find(19L);
        DataverseRole toBeSetDefault = new DataverseRole();
        toBeSetDefault.setOwner(dataverse);
        toBeSetDefault.setName("newRoleName");
        toBeSetDefault.setAlias("newRoleAlias");
        toBeSetDefault.setDescription("newRoleDesc");

        dataverseSession.setUser(GuestUser.get());

        // when&then
        assertThrows(PermissionException.class, () -> managePermissionsService.setDataverseDefaultContributorRole(toBeSetDefault, dataverse));
    }
}
