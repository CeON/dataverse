package edu.harvard.iq.dataverse.permission;

import static edu.harvard.iq.dataverse.persistence.user.DataverseRole.BuiltInRole.ADMIN;
import static edu.harvard.iq.dataverse.persistence.user.DataverseRole.BuiltInRole.COLLECTION_CUSTODIAN;
import static edu.harvard.iq.dataverse.persistence.user.DataverseRole.BuiltInRole.CURATOR;
import static edu.harvard.iq.dataverse.persistence.user.DataverseRole.BuiltInRole.DEPOSITOR;
import static edu.harvard.iq.dataverse.persistence.user.DataverseRole.BuiltInRole.DS_CONTRIBUTOR;
import static edu.harvard.iq.dataverse.persistence.user.DataverseRole.BuiltInRole.DV_CONTRIBUTOR;
import static edu.harvard.iq.dataverse.persistence.user.DataverseRole.BuiltInRole.EDITOR;
import static edu.harvard.iq.dataverse.persistence.user.DataverseRole.BuiltInRole.FULL_CONTRIBUTOR;
import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.enterprise.event.Event;
import javax.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;

import edu.harvard.iq.dataverse.DataverseDao;
import edu.harvard.iq.dataverse.DataverseRequestServiceBean;
import edu.harvard.iq.dataverse.DataverseRoleServiceBean;
import edu.harvard.iq.dataverse.DataverseSession;
import edu.harvard.iq.dataverse.DvObjectServiceBean;
import edu.harvard.iq.dataverse.EjbDataverseEngine;
import edu.harvard.iq.dataverse.NavigationWrapper;
import edu.harvard.iq.dataverse.PermissionServiceBean;
import edu.harvard.iq.dataverse.PermissionsWrapper;
import edu.harvard.iq.dataverse.RoleAssigneeServiceBean;
import edu.harvard.iq.dataverse.actionlogging.ActionLogServiceBean;
import edu.harvard.iq.dataverse.authorization.groups.GroupServiceBean;
import edu.harvard.iq.dataverse.engine.command.DataverseRequest;
import edu.harvard.iq.dataverse.mail.confirmemail.ConfirmEmailServiceBean;
import edu.harvard.iq.dataverse.notification.UserNotificationService;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.dataverse.DataverseRepository;
import edu.harvard.iq.dataverse.persistence.group.AuthenticatedUsers;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.persistence.user.DataverseRole;
import edu.harvard.iq.dataverse.persistence.user.DataverseRoleRepository;
import edu.harvard.iq.dataverse.persistence.user.GuestUser;
import edu.harvard.iq.dataverse.persistence.user.Permission;
import edu.harvard.iq.dataverse.persistence.user.RoleAssignment;
import edu.harvard.iq.dataverse.persistence.user.RoleAssignmentRepository;
import edu.harvard.iq.dataverse.search.index.PermissionReindexEvent;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import edu.harvard.iq.dataverse.util.SystemConfig;
import edu.harvard.iq.dataverse.util.UIMessages;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = LENIENT)
public class ManagePermissionsPageTest {
	
	private final static List<DataverseRole> roles = asList(
			newRole(1L, DV_CONTRIBUTOR.getAlias()),
			newRole(2L, DS_CONTRIBUTOR.getAlias()),
			newRole(3L, FULL_CONTRIBUTOR.getAlias()),
			newRole(4L, EDITOR.getAlias()),
			newRole(5L, CURATOR.getAlias()),
			newRole(6L, DEPOSITOR.getAlias()),
			newRole(7L, "custom"),
			newRole(8L, ADMIN.getAlias()),
			newRole(9L, COLLECTION_CUSTODIAN.getAlias())
			);

