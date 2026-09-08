package edu.harvard.iq.dataverse.permission;

import static edu.harvard.iq.dataverse.common.BundleUtil.getStringFromBundle;
import static edu.harvard.iq.dataverse.persistence.user.DataverseRole.BuiltInRole.ADMIN;
import static edu.harvard.iq.dataverse.persistence.user.DataverseRole.BuiltInRole.COLLECTION_CUSTODIAN;
import static edu.harvard.iq.dataverse.persistence.user.DataverseRole.BuiltInRole.CURATOR;
import static edu.harvard.iq.dataverse.persistence.user.DataverseRole.BuiltInRole.DEPOSITOR;
import static edu.harvard.iq.dataverse.persistence.user.DataverseRole.BuiltInRole.DS_CONTRIBUTOR;
import static edu.harvard.iq.dataverse.persistence.user.DataverseRole.BuiltInRole.DV_CONTRIBUTOR;
import static edu.harvard.iq.dataverse.persistence.user.DataverseRole.BuiltInRole.EDITOR;
import static edu.harvard.iq.dataverse.persistence.user.DataverseRole.BuiltInRole.FULL_CONTRIBUTOR;
import static java.util.Arrays.asList;
import static org.apache.commons.text.StringEscapeUtils.escapeHtml4;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;

import edu.harvard.iq.dataverse.DataverseRoleServiceBean;
import edu.harvard.iq.dataverse.DataverseSession;
import edu.harvard.iq.dataverse.RoleAssigneeServiceBean;
import edu.harvard.iq.dataverse.engine.command.exception.CommandException;
import edu.harvard.iq.dataverse.engine.command.exception.PermissionException;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.group.AuthenticatedUsers;
import edu.harvard.iq.dataverse.persistence.user.DataverseRole;
import edu.harvard.iq.dataverse.persistence.user.RoleAssignee;
import edu.harvard.iq.dataverse.persistence.user.RoleAssignment;
import edu.harvard.iq.dataverse.util.UIMessages;

public final class DataverseDefaultSettingsTab {
	
	//--------------------------------------------------------------------------
	public final static class RoleOption {

	    private final DataverseRole role;
	    private final String label;
	    private final String details;

	    public RoleOption(final DataverseRole role, final String label, 
	    		final String details) {
	        this.role = role;
	        this.label = label;
	        this.details = details;
	    }

	    public DataverseRole getRole() {
	        return this.role;
	    }

	    public String getLabel() {
	        return this.label;
	    }
	    
	    public String getDetails() {
	    	return this.details;
	    }
	    
	    public String getLabelWithDetails() {
	    	return this.label + ' ' + this.details;
	    }
	    
	    @Override
	    public String toString() {
	    	return this.label;
	    }
	}
	//--------------------------------------------------------------------------
    public final class PermissionsConfigureDialog {
    		
    	private int creatorRoleIndex;
        private int defaultDatasetContributorRoleIndex;     
        private int defaultDataverseContributorRoleIndex;    
        
        public PermissionsConfigureDialog(final int creatorRoleIndex, 
        		final int defaultDatasetContributorRoleIndex, 
        		final int defaultDataverseContributorRoleIndex) {
			this.creatorRoleIndex = creatorRoleIndex;
			this.defaultDatasetContributorRoleIndex = defaultDatasetContributorRoleIndex;
			this.defaultDataverseContributorRoleIndex = defaultDataverseContributorRoleIndex;
		}

		public List<RoleOption> getCreatorRoles() {
        	return creatorRoles;
        }
		
		public List<RoleOption> getDefaultDatasetContributorRoles() {
        	return defaultDatasetContributorRoles;
        }
		
		public List<RoleOption> getDefaultDataverseContributorRoles() {
        	return defaultDataverseContributorRoles;
        }
    	
        public int getCreatorRoleIndex() {
            return this.creatorRoleIndex;
        }

        public void setCreatorRoleIndex(final int index) {
            this.creatorRoleIndex = index;
        }
 
		public int getDefaultDatasetContributorRoleIndex() {
			return this.defaultDatasetContributorRoleIndex;
		}

