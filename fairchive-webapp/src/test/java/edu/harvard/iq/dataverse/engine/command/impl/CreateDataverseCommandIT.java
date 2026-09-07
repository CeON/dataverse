package edu.harvard.iq.dataverse.engine.command.impl;

import static edu.harvard.iq.dataverse.persistence.dataverse.Dataverse.DataverseType.DEPARTMENT;
import static edu.harvard.iq.dataverse.persistence.user.DataverseRole.BuiltInRole.ADMIN;
import static edu.harvard.iq.dataverse.persistence.user.DataverseRole.BuiltInRole.COLLECTION_CUSTODIAN;
import static edu.harvard.iq.dataverse.persistence.user.DataverseRole.BuiltInRole.DEPOSITOR;
import static edu.harvard.iq.dataverse.persistence.user.DataverseRole.BuiltInRole.DS_CONTRIBUTOR;
import static edu.harvard.iq.dataverse.persistence.user.DataverseRole.BuiltInRole.EDITOR;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import org.jboss.arquillian.transaction.api.annotation.TransactionMode;
import org.jboss.arquillian.transaction.api.annotation.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.harvard.iq.dataverse.DataverseRoleServiceBean;
import edu.harvard.iq.dataverse.DataverseSession;
import edu.harvard.iq.dataverse.EjbDataverseEngine;
import edu.harvard.iq.dataverse.arquillian.arquillianexamples.WebappArquillianDeployment;
import edu.harvard.iq.dataverse.engine.command.DataverseRequest;
import edu.harvard.iq.dataverse.permission.ManagePermissionsService;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.dataverse.DataverseContact;
import edu.harvard.iq.dataverse.persistence.dataverse.DataverseRepository;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUserRepository;
import edu.harvard.iq.dataverse.persistence.user.RoleAssignment;

public class CreateDataverseCommandIT  extends WebappArquillianDeployment {
	
	@Inject
	private EjbDataverseEngine engine;
	@Inject
	private AuthenticatedUserRepository userRepository;
	@Inject
	private DataverseRepository dataverseRepository;
	@Inject
	private DataverseRoleServiceBean rolesService;
	@Inject 
	private ManagePermissionsService permissionsService;
	@Inject
	private DataverseSession session;
	
	private AuthenticatedUser admin;
	private AuthenticatedUser fileDownloader;
	private Dataverse owner;
	private Dataverse dataverse;
	
	@BeforeEach
	private void setUp() {
		
		this.admin = this.userRepository.findByIdentifier("dataverseAdmin").get();
		this.fileDownloader = this.userRepository.findByIdentifier("filedownloader").get();
		this.session.logIn(this.admin);
		this.owner = this.dataverseRepository.findById(68L).get();
		
		assertThat(this.rolesService.directRoleAssignments(this.owner)).isEmpty();
		
		assertThat(this.owner.getDefaultDatasetContributorRole().getAlias()).
			isEqualTo(EDITOR.getAlias());
		assertThat(this.owner.getDefaultDataverseContributorRole()).isNull();
		
		this.dataverse = new Dataverse();
		this.dataverse.setOwner(owner);
		this.dataverse.setName("test1");
		this.dataverse.setAlias(this.dataverse.getName());
		this.dataverse.setDataverseType(DEPARTMENT);
		dataverse.getDataverseContacts().add(new DataverseContact(this.dataverse, 
				"abc@mail.com"));
	}
	
	@AfterEach
	private void tearDown() {
		this.session.logOut();
	}
	