	@Mock
	private SettingsServiceBean settings;
	@Mock 
	private UIMessages ui;
	@Mock
	private DvObjectServiceBean dvObjectService;
	@Mock 
	private RoleAssigneeServiceBean roleAssigneeService;
	@Mock
	private Event<PermissionReindexEvent> permissionReindexEvent;
	@Mock
	private DataverseRoleRepository dataverseRoleRepository;
	@Mock
	private RoleAssignmentRepository roleAssignmentRepository;
	@Mock
    private DataverseRepository dataverseRepository;
	@Mock
	private DataverseDao dataverseDao;
	@Mock
	private GroupServiceBean groupService;
	@Mock
	private ConfirmEmailServiceBean confirmEmailService;
	@Mock
	private ActionLogServiceBean logService;
	@Mock
	private HttpServletRequest request;
	@Mock
	private EjbDataverseEngine commandEngine;
	@Mock
	private UserNotificationService userNotificationService;
	@Mock
	private NavigationWrapper navigation;
	@Mock
	private ManagePermissionsService permissionService;
	
	private DataverseSession session;
	private DataverseRequestServiceBean requestService;
	private ManagePermissionsPage page;
	private final Dataverse dataverse = newDataverse();
	
	@BeforeEach
	void setUp() {
		
		SystemConfig systemConfig = new SystemConfig(this.settings);
		this.session = new DataverseSession(this.logService, systemConfig);
		this.requestService = new DataverseRequestServiceBean(
				this.session, this.request);
		this.requestService.setup();
		
		DataverseRoleServiceBean  roleService = new DataverseRoleServiceBean(
				this.roleAssigneeService, this.permissionReindexEvent, 
				this.dataverseRoleRepository, this.roleAssignmentRepository, 
				this.dataverseRepository);
		
		PermissionServiceBean permissionService = new PermissionServiceBean(
				roleService, this.roleAssigneeService, this.dataverseDao, 
				this.dvObjectService, this.groupService, systemConfig, 
				this.confirmEmailService, this.roleAssignmentRepository);
		
		PermissionsWrapper permissionWrapper = new PermissionsWrapper(
				permissionService, requestService, this.navigation);
	
		this.page = new ManagePermissionsPage(this.dvObjectService, roleService, 
				this.roleAssigneeService, permissionService, requestService, 
				permissionWrapper, this.permissionService, this.session, this.ui);
		
		when(this.navigation.notAuthorized()).thenReturn("notAuthorized");
		when(this.navigation.notFound()).thenReturn("notFound");
		
		when(this.dvObjectService.findDvObject(anyLong())).thenReturn(Optional.of(this.dataverse));
		
		when(this.dataverseRoleRepository.findByAlias(anyString())).thenAnswer(invocation -> {
			return Optional.of(getRole(invocation.getArgument(0)));
		});
		
		when(this.roleAssigneeService.getRoleAssignee(any())).thenReturn(GuestUser.get());
		
		when(this.groupService.groupsFor(any(DataverseRequest.class), eq(this.dataverse))).
			thenReturn(singleton(AuthenticatedUsers.get()));
	}
	
	//--------------------------------------------------------------------------
	@Test
	public void page_returnsNotFound_ifObjectId_isNotSet() {
		
		String initResult = this.page.init();
		
		assertThat(initResult).isEqualTo("notFound");
	}
	
	@Test
	public void page_returnsNotAuthorized_ifUserIsNotLoggedIn() {
		
		this.page.setId(1L);
		String initResult = this.page.init();
		
		assertThat(initResult).isEqualTo("notAuthorized");
	}
	
	@Test
	public void page_returnsNotAuthorized_ifUnathorizedUserIsLoggedIn() {
		
		this.page.setId(1L);
		logIn(newUser(false));
		
		String initResult = this.page.init();
		
		assertThat(initResult).isEqualTo("notAuthorized");
	}
	
	@Test
	public void page_returnsNotAuthorized_ifAthorizedUserIsLoggedIn() {
		
		this.page.setId(1L);
		AuthenticatedUser user = newUser(false);
		when(this.roleAssignmentRepository.findByAssigneeIdentifiersAndDefinitionPointIds(anyList(), anyList())).
			thenReturn(singletonList(newManagerRoleAssinment(user)));
		logIn(user);
		
		String initResult = this.page.init();
		
		assertThat(initResult).isEqualTo("");
	}
	
	@Test
	public void page_returnsEMpty_whenSuerUserIsLoggedIn() {
		
		this.page.setId(1L);
		logIn(newUser(true));
		
		String initResult = this.page.init();
		
		assertThat(initResult).isEqualTo("");
	}
	