		public void setDefaultDatasetContributorRoleIndex(final int index) {
			this.defaultDatasetContributorRoleIndex = index;
		}
		
		public boolean displayDefaultDataverseContributorRoleSelection() {
			return session.isSuperUserLoggedIn();
		}
		
		public int getDefaultDataverseContributorRoleIndex() {
			return this.defaultDataverseContributorRoleIndex;
		}

		public void setDefaultDataverseContributorRoleIndex(final int index) {
			this.defaultDataverseContributorRoleIndex = index;
		}
		
		public void save() {
			saveConfiguration(creatorRoles.get(this.creatorRoleIndex),
					defaultDatasetContributorRoles.get(this.defaultDatasetContributorRoleIndex),
					defaultDataverseContributorRoles.get(this.defaultDataverseContributorRoleIndex));
		}
    }
    //--------------------------------------------------------------------------
    private static final Logger logger = getLogger(DataverseDefaultSettingsTab.class);

    private final DataverseRoleServiceBean roleService;
    private final ManagePermissionsService permissionsService;
    private final RoleAssigneeServiceBean roleAssigneeService;
    private final DataverseSession session;
    private final UIMessages ui;
    private final PermissionsConfigureDialog dialog; 
    private final Dataverse dataverse;
    private final Runnable configurationChangeListener;
    private final List<RoleOption> creatorRoles;
    private final List<RoleOption> defaultDatasetContributorRoles = new ArrayList<>(4);
    private final List<RoleOption> defaultDataverseContributorRoles;

    private RoleOption creatorRole;
    private RoleOption defaultDatasetContributorRole;
    private RoleOption defaultDataverseContributorRole;
    
    DataverseDefaultSettingsTab(final DataverseRoleServiceBean roleService, 
    		final ManagePermissionsService permissionsService,
    		final RoleAssigneeServiceBean roleAssigneeService,
    		final DataverseSession session,
    		final UIMessages ui, 
    		final Dataverse dataverse, 
    		final Runnable configurationChangListener) {
    	
    	this.roleService = roleService;
    	this.permissionsService = permissionsService;
    	this.roleAssigneeService = roleAssigneeService;
    	this.session = session;
    	this.ui = ui;
		this.dataverse = dataverse;
		this.configurationChangeListener = configurationChangListener;
		
		this.creatorRoles = asList(
	            new RoleOption(null, 
	            				getStringFromBundle("dataverse.permissions.Q1.answer1"), ""),
	            new RoleOption(this.roleService.findBuiltinRoleByAlias(DV_CONTRIBUTOR),
	            				getStringFromBundle("dataverse.permissions.Q1.answer2"), ""),
	            new RoleOption(this.roleService.findBuiltinRoleByAlias(DS_CONTRIBUTOR),
	            				getStringFromBundle("dataverse.permissions.Q1.answer3"), ""),
	            new RoleOption(this.roleService.findBuiltinRoleByAlias(FULL_CONTRIBUTOR),
	            				getStringFromBundle("dataverse.permissions.Q1.answer4"), ""));
		
		final List<RoleAssignment> assignments = this.roleService.
				directRoleAssignments(AuthenticatedUsers.get(), this.dataverse);
		this.creatorRole = assignments.size() > 0 
				? roleOptionByRole(this.creatorRoles, assignments.get(0).getRole())
				: this.creatorRoles.get(0);
        
		this.defaultDatasetContributorRoles.add(
				new RoleOption(this.roleService.findBuiltinRoleByAlias(EDITOR), 
								getStringFromBundle("editor"),
								getStringFromBundle("dataverse.permissions.Q2.answer.editor.description")));
		this.defaultDatasetContributorRoles.add(
				new RoleOption(this.roleService.findBuiltinRoleByAlias(CURATOR), 
								getStringFromBundle("curator"),
								getStringFromBundle("dataverse.permissions.Q2.answer.curator.description")));
		this.defaultDatasetContributorRoles.add(
				new RoleOption(this.roleService.findBuiltinRoleByAlias(DEPOSITOR), 
								getStringFromBundle("depositor"),
								getStringFromBundle("dataverse.permissions.Q2.answer.depositor.description")));
		this.defaultDatasetContributorRoles.add(
				new RoleOption(null, 
								getStringFromBundle("permission.default.contributor.role.none.name"),
								getStringFromBundle("permission.default.contributor.role.none.description")));
		final DataverseRole role = this.dataverse.getDefaultDatasetContributorRole();
		if(isCustom(role)) {
			this.defaultDatasetContributorRoles.add(3, 
					new RoleOption(role,
								escapeHtml4(role.getName()),
								escapeHtml4(role.getDescription())));
		}
		
		this.defaultDatasetContributorRole = roleOptionByRole(this.defaultDatasetContributorRoles, role);
		
		this.defaultDataverseContributorRoles = asList(
	            new RoleOption(this.roleService.findBuiltinRoleByAlias(ADMIN),
	            				getStringFromBundle("dataverse.permissions.Q3.answer1"), ""),
	            new RoleOption(this.roleService.findBuiltinRoleByAlias(COLLECTION_CUSTODIAN),
	            				getStringFromBundle("dataverse.permissions.Q3.answer2"), ""));
		
		this.defaultDataverseContributorRole = 
				this.dataverse.getDefaultDataverseContributorRole() != null
					? roleOptionByRole(this.defaultDataverseContributorRoles, 
							this.dataverse.getDefaultDataverseContributorRole())
					: this.defaultDataverseContributorRoles.get(0);
        
        this.dialog = new PermissionsConfigureDialog( 
        	this.creatorRoles.indexOf(this.creatorRole),
        	this.defaultDatasetContributorRoles.indexOf(this.defaultDatasetContributorRole),
        	this.defaultDataverseContributorRoles.indexOf(this.defaultDataverseContributorRole));
	}