	@Test
	@Transactional(TransactionMode.ROLLBACK)
	void subXCllectionsHaveDefaultRoleAssignments_forVanillaOwner() throws Throwable {
		
		CreateDataverseCommand command = new CreateDataverseCommand(this.dataverse, 
				newRequest(), null, null);
		
		this.engine.submit(command);
		
		final List<RoleAssignment> assignedRoles = 
				this.rolesService.directRoleAssignments(this.dataverse);
		
		assertThat(assignedRoles).hasSize(1);
		assertThat(assignedRoles).anyMatch(
				assignment -> assignment.getAssigneeIdentifier().equals("@dataverseAdmin")
							&& assignment.getRole().getAlias().equals("admin"));
		
		assertThat(this.dataverse.getDefaultDatasetContributorRole().getAlias()).
			isEqualTo(EDITOR.getAlias());
		assertThat(this.dataverse.getDefaultDataverseContributorRole().getAlias()).
			isEqualTo(ADMIN.getAlias());
	}
	
	@Test
	@Transactional(TransactionMode.ROLLBACK)
	void subCollectionsHaveRoleAssignments_forModifiedOwner() throws Throwable {
		
		this.owner.setDefaultDataverseContributorRole(
				this.rolesService.findBuiltinRoleByAlias(COLLECTION_CUSTODIAN));
		
		this.permissionsService.assignRoleWithNotification(
				this.rolesService.findBuiltinRoleByAlias(ADMIN),
				this.fileDownloader, this.owner);
		
		this.dataverseRepository.save(this.owner);
		
		CreateDataverseCommand command = new CreateDataverseCommand(this.dataverse, 
				newRequest(), null, null);
		
		this.engine.submit(command);
		
		final List<RoleAssignment> assignedRoles = 
				this.rolesService.directRoleAssignments(this.dataverse);
		
		assertThat(assignedRoles).hasSize(3);
		assertThat(assignedRoles).anyMatch(
				assignment -> assignment.getAssigneeIdentifier().equals("@dataverseAdmin")
							&& assignment.getRole().getAlias().equals(COLLECTION_CUSTODIAN.getAlias()));
		assertThat(assignedRoles).anyMatch(
				assignment -> assignment.getAssigneeIdentifier().equals(":authenticated-users")
							&& assignment.getRole().getAlias().equals(DS_CONTRIBUTOR.getAlias()));
		assertThat(assignedRoles).anyMatch(
				assignment -> assignment.getAssigneeIdentifier().equals("@filedownloader")
							&& assignment.getRole().getAlias().equals(ADMIN.getAlias()));
		
		
		assertThat(this.dataverse.getDefaultDatasetContributorRole().getAlias()).
			isEqualTo(DEPOSITOR.getAlias());
		assertThat(this.dataverse.getDefaultDataverseContributorRole().getAlias()).
			isEqualTo(COLLECTION_CUSTODIAN.getAlias());
	}
	
	@Test
	@Transactional(TransactionMode.ROLLBACK)
	void subCollectionsHaveNoAdminRolesAssigned_forNoCollectionCustodian() throws Throwable {

		this.permissionsService.assignRoleWithNotification(
				this.rolesService.findBuiltinRoleByAlias(ADMIN),
				this.fileDownloader, this.owner);
		
		this.dataverseRepository.save(this.owner);
		
		CreateDataverseCommand command = new CreateDataverseCommand(this.dataverse, 
				newRequest(), null, null);
		
		this.engine.submit(command);
		
		final List<RoleAssignment> assignedRoles = 
				this.rolesService.directRoleAssignments(this.dataverse);
		
		assertThat(assignedRoles).hasSize(1);
		assertThat(assignedRoles).anyMatch(
				assignment -> assignment.getAssigneeIdentifier().equals("@dataverseAdmin")
							&& assignment.getRole().getAlias().equals(ADMIN.getAlias()));
		
		assertThat(this.dataverse.getDefaultDatasetContributorRole().getAlias()).
			isEqualTo(EDITOR.getAlias());
		assertThat(this.dataverse.getDefaultDataverseContributorRole().getAlias()).
			isEqualTo(ADMIN.getAlias());
	}
	
	private DataverseRequest newRequest() {
		
		final HttpServletRequest httpRequest = null;
		return  new DataverseRequest(this.admin, httpRequest);
	}
}