	@Test
	public void page_isProperlyInitialized() {
	
		this.page.setId(1L);
		logIn(newUser(true));
		this.page.init();
		
		DataverseDefaultSettingsTab.PermissionsConfigureDialog dialog = this.page.getSettingsTab().getDialog();
		
		assertThat(this.page.displaySettingsTab()).isTrue();
		assertThat(dialog.getCreatorRoles().get(0).getRole()).isNull();
		assertThat(dialog.getCreatorRoles().get(1).getRole().getAlias()).
			isEqualTo(DV_CONTRIBUTOR.getAlias());
		assertThat(dialog.getCreatorRoles().get(2).getRole().getAlias()).
			isEqualTo(DS_CONTRIBUTOR.getAlias());
		assertThat(dialog.getCreatorRoles().get(3).getRole().getAlias()).
			isEqualTo(FULL_CONTRIBUTOR.getAlias());
		
		assertThat(dialog.getDefaultDatasetContributorRoles().get(0).getRole().getAlias()).
			isEqualTo(EDITOR.getAlias());
		assertThat(dialog.getDefaultDatasetContributorRoles().get(1).getRole().getAlias()).
			isEqualTo(CURATOR.getAlias());
		assertThat(dialog.getDefaultDatasetContributorRoles().get(2).getRole().getAlias()).
			isEqualTo(DEPOSITOR.getAlias());
		assertThat(dialog.getDefaultDatasetContributorRoles().get(3).getRole()).isNull();
		
		assertThat(dialog.getDefaultDataverseContributorRoles().get(0).getRole().getAlias()).
		isEqualTo(ADMIN.getAlias());
		assertThat(dialog.getDefaultDataverseContributorRoles().get(1).getRole().getAlias()).
		isEqualTo(COLLECTION_CUSTODIAN.getAlias());
	}
	
	@Test
	public void settingsTab_allowsModificationOfAnswerToQuestions1And2() {

		this.page.setId(1L);
		logIn(newUser(true));
		this.page.init();
		
		DataverseDefaultSettingsTab.RoleOption anyone = 
				this.page.getSettingsTab().getDialog().getCreatorRoles().get(0);
		DataverseDefaultSettingsTab.RoleOption dvContributor = 
				this.page.getSettingsTab().getDialog().getCreatorRoles().get(1);
		
		DataverseDefaultSettingsTab.RoleOption editor = 
				this.page.getSettingsTab().getDialog().getDefaultDatasetContributorRoles().get(0);
		DataverseDefaultSettingsTab.RoleOption none = 
				this.page.getSettingsTab().getDialog().getDefaultDatasetContributorRoles().get(3);
		
		DataverseDefaultSettingsTab.RoleOption admin = 
				this.page.getSettingsTab().getDialog().getDefaultDataverseContributorRoles().get(0);
		DataverseDefaultSettingsTab.RoleOption custodian = 
				this.page.getSettingsTab().getDialog().getDefaultDataverseContributorRoles().get(1);
		
		assertThat(this.page.getSettingsTab().getCreatorRole()).isEqualTo(anyone);
		assertThat(this.page.getSettingsTab().getDefaultDatasetContributorRole()).isEqualTo(none);
		assertThat(this.page.getSettingsTab().getDefaultDataverseContributorRole()).isEqualTo(admin);
		
		assertThat(this.page.getSettingsTab().getDialog().displayDefaultDataverseContributorRoleSelection()).isTrue();
		assertThat(this.page.getSettingsTab().getDialog().getCreatorRoleIndex()).isEqualTo(0);
		assertThat(this.page.getSettingsTab().getDialog().getDefaultDatasetContributorRoleIndex()).isEqualTo(3);
		assertThat(this.page.getSettingsTab().getDialog().getDefaultDataverseContributorRoleIndex()).isEqualTo(0);
		
		this.page.getSettingsTab().getDialog().setCreatorRoleIndex(1);
		this.page.getSettingsTab().getDialog().setDefaultDatasetContributorRoleIndex(0);
		this.page.getSettingsTab().getDialog().setDefaultDataverseContributorRoleIndex(1);
		
		assertThat(this.page.getSettingsTab().getCreatorRole()).isEqualTo(anyone);
		assertThat(this.page.getSettingsTab().getDefaultDatasetContributorRole()).isEqualTo(none);
		
		this.page.getSettingsTab().getDialog().save();
		
		assertThat(this.page.getSettingsTab().getCreatorRole()).isEqualTo(dvContributor);
		assertThat(this.page.getSettingsTab().getDefaultDatasetContributorRole()).isEqualTo(editor);
		assertThat(this.page.getSettingsTab().getDefaultDataverseContributorRole()).isEqualTo(custodian);

		
		verify(this.permissionService, times(1))
			.assignRoleWithNotification(eq(dvContributor.getRole()), any(), 
					eq(this.page.getDvObject()));
		
		verify(this.permissionService, times(0))
			.removeRoleAssignmentWithNotification(any());
		
		verify(this.permissionService, times(1))
			.setDefaultDatasetContributorRole(eq(editor.getRole()), 
					eq((Dataverse)this.page.getDvObject()));
		
		verify(this.permissionService, times(1))
			.setDefaultDataverseContributorRole(eq(custodian.getRole()), 
				eq((Dataverse)this.page.getDvObject()));
	}
	