    public RoleOption getCreatorRole() {
    	return this.creatorRole;
    }
    
    public RoleOption getDefaultDatasetContributorRole() {
        return this.defaultDatasetContributorRole;
    }
    
    public RoleOption getDefaultDataverseContributorRole() {
        return this.defaultDataverseContributorRole;
    }
    
    public PermissionsConfigureDialog getDialog() {
    	return this.dialog;
    }
    
    public void resetDialog() {
        this.dialog.setCreatorRoleIndex(this.creatorRoles.indexOf(this.creatorRole));
        this.dialog.setDefaultDatasetContributorRoleIndex(
        		this.defaultDatasetContributorRoles.indexOf(this.defaultDatasetContributorRole));
        this.dialog.setDefaultDataverseContributorRoleIndex(
        		this.defaultDataverseContributorRoles.indexOf(this.defaultDataverseContributorRole));
    }
    
    private boolean isCustom(final DataverseRole role) {
    	return !this.defaultDatasetContributorRoles
    			.stream()
    			.map(RoleOption::getRole)
    			.anyMatch(r -> Objects.equals(r, role));
    }
    
    private RoleOption roleOptionByRole(final List<RoleOption> roles, final DataverseRole role) {
    	return roles.stream()
    			.filter(ro -> Objects.equals(ro.getRole(), role))
    			.findFirst()
    			.orElse(null);
    }
    
    private List<RoleAssignment> directRoleAssignmentsForObject() {
    	return this.roleService.directRoleAssignments(AuthenticatedUsers.get(), 
    			this.dataverse);
    }
    
