package edu.harvard.iq.dataverse;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newHashSet;
import static edu.harvard.iq.dataverse.persistence.dataset.DatasetLock.Reason.InReview;
import static edu.harvard.iq.dataverse.persistence.dataset.DatasetLock.Reason.Ingest;
import static edu.harvard.iq.dataverse.persistence.dataset.DatasetLock.Reason.Workflow;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import edu.harvard.iq.dataverse.PermissionServiceBean.RequestPermissionQuery;
import edu.harvard.iq.dataverse.authorization.groups.GroupServiceBean;
import edu.harvard.iq.dataverse.engine.command.DataverseRequest;
import edu.harvard.iq.dataverse.mail.confirmemail.ConfirmEmailServiceBean;
import edu.harvard.iq.dataverse.persistence.DvObject;
import edu.harvard.iq.dataverse.persistence.MocksFactory;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetLock;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.group.Group;
import edu.harvard.iq.dataverse.persistence.group.IpAddress;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.persistence.user.DataverseRole;
import edu.harvard.iq.dataverse.persistence.user.GuestUser;
import edu.harvard.iq.dataverse.persistence.user.Permission;
import edu.harvard.iq.dataverse.persistence.user.RoleAssignment;
import edu.harvard.iq.dataverse.persistence.user.User;
import edu.harvard.iq.dataverse.util.SystemConfig;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class PermissionServiceBeanTest {
	
	private final static IpAddress localhost = IpAddress.valueOf("127.0.0.1");

    @InjectMocks
    private PermissionServiceBean permissionService;

    @Mock
    private DataverseRoleServiceBean roleService;

    @Mock
    private SystemConfig systemConfig;

    @Mock
    private GroupServiceBean groupService;

    @Mock
    private ConfirmEmailServiceBean confirmEmailService;

    private AuthenticatedUser authenticatedUser = MocksFactory.makeAuthenticatedUser("John", "Doe");
    private DataverseRequest authenticatedUserRequest = new DataverseRequest(this.authenticatedUser, 
    		localhost);

    private GuestUser guestUser = GuestUser.get();
    private DataverseRequest guestUserRequest = new DataverseRequest(this.guestUser, 
    		localhost);

    private Dataset dataset = MocksFactory.makeDataset();

    // -------------------- TESTS --------------------

    @Test
    @DisplayName("Should have permission when authenticated user have role assignments that contains checked permission")
    public void requestOn_user_have_correct_role_assignment() {

    	directRoleAssignmentsByAssigneesAndDvObjectsReturns(
    			Permission.ViewUnpublishedDataset, Permission.EditDataset);

        assertThat(requestOn(this.authenticatedUserRequest).
        		has(Permission.ViewUnpublishedDataset)).isTrue();
        
        verify(this.roleService).directRoleAssignmentsByAssigneesAndDvObjects(
                singleton(this.authenticatedUser),
                datasetAndOwner());
    }

    @Test
    @DisplayName("Should have permission when authenticated user is member of group which have role assignments that contains checked permission")
    public void requestOn_user_have_correct_role_assignment_by_group_assignment() {

        Group group = mock(Group.class);

        when(this.groupService.groupsFor(this.authenticatedUserRequest, this.dataset)).
        	thenReturn(singleton(group));
        directRoleAssignmentsByAssigneesAndDvObjectsReturns(
        		Permission.ViewUnpublishedDataset, Permission.EditDataset);

        assertThat(requestOn(this.authenticatedUserRequest).
        		has(Permission.ViewUnpublishedDataset)).isTrue();
        
        verify(this.roleService).directRoleAssignmentsByAssigneesAndDvObjects(
                newHashSet(this.authenticatedUser, group),
                datasetAndOwner());
    }

    @Test
    @DisplayName("Should not have permission when authenticated user do not have role assignments with checked permission")
    public void requestOn_user_do_not_have_correct_role_assignment() {

    	directRoleAssignmentsByAssigneesAndDvObjectsReturns(
    			Permission.ViewUnpublishedDataset, Permission.EditDataset);

        assertThat(requestOn(this.authenticatedUserRequest).
        		has(Permission.PublishDataset)).isFalse();
        
        verify(this.roleService).directRoleAssignmentsByAssigneesAndDvObjects(
                singleton(this.authenticatedUser),
                datasetAndOwner());
    }

    @Test
    @DisplayName("Should have permission when guest user have role assignments that contains checked permission")
    public void requestOn_guestUser_have_correct_role_assignment() {

    	directRoleAssignmentsByAssigneesAndDvObjectsReturns(
    			Permission.ViewUnpublishedDataset, Permission.EditDataset);

        assertThat(requestOn(this.guestUserRequest).
        		has(Permission.ViewUnpublishedDataset)).isTrue();
        
        verify(this.roleService).directRoleAssignmentsByAssigneesAndDvObjects(
                singleton(this.guestUser),
                datasetAndOwner());
    }

    @Test
    @DisplayName("Should not have permission when user is guest and checked permission is for authenticated users only")
    public void requestOn_guestUser_permission_is_for_authenticated_users_only() {

        assertThat(requestOn(this.guestUserRequest).
        		has(Permission.EditDataset)).isFalse();
        
        verifyNoInteractions(this.roleService);
    }

    @Test
    @DisplayName("Should not have permission when readonly mode is on and checked permission is write permission")
    public void requestOn_user_do_not_have_permission_for_write_operations_in_readonly_mode() {

    	readOnlyOn();

        assertThat(requestOn(this.authenticatedUserRequest).
        		has(Permission.PublishDataset)).isFalse();
        
        verifyNoInteractions(this.groupService, this.roleService);
    }

    @Test
    @DisplayName("Should have permission when readonly mode is on and checked permission is not write permission")
    public void requestOn_user_do_have_permission_for_not_write_operations_in_readonly_mode() {

    	readOnlyOn();
    	directRoleAssignmentsByAssigneesAndDvObjectsReturns(
    			Permission.ViewUnpublishedDataset, Permission.EditDataset);

        assertThat(requestOn(this.authenticatedUserRequest).
        		has(Permission.ViewUnpublishedDataset)).isTrue();
        
        verify(this.roleService).directRoleAssignmentsByAssigneesAndDvObjects(
                singleton(this.authenticatedUser),
                datasetAndOwner());
    }


    @Test
    @DisplayName("Should have same permissions as defined by role assignments when user is authenticated")
    public void permissionsFor_authenticated_user() {

    	directRoleAssignmentsByAssigneesAndDvObjectsReturns(
    			Permission.ViewUnpublishedDataset, Permission.EditDataset);

        assertThat(permissionsFor(this.authenticatedUserRequest)).
        	containsExactlyInAnyOrder(Permission.ViewUnpublishedDataset, Permission.EditDataset);
        
        verify(this.roleService).directRoleAssignmentsByAssigneesAndDvObjects(
                singleton(this.authenticatedUser),
                datasetAndOwner());
    }

    @Test
    @DisplayName("Should have all permissions when user is superadmin")
    public void permissionsFor_superuser() {
    	
        this.authenticatedUser.setSuperuser(true);

        assertThat(permissionsFor(this.authenticatedUserRequest)).
        	containsExactlyInAnyOrderElementsOf(Permission.all());
    }

    @Test
    @DisplayName("Should have permissions without any permission dedicated for authenticated users only")
    public void permissionsFor_guest() {

    	directRoleAssignmentsByAssigneesAndDvObjectsReturns(
                    Permission.PublishDataset,
                    Permission.ViewUnpublishedDataset,
                    Permission.EditDataset);

        assertThat(permissionsFor(this.guestUserRequest)).
        	containsExactlyInAnyOrder(Permission.ViewUnpublishedDataset);
    }

    @Test
    @DisplayName("Should have only read permissions when user is authenticated and readonly mode is on")
    public void permissionsFor_authenticated_users_in_readonly_mode() {
    	
    	readOnlyOn();
    	directRoleAssignmentsByAssigneesAndDvObjectsReturns(Permission.values());

        assertThat(permissionsFor(this.authenticatedUserRequest)).
        	containsExactlyInAnyOrder(Permission.ViewUnpublishedDataverse, 
        			Permission.ViewUnpublishedDataset, Permission.DownloadFile);
    }

    @Test
    @DisplayName("Should have only read permissions when user is superuser and readonly mode is on")
    public void permissionsFor_superuser_in_readonly_mode() {

        this.authenticatedUser.setSuperuser(true);
        readOnlyOn();

        assertThat(permissionsFor(this.authenticatedUserRequest)).
        	containsExactlyInAnyOrder(Permission.ViewUnpublishedDataverse, 
        			Permission.ViewUnpublishedDataset, Permission.DownloadFile);
    }

	@Test
	@DisplayName("Should have edit dataverse permission")
	public void permissionsFor_user_with_editDataverse_permission() {

		User user = new AuthenticatedUser();
		Dataverse dataverse = new Dataverse();
		directRoleAssignmentsByAssigneesAndDvObjectsReturns(Permission.EditDataverse);

		assertThat(this.permissionService.isUserAbleToEditDataverse(user, dataverse)).isTrue();
	}

    @Test
    @DisplayName("Shouldn't have edit dataverse permission")
    public void permissionsFor_user_without_editDataverse_permission() {

        User user = new AuthenticatedUser();
        Dataverse dataverse = new Dataverse();

        assertThat(this.permissionService.isUserAbleToEditDataverse(user, dataverse)).isFalse();
    }
    
    @Test
    public void checkEditDatasetLockNonThrowing() {
        
        Dataset set = new Dataset();
        DatasetLock lock = new DatasetLock(Ingest, new AuthenticatedUser());
        DataverseRequest request = new DataverseRequest(new AuthenticatedUser(), (IpAddress)null);
        
        assertThat(this.permissionService.checkEditDatasetLockNonThrowing(set, null)).isFalse();
        
        set.addLock(lock);
        
        assertThat(this.permissionService.checkEditDatasetLockNonThrowing(set, null)).isTrue();
        
        lock.setReason(Workflow);
        
        assertThat(this.permissionService.checkEditDatasetLockNonThrowing(set, null)).isTrue();
        
        lock.setReason(InReview);
        
        assertThat(this.permissionService.checkEditDatasetLockNonThrowing(set, request)).isTrue();
    }

    // -------------------- PRIVATE --------------------

    private RoleAssignment roleAssignmentWith(final Permission... permissions) {
    	
        final RoleAssignment assignment = new RoleAssignment();

        final DataverseRole role = new DataverseRole();
        role.addPermissions(newArrayList(permissions));
        assignment.setRole(role);

        return assignment;
    }
    
    private RequestPermissionQuery requestOn(final DataverseRequest request) {
    	
    	return this.permissionService.requestOn(request, this.dataset);
    }
    
    private Set<Permission> permissionsFor(final DataverseRequest request) {
    	
    	return  this.permissionService.permissionsFor(request, this.dataset);
    }
    
    private void readOnlyOn() {
    	
    	when(this.systemConfig.isReadonlyMode()).thenReturn(true);
    }
    
    private Set<DvObject> datasetAndOwner() {
    	return newHashSet(this.dataset, this.dataset.getOwner());
    }
    
    private void directRoleAssignmentsByAssigneesAndDvObjectsReturns(final Permission... permissions) {
    	
        when(this.roleService.directRoleAssignmentsByAssigneesAndDvObjects(any(), any()))
        	.thenReturn(singletonList(roleAssignmentWith(permissions)));
    }
    
}