	@Test
	public void settingsTab_properlyManupulatesPresetRoles_AndUpdatesThem() {
	
		final RoleAssignment assignment = new RoleAssignment();
		assignment.setRole(getRole(FULL_CONTRIBUTOR.getAlias()));
		assignment.setDefinitionPoint(this.dataverse);
		
		when(this.roleAssignmentRepository.findByAssigneeIdentifier(anyString()))
			.thenReturn(Collections.singletonList(assignment));
			
		this.dataverse.setDefaultDatasetContributorRole(getRole(EDITOR.getAlias()));
		this.dataverse.setDefaultDataverseContributorRole(getRole(COLLECTION_CUSTODIAN.getAlias()));
		
		this.page.setId(1L);
		logIn(newUser(true));
		this.page.init();
		
		assertThat(this.page.displaySettingsTab()).isTrue();
		
		DataverseDefaultSettingsTab.RoleOption anyone = 
				this.page.getSettingsTab().getDialog().getCreatorRoles().get(0);
		DataverseDefaultSettingsTab.RoleOption fullContributor = 
				this.page.getSettingsTab().getDialog().getCreatorRoles().get(3);
		
		DataverseDefaultSettingsTab.RoleOption editor = 
				this.page.getSettingsTab().getDialog().getDefaultDatasetContributorRoles().get(0);
		DataverseDefaultSettingsTab.RoleOption none = 
				this.page.getSettingsTab().getDialog().getDefaultDatasetContributorRoles().get(3);
		
		DataverseDefaultSettingsTab.RoleOption admin = 
				this.page.getSettingsTab().getDialog().getDefaultDataverseContributorRoles().get(0);
		DataverseDefaultSettingsTab.RoleOption custodian = 
				this.page.getSettingsTab().getDialog().getDefaultDataverseContributorRoles().get(1);
		
		assertThat(this.page.getSettingsTab().getCreatorRole()).isEqualTo(fullContributor);
		assertThat(this.page.getSettingsTab().getDefaultDatasetContributorRole()).isEqualTo(editor);
		assertThat(this.page.getSettingsTab().getDefaultDataverseContributorRole()).isEqualTo(custodian);
		
		assertThat(this.page.getSettingsTab().getDialog().displayDefaultDataverseContributorRoleSelection()).isTrue();
		assertThat(this.page.getSettingsTab().getDialog().getCreatorRoleIndex()).isEqualTo(3);
		assertThat(this.page.getSettingsTab().getDialog().getDefaultDatasetContributorRoleIndex()).isEqualTo(0);
		assertThat(this.page.getSettingsTab().getDialog().getDefaultDataverseContributorRoleIndex()).isEqualTo(1);
		
		
		this.page.getSettingsTab().getDialog().setCreatorRoleIndex(0);
		this.page.getSettingsTab().getDialog().setDefaultDatasetContributorRoleIndex(3);
		this.page.getSettingsTab().getDialog().setDefaultDataverseContributorRoleIndex(0);
		
		
		assertThat(this.page.getSettingsTab().getCreatorRole()).isEqualTo(fullContributor);
		assertThat(this.page.getSettingsTab().getDefaultDatasetContributorRole()).isEqualTo(editor);
		assertThat(this.page.getSettingsTab().getDefaultDataverseContributorRole()).isEqualTo(custodian);
		
		this.page.getSettingsTab().getDialog().save();
		
		assertThat(this.page.getSettingsTab().getCreatorRole()).isEqualTo(anyone);
		assertThat(this.page.getSettingsTab().getDefaultDatasetContributorRole()).isEqualTo(none);
		assertThat(this.page.getSettingsTab().getDefaultDataverseContributorRole()).isEqualTo(admin);

		
		verify(this.permissionService, times(0)).
			assignRoleWithNotification(any(), any(), any());
		verify(this.permissionService, times(1)).
			removeRoleAssignmentWithNotification(
				argThat(a -> a.getRole().equals(getRole(FULL_CONTRIBUTOR.getAlias()))));
		
		verify(this.permissionService, times(1)).
			setDefaultDatasetContributorRole(eq(null), 
					eq((Dataverse)this.page.getDvObject()));
		
		verify(this.permissionService, times(1))
			.setDefaultDataverseContributorRole(eq(admin.getRole()), 
					eq((Dataverse)this.page.getDvObject()));
	}
	