    private void saveConfiguration(final RoleOption selectedCreatorRole, 
    		final RoleOption selectedDatasetContributorRole, 
    		final RoleOption selectedDataverseContributorRole) {
    	
    	try {
	        // Set role (if any) for authenticatedUsers
	        DataverseRole roleToAssign = selectedCreatorRole.getRole();
	
	        // then, check current contributor role
	        for (final RoleAssignment assignment : directRoleAssignmentsForObject()) {
	            if (assignment.getRole().isContributor()) {
	                if (assignment.getRole().equals(roleToAssign)) {
	                    roleToAssign = null; // found the role, so no need to assign
	                } else {
	                    removeRoleAssignment(assignment);
	                }
	            } 
	        }
	        // finally, assign role, if new
	        if (roleToAssign != null) {
	            assignRole(AuthenticatedUsers.get(), roleToAssign);
	        }
	
	        setDefaultDatasetContributorRole(selectedDatasetContributorRole.getRole());
	        
	        if(this.session.isSuperUserLoggedIn()) {
	        	setDefaultDataverseContributorRole(selectedDataverseContributorRole.getRole());
	        	this.defaultDataverseContributorRole = selectedDataverseContributorRole;
	        }
	        
			this.creatorRole = selectedCreatorRole;
			this.defaultDatasetContributorRole = selectedDatasetContributorRole;
	
	        this.configurationChangeListener.run();
    	} catch(final Exception e) {
    		logger.warn("Permission modification failure.", e);
    	}
    }

	private void setDefaultDatasetContributorRole(final DataverseRole role) {
		try {
			this.permissionsService.setDefaultDatasetContributorRole(role, this.dataverse);
			this.ui.addFlashSuccessMessage(
					getStringFromBundle("permission.defaultPermissionDataverseUpdated"));
		} catch(final PermissionException e) {
		    this.ui.addErrorMessage(
		    		getStringFromBundle("permission.CannotAssigntDefaultPermissions"),
		            getStringFromBundle("permission.permissionsMissing",
		                    e.getMissingPermissions().toString()));
		    throw e;
		} catch(final CommandException e) {
		    this.ui.addErrorMessage(
		    		getStringFromBundle("permission.CannotAssigntDefaultPermissions"));
		    logger.error("Error assigning default permissions: " + e.getMessage(), e);
		    throw e;
		}
	}
	
	private void setDefaultDataverseContributorRole(final DataverseRole role) {
		try {
			this.permissionsService.setDefaultDataverseContributorRole(role, this.dataverse);
			this.ui.addFlashSuccessMessage(
					getStringFromBundle("permission.defaultPermissionDataverseUpdated"));
		} catch(final PermissionException e) {
		    this.ui.addErrorMessage(
		    		getStringFromBundle("permission.CannotAssigntDefaultPermissions"),
		            getStringFromBundle("permission.permissionsMissing",
		                    e.getMissingPermissions().toString()));
		    throw e;
		} catch(final CommandException e) {
		    this.ui.addErrorMessage(
		    		getStringFromBundle("permission.CannotAssigntDefaultPermissions"));
		    logger.error("Error assigning default permissions: " + e.getMessage(), e);
		    throw e;
		}
	}
    
   private void assignRole(final RoleAssignee assignee, final DataverseRole role) {
    	
        final Object[] messageArgs = {
                role.getName(),
                assignee.getDisplayInfo().getTitle(),
                escapeHtml4(this.dataverse.getDisplayName())
        };
        
        try {
        	this.permissionsService.assignRoleWithNotification(role, assignee, 
        			this.dataverse);
        	this.ui.addFlashSuccessMessage(
        			getStringFromBundle("permission.roleAssignedToFor", messageArgs));
        } catch(final PermissionException e) {
            this.ui.addErrorMessage(
                    getStringFromBundle("permission.roleNotAbleToBeAssigned"),
                    getStringFromBundle("permission.permissionsMissing",
                            e.getMissingPermissions().toString()));
            throw e;
        } catch(final CommandException e) {
            this.ui.addErrorMessage(
            		getStringFromBundle("permission.roleNotAssignedFor", messageArgs));
            logger.error("Error assiging role: " + e.getMessage(), e);
            throw e;
        }
    }
    
    private void removeRoleAssignment(final RoleAssignment assignment) {
    	try {
    		this.permissionsService.removeRoleAssignmentWithNotification(assignment);
    		this.ui.addFlashSuccessMessage(
    				getStringFromBundle("permission.roleWasRemoved",
            		assignment.getRole().getName(), getAssigneeTitle(assignment)));
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
    
    private String getAssigneeTitle(final RoleAssignment assignment) {
    	return this.roleAssigneeService.getRoleAssignee(
    			assignment.getAssigneeIdentifier()).getDisplayInfo().getTitle();
    }
}
