package edu.harvard.iq.dataverse.permission;

import static edu.harvard.iq.dataverse.common.BundleUtil.getStringFromBundle;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.sort;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.text.StringEscapeUtils.escapeHtml4;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.faces.event.ActionEvent;
import javax.inject.Inject;
import javax.inject.Named;

import org.omnifaces.cdi.ViewScoped;
import org.slf4j.Logger;

import edu.harvard.iq.dataverse.DataverseRequestServiceBean;
import edu.harvard.iq.dataverse.DataverseRoleServiceBean;
import edu.harvard.iq.dataverse.DataverseSession;
import edu.harvard.iq.dataverse.DvObjectServiceBean;
import edu.harvard.iq.dataverse.PermissionServiceBean;
import edu.harvard.iq.dataverse.PermissionsWrapper;
import edu.harvard.iq.dataverse.RoleAssigneeServiceBean;
import edu.harvard.iq.dataverse.authorization.DataverseRolePermissionHelper;
import edu.harvard.iq.dataverse.engine.command.exception.CommandException;
import edu.harvard.iq.dataverse.engine.command.exception.PermissionException;
import edu.harvard.iq.dataverse.persistence.DvObject;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.user.DataverseRole;
import edu.harvard.iq.dataverse.persistence.user.DataverseRole.BuiltInRole;
import edu.harvard.iq.dataverse.persistence.user.Permission;
import edu.harvard.iq.dataverse.persistence.user.RoleAssignee;
import edu.harvard.iq.dataverse.persistence.user.RoleAssigneeDisplayInfo;
import edu.harvard.iq.dataverse.persistence.user.RoleAssignment;
import edu.harvard.iq.dataverse.util.UIMessages;


/**
 * @author gdurand
 */
@SuppressWarnings("serial")
@ViewScoped
@Named
public class ManagePermissionsPage implements java.io.Serializable {


    private static final Logger logger = getLogger(ManagePermissionsPage.class);

    private DvObjectServiceBean dvObjectService;
    private DataverseRoleServiceBean roleService;
    private RoleAssigneeServiceBean roleAssigneeService;
    private PermissionServiceBean permissionService;
    private DataverseRequestServiceBean dvRequestService;
    private PermissionsWrapper permissionsWrapper;
    private ManagePermissionsService managePermissionsService;
    private DataverseSession session;
    
    private UIMessages ui;
    
    private DvObject dvObject;
    private Long id;
    private DataverseDefaultSettingsTab settingsTab;
    
    
    
    public ManagePermissionsPage() {}
    
    @Inject
    public ManagePermissionsPage (
    		final DvObjectServiceBean dvObjectService, 
    		final DataverseRoleServiceBean roleService,
			final RoleAssigneeServiceBean roleAssigneeService, 
			final PermissionServiceBean permissionService,
			final DataverseRequestServiceBean dvRequestService, 
			final PermissionsWrapper permissionsWrapper,
			final ManagePermissionsService managePermissionsService, 
			final DataverseSession session,
			final UIMessages ui) {

		this.dvObjectService = dvObjectService;
		this.roleService = roleService;
		this.roleAssigneeService = roleAssigneeService;
		this.permissionService = permissionService;
		this.dvRequestService = dvRequestService;
		this.permissionsWrapper = permissionsWrapper;
		this.managePermissionsService = managePermissionsService;
		this.session = session;
		this.ui = ui;
	}