	@Test
	public void settingsTab_properlyHandlesCustomPresetRole_AndUpdatesIt() {
			
		this.dataverse.setDefaultDatasetContributorRole(getRole("custom"));
		
		this.page.setId(1L);
		logIn(newUser(true));
		this.page.init();
		
		assertThat(this.page.displaySettingsTab()).isTrue();
		
		DataverseDefaultSettingsTab.PermissionsConfigureDialog dialog = this.page.getSettingsTab().getDialog();
		
		assertThat(dialog.getDefaultDatasetContributorRoles().get(0).getRole().getAlias()).
			isEqualTo(EDITOR.getAlias());
		assertThat(dialog.getDefaultDatasetContributorRoles().get(1).getRole().getAlias()).
			isEqualTo(CURATOR.getAlias());
		assertThat(dialog.getDefaultDatasetContributorRoles().get(2).getRole().getAlias()).
			isEqualTo(DEPOSITOR.getAlias());
		assertThat(dialog.getDefaultDatasetContributorRoles().get(3).getRole().getAlias()).
			isEqualTo("custom");
		assertThat(dialog.getDefaultDatasetContributorRoles().get(4).getRole()).isNull();
		
		DataverseDefaultSettingsTab.RoleOption custom = 
				this.page.getSettingsTab().getDialog().getDefaultDatasetContributorRoles().get(3);
		DataverseDefaultSettingsTab.RoleOption none = 
				this.page.getSettingsTab().getDialog().getDefaultDatasetContributorRoles().get(4);
		
		assertThat(this.page.getSettingsTab().getDefaultDatasetContributorRole()).isEqualTo(custom);
		
		assertThat(dialog.getDefaultDatasetContributorRoleIndex()).isEqualTo(3);
		
		dialog.setDefaultDatasetContributorRoleIndex(4);
		
		assertThat(this.page.getSettingsTab().getDefaultDatasetContributorRole()).isEqualTo(custom);
		
		dialog.save();
		
		assertThat(this.page.getSettingsTab().getDefaultDatasetContributorRole()).isEqualTo(none);
		
		verify(this.permissionService, times(1)).
			setDefaultDatasetContributorRole(eq(null), 
					eq((Dataverse)this.page.getDvObject()));
	}
	
	@Test
	public void settingsTab_takesNotAction_ifNothingChanges() {
	
		final RoleAssignment assignment = new RoleAssignment();
		assignment.setRole(getRole(FULL_CONTRIBUTOR.getAlias()));
		assignment.setDefinitionPoint(this.dataverse);
		
		when(this.roleAssignmentRepository.findByAssigneeIdentifier(anyString()))
			.thenReturn(Collections.singletonList(assignment));
			
		this.dataverse.setDefaultDatasetContributorRole(getRole(EDITOR.getAlias()));
		
		this.page.setId(1L);
		logIn(newUser(true));
		this.page.init();
		
		assertThat(this.page.displaySettingsTab()).isTrue();
		
		DataverseDefaultSettingsTab.RoleOption fullContributor = 
				this.page.getSettingsTab().getDialog().getCreatorRoles().get(3);
		
		DataverseDefaultSettingsTab.RoleOption editor = 
				this.page.getSettingsTab().getDialog().getDefaultDatasetContributorRoles().get(0);
		
		DataverseDefaultSettingsTab.RoleOption admin = 
				this.page.getSettingsTab().getDialog().getDefaultDataverseContributorRoles().get(0);
		
		assertThat(this.page.getSettingsTab().getCreatorRole()).isEqualTo(fullContributor);
		assertThat(this.page.getSettingsTab().getDefaultDatasetContributorRole()).isEqualTo(editor);
		assertThat(this.page.getSettingsTab().getDefaultDataverseContributorRole()).isEqualTo(admin);
		
		assertThat(this.page.getSettingsTab().getDialog().getCreatorRoleIndex()).isEqualTo(3);
		assertThat(this.page.getSettingsTab().getDialog().getDefaultDatasetContributorRoleIndex()).isEqualTo(0);
		assertThat(this.page.getSettingsTab().getDialog().getDefaultDataverseContributorRoleIndex()).isEqualTo(0);
		
		// don't change anything
		
		this.page.getSettingsTab().getDialog().save();
		
		assertThat(this.page.getSettingsTab().getCreatorRole()).isEqualTo(fullContributor);
		assertThat(this.page.getSettingsTab().getDefaultDatasetContributorRole()).isEqualTo(editor);
		assertThat(this.page.getSettingsTab().getDefaultDataverseContributorRole()).isEqualTo(admin);

		
		verify(this.permissionService, times(0)).
			assignRoleWithNotification(any(), any(), any());
		verify(this.permissionService, times(0)).
			removeRoleAssignmentWithNotification(any());
	}
	