    public DvObject getDvObject() {
        return dvObject;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String init() {
    	if(this.id == null) {
    		return this.permissionsWrapper.notFound();
    	}
    	
        this.dvObject = this.dvObjectService.findDvObject(this.id).orElse(null);
        if (this.dvObject == null) {
            return this.permissionsWrapper.notFound();
        }

        if (!this.permissionsWrapper.canManagePermissions(this.dvObject.getPermissionRoot())) {
            return this.permissionsWrapper.notAuthorized();
        }

        if(this.dvObject instanceof Dataset || this.dvObject instanceof DataFile) {
            final Dataset dataset = (Dataset) this.dvObject.getPermissionRoot();
            if (dataset.isInReview()
                    && !(this.permissionsWrapper.canIssuePublishDatasetCommand(dataset)
                    && this.permissionsWrapper.canManageDatasetOrMinorDatasetPermissions(dataset))) {
                return this.permissionsWrapper.notAuthorized();
            }
        }
        
        if(this.dvObject.isInstanceofDataverse()) {
        	this.settingsTab = new DataverseDefaultSettingsTab(this.roleService, 
        			this.managePermissionsService, this.roleAssigneeService, this.session, this.ui, 
        			(Dataverse) this.dvObject, this::settingsChanged);
        	
        }
        this.roleAssignments = initRoleAssignments();
        
        return EMPTY;
    }
    
    public boolean displaySettingsTab() {
    	return this.settingsTab != null;
    }
    
    public boolean displayRolesAddButton() {
    	return this.dvObject.isInstanceofDataverse() && session.isSuperUserLoggedIn();
    }
    
    public DataverseDefaultSettingsTab getSettingsTab() {
    	return this.settingsTab;
    }
    
    public String getTitle() {
    	return this.dvObject.isInstanceofDataverse()
    			? getStringFromBundle("dataverse.permissions.title")
    			: getStringFromBundle("dataverse.permissions.dataset.title") +
    				" - " + ((Dataset)this.dvObject).getLatestVersion().getTitle();
    }

    /*
     main page - role assignment table
     */

    // used by remove Role Assignment
    private RoleAssignment selectedRoleAssignment;

    public RoleAssignment getSelectedRoleAssignment() {
        return selectedRoleAssignment;
    }

    public void setSelectedRoleAssignment(RoleAssignment selectedRoleAssignment) {
        this.selectedRoleAssignment = selectedRoleAssignment;
    }

    private List<RoleAssignmentRow> roleAssignments;

    public List<RoleAssignmentRow> getRoleAssignments() {
        return roleAssignments;
    }

    public void setRoleAssignments(List<RoleAssignmentRow> roleAssignments) {
        this.roleAssignments = roleAssignments;
    }

    public List<RoleAssignmentRow> initRoleAssignments() {

        List<RoleAssignmentRow> result = emptyList();
        if (this.dvObject != null && this.dvObject.isNotNew()) {
            Set<RoleAssignment> assignments = this.roleService.rolesAssignments(this.dvObject);
            result = new ArrayList<>(assignments.size());
            for (RoleAssignment roleAssignment : assignments) {
                // for files, only show role assignments which can download
                if (!(this.dvObject instanceof DataFile) || roleAssignment.has(Permission.DownloadFile)) {
                    RoleAssignee roleAssignee = this.roleAssigneeService.getRoleAssignee(roleAssignment.getAssigneeIdentifier());
                    if (roleAssignee != null) {
                        result.add(new RoleAssignmentRow(roleAssignment, roleAssignee.getDisplayInfo()));
                    } else {
                        logger.info("Could not find role assignee based on role assignment id " + roleAssignment.getId());
                    }
                }
            }
        }
        return result;
    }

    public void removeRoleAssignment() {

    	if(this.selectedRoleAssignment != null) {
    		removeRoleAssignment(this.selectedRoleAssignment);
    	}
    	roleAssignments = initRoleAssignments();
        showAssignmentMessages();
    }
    
    private void settingsChanged() {
    	this.roleAssignments = initRoleAssignments();
    	showConfigureMessages();
    }

    /*
     main page - roles table
     */

    public List<DataverseRole> getRoles() {
        if (dvObject != null && dvObject.getId() != null) {
            return roleService.findByOwnerId(dvObject.getId());
        }
        return new ArrayList<>();
    }

    public void createNewRole() {
        DataverseRole newRole = new DataverseRole();
        newRole.setOwner(dvObject);
        
        setRole(newRole);
    }

    public void cloneRole(String roleId) {
        DataverseRole clonedRole = new DataverseRole();
        clonedRole.setOwner(dvObject);

        DataverseRole originalRole = roleService.find(Long.parseLong(roleId));
        clonedRole.addPermissions(originalRole.permissions());
        setRole(clonedRole);
    }

    public void editRole(String roleId) {
        setRole(roleService.find(Long.parseLong(roleId)));
    }

    /*
   ============================================================================
     assign roles dialog
   ============================================================================
     */
    private List<RoleAssignee> roleAssignSelectedRoleAssignees;
    private Long selectedRoleId;

    public List<RoleAssignee> getRoleAssignSelectedRoleAssignees() {
        return roleAssignSelectedRoleAssignees;
    }

    public void setRoleAssignSelectedRoleAssignees(List<RoleAssignee> selectedRoleAssignees) {
        this.roleAssignSelectedRoleAssignees = selectedRoleAssignees;
    }

    public Long getSelectedRoleId() {
        return selectedRoleId;
    }

    public void setSelectedRoleId(Long selectedRoleId) {
        this.selectedRoleId = selectedRoleId;
    }

    public void initAssigneeDialog(ActionEvent ae) {
        roleAssignSelectedRoleAssignees = new LinkedList<>();
        selectedRoleId = null;
        showNoMessages();
    }

    public List<RoleAssignee> completeRoleAssignee(String query) {
        return roleAssigneeService.filterRoleAssignees(query, dvObject, roleAssignSelectedRoleAssignees);
    }

    public List<DataverseRole> getAvailableRoles() {
        List<DataverseRole> roles = new LinkedList<>();
        if (dvObject != null && dvObject.getId() != null) {

            if (dvObject instanceof Dataverse) {
                roles.addAll(roleService.availableRoles(dvObject.getId()));

            } else if (dvObject instanceof Dataset) {
                // don't show roles that only have Dataverse level permissions
                // current the available roles for a dataset are gotten from its parent
                for (DataverseRole role : roleService.availableRoles(dvObject.getOwner().getId())) {
                    for (Permission permission : role.permissions()) {
                        if (permission.appliesTo(Dataset.class) || permission.appliesTo(DataFile.class)) {
                            if (isHasPermission(Permission.ManageMinorDatasetPermissions)
                                    && isAllowedToManageRole(role) || isHasPermission(Permission.ManageDatasetPermissions)) {
                                roles.add(role);
                            }
                            break;
                        }
                    }
                }

            } else if (dvObject instanceof DataFile) {
                roles.add(roleService.findBuiltinRoleByAlias(BuiltInRole.FILE_DOWNLOADER));
            }

            sort(roles, DataverseRole.compareByName);
        }
        return roles;
    }

    public DataverseRole getAssignedRole() {
        if (selectedRoleId != null) {
            return roleService.find(selectedRoleId);
        }
        return null;
    }

    public void assignRole(ActionEvent evt) {
        logger.info("Got to assignRole");
        List<RoleAssignee> selectedRoleAssigneesList = getRoleAssignSelectedRoleAssignees();
        if (selectedRoleAssigneesList == null) {
            logger.info("** SELECTED role asignees is null");
            selectedRoleAssigneesList = new LinkedList<>();
        }
        for (RoleAssignee roleAssignee : selectedRoleAssigneesList) {
            assignRole(roleAssignee, roleService.find(selectedRoleId));
        }
        roleAssignments = initRoleAssignments();
    }

 
   private void assignRole(final RoleAssignee assignee, final DataverseRole role) {
    	
        final Object[] messageArgs = {
                role.getName(),
                assignee.getDisplayInfo().getTitle(),
                escapeHtml4(this.dvObject.getDisplayName())
        };
        
        try {
        	this.managePermissionsService.assignRoleWithNotification(role, assignee, 
        			this.dvObject);
        	this.ui.addFlashSuccessMessage(
        			getStringFromBundle("permission.roleAssignedToFor", messageArgs));
        } catch(final PermissionException e) {
            this.ui.addErrorMessage(
                    getStringFromBundle("permission.roleNotAbleToBeAssigned"),
                    getStringFromBundle("permission.permissionsMissing",
                            e.getMissingPermissions().toString()));
        } catch(final CommandException e) {
            this.ui.addErrorMessage(
            		getStringFromBundle("permission.roleNotAssignedFor", messageArgs));
            logger.error("Error assiging role: " + e.getMessage(), e);
        }

        showAssignmentMessages();
    }
   
   private void removeRoleAssignment(final RoleAssignment assignment) {
   	try {
   		this.managePermissionsService.removeRoleAssignmentWithNotification(assignment);
   		this.ui.addFlashSuccessMessage(
   				getStringFromBundle("permission.roleWasRemoved",
           		assignment.getRole().getName(), this.roleAssigneeService.getRoleAssignee(
            			assignment.getAssigneeIdentifier()).getDisplayInfo().getTitle()));
   	} catch(final PermissionException e) {
   		this.ui.addErrorMessage(
   				getStringFromBundle("permission.roleNotAbleToBeRemoved"),
                   getStringFromBundle("permission.permissionsMissing",
                           e.getMissingPermissions().toString()));
   		throw e;
   	} catch(final CommandException e) {
   		this.ui.addErrorMessage(
   				getStringFromBundle("permission.roleNotAbleToBeRemoved"));
           logger.error("Error removing role assignment: " + e.getMessage(), e);
           throw e;
   	}
   }

    private boolean isAllowedToManageRole(DataverseRole role) {
        return DataverseRolePermissionHelper.getRolesAllowedToBeAssignedByManageMinorDatasetPermissions().contains(role.getAlias());
    }

    private boolean isHasPermission(Permission manageMinorDatasetPermissions) {
        return permissionService.userOn(dvRequestService.getDataverseRequest().getUser(), this.dvObject)
                .has(manageMinorDatasetPermissions);
    }

    /*
    ============================================================================
     edit role dialog
    ============================================================================
    */
    private DataverseRole role = new DataverseRole();
    private List<String> selectedPermissions;

    public DataverseRole getRole() {
        return role;
    }

    public void setRole(DataverseRole role) {
        this.role = role;
        selectedPermissions = new LinkedList<>();
        if (role != null) {
            for (Permission p : role.permissions()) {
                selectedPermissions.add(p.name());
            }
        }
    }

    public List<String> getSelectedPermissions() {
        return selectedPermissions;
    }

    public void setSelectedPermissions(List<String> selectedPermissions) {
        this.selectedPermissions = selectedPermissions;
    }

    public List<Permission> getPermissions() {
        return asList(Permission.values());
    }

    public void updateRole(ActionEvent event) {
        // @todo currently only works for Dataverse since CreateRoleCommand only takes a dataverse
        // we need to decide if we want roles at the dataset level or not
        if (dvObject instanceof Dataverse) {
            boolean isCreateRoleAction = role.getId() == null;
            role.clearPermissions();
            for (String pmsnStr : getSelectedPermissions()) {
                role.addPermission(Permission.valueOf(pmsnStr));
            }
            try {
            	 final DataverseRole modifiedRole = this.managePermissionsService.saveOrUpdateRole(role);
            	 setRole(modifiedRole);
            	 String roleState = isCreateRoleAction 
                         ? getStringFromBundle("permission.created") 
                         : getStringFromBundle("permission.updated");
                 this.ui.addFlashSuccessMessage(getStringFromBundle("permission.roleWas", roleState));      
        } catch (final PermissionException e) {
                this.ui.addErrorMessage(
                        getStringFromBundle("permission.roleNotSaved"),
                        getStringFromBundle("permission.permissionsMissing",
                                e.getMissingPermissions().toString()));
            } catch (final CommandException e) {
                this.ui.addErrorMessage(getStringFromBundle("permission.roleNotSaved"));
                logger.error("Error saving role: " + e.getMessage(), e);
            }
        }
        showRoleMessages();
    }

    /*
    ============================================================================
    Internal methods
    ============================================================================
    */

    private boolean renderConfigureMessages = false;
    private boolean renderAssignmentMessages = false;
    private boolean renderRoleMessages = false;

    private void showNoMessages() {
        renderConfigureMessages = false;
        renderAssignmentMessages = false;
        renderRoleMessages = false;
    }

    private void showConfigureMessages() {
        renderConfigureMessages = true;
        renderAssignmentMessages = false;
        renderRoleMessages = false;
    }

    private void showAssignmentMessages() {
        renderConfigureMessages = false;
        renderAssignmentMessages = true;
        renderRoleMessages = false;
    }

    private void showRoleMessages() {
        renderConfigureMessages = false;
        renderAssignmentMessages = false;
        renderRoleMessages = true;
    }

    public Boolean getRenderConfigureMessages() {
        return renderConfigureMessages;
    }

    public void setRenderConfigureMessages(Boolean renderConfigureMessages) {
        this.renderConfigureMessages = renderConfigureMessages;
    }

    public Boolean getRenderAssignmentMessages() {
        return renderAssignmentMessages;
    }

    public void setRenderAssignmentMessages(Boolean renderAssignmentMessages) {
        this.renderAssignmentMessages = renderAssignmentMessages;
    }

    public Boolean getRenderRoleMessages() {
        return renderRoleMessages;
    }

    public void setRenderRoleMessages(Boolean renderRoleMessages) {
        this.renderRoleMessages = renderRoleMessages;
    }

    // inner class used for display of role assignments
    public static class RoleAssignmentRow {

        private final RoleAssigneeDisplayInfo assigneeDisplayInfo;
        private final RoleAssignment ra;

        RoleAssignmentRow(RoleAssignment anRa, RoleAssigneeDisplayInfo disInf) {
            ra = anRa;
            assigneeDisplayInfo = disInf;
        }

        public RoleAssignment getRoleAssignment() {
            return ra;
        }

        public RoleAssigneeDisplayInfo getAssigneeDisplayInfo() {
            return assigneeDisplayInfo;
        }

        public DataverseRole getRole() {
            return ra.getRole();
        }

        public String getRoleName() {
            return getRole().getName();
        }


        public DvObject getDefinitionPoint() {
            return ra.getDefinitionPoint();
        }

        public String getAssignedDvName() {
            return ra.getDefinitionPoint().getDisplayName();
        }

        public Long getId() {
            return ra.getId();
        }
    }
}