	@Test
	public void settingsTab_doesNotUpdate_defaultDataverseContributorLole_ifSuperuserInNotLoggedIn() {
	
		final RoleAssignment assignment = new RoleAssignment();
		assignment.setRole(getRole(FULL_CONTRIBUTOR.getAlias()));
		assignment.getRole().addPermission(Permission.ManageDataversePermissions);
		assignment.setDefinitionPoint(this.dataverse);
		
		when(this.roleAssignmentRepository.findByAssigneeIdentifier(anyString())).
			thenReturn(singletonList(assignment));
		
		when(this.roleAssignmentRepository.findByAssigneeIdentifiersAndDefinitionPointIds(anyList(), anyList())).
			thenReturn(singletonList(assignment));
			
		this.dataverse.setDefaultDatasetContributorRole(getRole(EDITOR.getAlias()));
		this.dataverse.setDefaultDataverseContributorRole(getRole(COLLECTION_CUSTODIAN.getAlias()));
		
		this.page.setId(1L);
		logIn(newUser(false)); // regular user
		this.page.init();
		
		assertThat(this.page.displaySettingsTab()).isTrue();
		
		DataverseDefaultSettingsTab.RoleOption anyone = 
				this.page.getSettingsTab().getDialog().getCreatorRoles().get(0);
		DataverseDefaultSettingsTab.RoleOption fullContributor = 
				this.page.getSettingsTab().getDialog().getCreatorRoles().get(3);
		
		DataverseDefaultSettingsTab.RoleOption editor = 
				this.page.getSettingsTab().getDialog().getDefaultDatasetContributorRoles().get(0);
		DataverseDefaultSettingsTab.RoleOption none = 
				this.page.getSettingsTab().getDialog().getDefaultDatasetContributorRoles().get(3);
		
		DataverseDefaultSettingsTab.RoleOption custodian = 
				this.page.getSettingsTab().getDialog().getDefaultDataverseContributorRoles().get(1);
		
		assertThat(this.page.getSettingsTab().getCreatorRole()).isEqualTo(fullContributor);
		assertThat(this.page.getSettingsTab().getDefaultDatasetContributorRole()).isEqualTo(editor);
		assertThat(this.page.getSettingsTab().getDefaultDataverseContributorRole()).isEqualTo(custodian);
		
		assertThat(this.page.getSettingsTab().getDialog().displayDefaultDataverseContributorRoleSelection()).isFalse();
		assertThat(this.page.getSettingsTab().getDialog().getCreatorRoleIndex()).isEqualTo(3);
		assertThat(this.page.getSettingsTab().getDialog().getDefaultDatasetContributorRoleIndex()).isEqualTo(0);
		assertThat(this.page.getSettingsTab().getDialog().getDefaultDataverseContributorRoleIndex()).isEqualTo(1);
		
		
		this.page.getSettingsTab().getDialog().setCreatorRoleIndex(0);
		this.page.getSettingsTab().getDialog().setDefaultDatasetContributorRoleIndex(3);
		this.page.getSettingsTab().getDialog().setDefaultDataverseContributorRoleIndex(0);
		
		
		assertThat(this.page.getSettingsTab().getCreatorRole()).isEqualTo(fullContributor);
		assertThat(this.page.getSettingsTab().getDefaultDatasetContributorRole()).isEqualTo(editor);
		assertThat(this.page.getSettingsTab().getDefaultDataverseContributorRole()).isEqualTo(custodian);
		
		this.page.getSettingsTab().getDialog().save();
		
		assertThat(this.page.getSettingsTab().getCreatorRole()).isEqualTo(anyone);
		assertThat(this.page.getSettingsTab().getDefaultDatasetContributorRole()).isEqualTo(none);
		assertThat(this.page.getSettingsTab().getDefaultDataverseContributorRole()).isEqualTo(custodian);

		
		verify(this.permissionService, times(0)).
			assignRoleWithNotification(any(), any(), any());
		verify(this.permissionService, times(1)).
			removeRoleAssignmentWithNotification(
				argThat(a -> a.getRole().equals(getRole(FULL_CONTRIBUTOR.getAlias()))));
		
		verify(this.permissionService, times(1)).
			setDefaultDatasetContributorRole(eq(null), 
					eq((Dataverse)this.page.getDvObject()));
		
		verify(this.permissionService, times(0))
			.setDefaultDataverseContributorRole(any(), any());
	}
	
	//--------------------------------------------------------------------------
	private void logIn(final AuthenticatedUser user) {
		this.session.logIn(user);
		this.requestService.setup();
	}
	
	private static Dataverse newDataverse() {
		final Dataverse dataverse = new Dataverse();
		
		dataverse.setId(23L);
		dataverse.setName("dataverse1");
		
		return dataverse;
	}
	
	private static DataverseRole newRole(final Long id, final String alias) {
		final DataverseRole role = new DataverseRole();
		
		role.setId(id);
		role.setAlias(alias);
		
		return role;
	}
	
	private static DataverseRole getRole(final String alias) {	
		return roles.stream()
			.filter(role -> role.getAlias().equals(alias))
			.findFirst()
			.get();
	}
	
	private static AuthenticatedUser newUser(final boolean superUser) {
		
		final AuthenticatedUser user = new AuthenticatedUser();
		user.setId(1L);
		user.setUserIdentifier("user1");
		user.setSuperuser(superUser);
		
		return user;
	}
	
	private RoleAssignment newManagerRoleAssinment(final AuthenticatedUser user) {
		
		final RoleAssignment assignment = new RoleAssignment();
		assignment.setId(1L);
		
		final DataverseRole role = new DataverseRole();
		role.setId(1L);
		role.setName("editRole");
		role.addPermission(Permission.ManageDataversePermissions);
		
		assignment.setRole(role);
		assignment.setAssigneeIdentifier(user.getIdentifier());
		assignment.setDefinitionPoint(this.dataverse);
		
		return assignment;
	}
}
