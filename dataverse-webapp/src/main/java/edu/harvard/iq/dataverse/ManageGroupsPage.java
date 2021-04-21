package edu.harvard.iq.dataverse;

import edu.harvard.iq.dataverse.authorization.groups.impl.explicit.ExplicitGroupServiceBean;
import edu.harvard.iq.dataverse.common.BundleUtil;
import edu.harvard.iq.dataverse.engine.command.impl.CreateExplicitGroupCommand;
import edu.harvard.iq.dataverse.engine.command.impl.DeleteExplicitGroupCommand;
import edu.harvard.iq.dataverse.engine.command.impl.UpdateExplicitGroupCommand;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.group.ExplicitGroup;
import edu.harvard.iq.dataverse.persistence.group.Group;
import edu.harvard.iq.dataverse.persistence.user.RoleAssignee;
import edu.harvard.iq.dataverse.persistence.user.User;
import edu.harvard.iq.dataverse.util.JsfHelper;
import io.vavr.control.Try;
import org.apache.commons.lang.StringUtils;
import javax.faces.view.ViewScoped;

import javax.faces.application.FacesMessage;
import javax.faces.component.UIComponent;
import javax.faces.component.UIInput;
import javax.faces.context.FacesContext;
import javax.faces.event.ActionEvent;
import javax.inject.Inject;
import javax.inject.Named;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * @author michaelsuo
 */
@ViewScoped
@Named
public class ManageGroupsPage implements java.io.Serializable {

    private static final Logger logger = Logger.getLogger(ManageGroupsPage.class.getCanonicalName());

    private DataverseDao dataverseDao;
    private ExplicitGroupServiceBean explicitGroupService;
    private RoleAssigneeServiceBean roleAssigneeService;
    private PermissionsWrapper permissionsWrapper;
    private ManageGroupsCRUDService mgCrudService;


    private List<ExplicitGroup> explicitGroups;
    private Dataverse dataverse;
    private Long dataverseId;
    private ExplicitGroup selectedGroup;
    private List<RoleAssignee> selectedGroupRoleAssignees = new ArrayList<>();
    private List<RoleAssignee> selectedGroupAddRoleAssignees;

    /*
    ============================================================================
    Explicit Group dialogs
    ============================================================================
    */
    private String explicitGroupIdentifier = "";
    private String explicitGroupName = "";
    private String newExplicitGroupDescription = "";
    private List<RoleAssignee> newExplicitGroupRoleAssignees = new LinkedList<>();

    // -------------------- CONSTRUCTORS --------------------
    @Deprecated
    public ManageGroupsPage() {
    }

    @Inject
    public ManageGroupsPage(DataverseDao dataverseDao, ExplicitGroupServiceBean explicitGroupService,
                            RoleAssigneeServiceBean roleAssigneeService, PermissionsWrapper permissionsWrapper,
                            ManageGroupsCRUDService mgCrudService) {
        this.dataverseDao = dataverseDao;
        this.explicitGroupService = explicitGroupService;
        this.roleAssigneeService = roleAssigneeService;
        this.permissionsWrapper = permissionsWrapper;
        this.mgCrudService = mgCrudService;
    }

    // -------------------- GETTERS --------------------
    public List<ExplicitGroup> getExplicitGroups() {
        return explicitGroups;
    }

    public Dataverse getDataverse() {
        return dataverse;
    }

    public Long getDataverseId() {
        return dataverseId;
    }

    public Group getSelectedGroup() {
        return selectedGroup;
    }

    public List<RoleAssignee> getSelectedGroupRoleAssignees() {
        return this.selectedGroupRoleAssignees;
    }

    public List<RoleAssignee> getSelectedGroupAddRoleAssignees() {
        return this.selectedGroupAddRoleAssignees;
    }

    public String getExplicitGroupName() {
        return explicitGroupName;
    }

    public String getExplicitGroupIdentifier() {
        return explicitGroupIdentifier;
    }

    public List<RoleAssignee> getNewExplicitGroupRoleAssignees() {
        return newExplicitGroupRoleAssignees;
    }

    public String getNewExplicitGroupDescription() {
        return newExplicitGroupDescription;
    }

    // -------------------- LOGIC --------------------
    public String init() {
        setDataverse(dataverseDao.find(getDataverseId()));
        Dataverse editDv = getDataverse();

        if (editDv == null) {
            return permissionsWrapper.notFound();
        }

        Boolean hasPermissions = permissionsWrapper.canIssueCommand(editDv, CreateExplicitGroupCommand.class);
        hasPermissions |= permissionsWrapper.canIssueCommand(editDv, DeleteExplicitGroupCommand.class);
        hasPermissions |= permissionsWrapper.canIssueCommand(editDv, UpdateExplicitGroupCommand.class);
        if (!hasPermissions) {
            return permissionsWrapper.notAuthorized();
        }
        explicitGroups = new LinkedList<>(explicitGroupService.findByOwner(getDataverseId()));
        selectedGroup = null;

        return null;
    }

    public void deleteGroup() {
        if (selectedGroup != null) {
            Try
                    .run(() -> mgCrudService.delete(selectedGroup))
                    .andThen(() -> {
                        explicitGroups.remove(selectedGroup);
                        JsfHelper.addSuccessMessage(BundleUtil.getStringFromBundle("dataverse.manageGroups.delete"));
                    })
                    .onFailure(throwable -> {
                        String failMessage = BundleUtil.getStringFromBundle("dataverse.manageGroups.nodelete");
                        JsfHelper.addErrorMessage(failMessage);
                    })
            ;
        } else {
            logger.info("Selected group is null");
        }
    }

    public void viewSelectedGroup(ExplicitGroup selectedGroup) {
        this.selectedGroup = explicitGroupService.findByAlias(selectedGroup.getAlias());

        // initialize member list for autocomplete interface
        setSelectedGroupAddRoleAssignees(new LinkedList<>());
        setSelectedGroupRoleAssignees(getExplicitGroupMembers(selectedGroup));
    }

    /**
     * Return the set of all role assignees for an explicit group.
     * Does not traverse subgroups.
     *
     * @param eg The explicit group to check.
     * @return The set of role assignees belonging to explicit group.
     */
    public List<RoleAssignee> getExplicitGroupMembers(ExplicitGroup eg) {
        return (eg != null) ?
                new ArrayList<>(explicitGroupService.getDirectMembers(eg)) : null;
    }

    /**
     * Return a string describing the type of a role assignee
     * TODO reference the bundle for localization
     *
     * @param ra The role assignee
     * @return A {@code String} representing the role assignee's type.
     */
    public String getRoleAssigneeTypeString(RoleAssignee ra) {
        if (ra instanceof User) {
            return BundleUtil.getStringFromBundle("dataverse.manageGroups.User");
        } else if (ra instanceof Group) {
            return BundleUtil.getStringFromBundle("dataverse.manageGroups.Group");
        } else {
            return BundleUtil.getStringFromBundle("dataverse.manageGroups.unknown");
        }
    }

    public String getMembershipString(ExplicitGroup eg) {
        long userCount = 0;
        long groupCount = 0;
        for (RoleAssignee ra : explicitGroupService.getDirectMembers(eg)) {
            if (ra instanceof User) {
                userCount++;
            } else {
                groupCount++;
            }
        }

        if (userCount == 0 && groupCount == 0) {
            return BundleUtil.getStringFromBundle("dataverse.manageGroups.nomembers");
        }

        String memberString = "";
        if (userCount == 1) {
            memberString = "1 " + BundleUtil.getStringFromBundle("dataverse.manageGroups.user");
        } else if (userCount != 1) {
            memberString = userCount + " " + BundleUtil.getStringFromBundle("dataverse.manageGroups.users");
        }

        if (groupCount == 1) {
            memberString = memberString + ", 1 " + BundleUtil.getStringFromBundle("dataverse.manageGroups.group");
        } else if (groupCount != 1) {
            memberString = memberString + ", " + groupCount + " " + BundleUtil.getStringFromBundle("dataverse.manageGroups.groups");
        }

        return memberString;
    }

    public void removeMemberFromSelectedGroup(RoleAssignee ra) {
        selectedGroup.remove(ra);
        selectedGroupRoleAssignees.remove(ra);
    }

    public List<RoleAssignee> completeRoleAssignee(String query) {

        List<RoleAssignee> alreadyAssignedRoleAssignees = new ArrayList<>();

        if (this.getNewExplicitGroupRoleAssignees() != null) {
            alreadyAssignedRoleAssignees.addAll(this.getNewExplicitGroupRoleAssignees());
        }
        if (this.getSelectedGroupRoleAssignees() != null) {
            alreadyAssignedRoleAssignees.addAll(this.getSelectedGroupRoleAssignees());
        }
        if (this.getSelectedGroupAddRoleAssignees() != null) {
            alreadyAssignedRoleAssignees.addAll(this.getSelectedGroupAddRoleAssignees());
        }

        return roleAssigneeService.filterRoleAssignees(query, dataverse, alreadyAssignedRoleAssignees);

    }

    public void initExplicitGroupDialog(ActionEvent ae) {
        setExplicitGroupName("");
        setExplicitGroupIdentifier("");
        setNewExplicitGroupDescription("");
        setNewExplicitGroupRoleAssignees(new LinkedList<>());
        setSelectedGroupRoleAssignees(null);
    }

    public void createExplicitGroup(ActionEvent ae) {
        Try.of(() -> mgCrudService.create(dataverse, explicitGroupName, explicitGroupIdentifier, newExplicitGroupDescription, newExplicitGroupRoleAssignees))
            .onSuccess((eg) -> {
                explicitGroups.add(eg.get());
                JsfHelper.addSuccessMessage(BundleUtil.getStringFromBundle("dataverse.manageGroups.create.success", eg.get().getDisplayName()));
            })
            .onFailure(throwable -> {
                logger.log(Level.WARNING, "Group creation failed", throwable);
                JsfHelper.addErrorMessage(BundleUtil.getStringFromBundle("dataverse.manageGroups.create.fail"), "");
            })
        ;
    }

    public void editExplicitGroup(ActionEvent ae) {

        Try.of(() -> mgCrudService.update(selectedGroup, selectedGroupAddRoleAssignees))
            .onSuccess((eg) -> {
                JsfHelper.addFlashSuccessMessage(BundleUtil.getStringFromBundle("dataverse.manageGroups.save.success", eg.get().getDisplayName()));
                explicitGroups.set(explicitGroups.indexOf(eg.get()), eg.get());
            })
            .onFailure(throwable -> JsfHelper.addErrorMessage(BundleUtil.getStringFromBundle("dataverse.manageGroups.edit.fail"),
                    ""))
        ;
    }

    public void validateGroupIdentifier(FacesContext context, UIComponent toValidate, Object rawValue) {
        String value = (String) rawValue;
        UIInput input = (UIInput) toValidate;
        input.setValid(true); // Optimistic approach

        if (!StringUtils.isEmpty(value)) {

            // cheap test - regex
            if (!Pattern.matches("^[a-zA-Z0-9\\_\\-]+$", value)) {
                input.setValid(false);
                context.addMessage(toValidate.getClientId(),
                        new FacesMessage(FacesMessage.SEVERITY_ERROR, "", BundleUtil.getStringFromBundle("dataverse.permissions.explicitGroupEditDialog.groupIdentifier.invalid")));

            } else if (explicitGroupService.findInOwner(dataverse.getId(), value) != null) {
                // Ok, see that the alias is not taken
                input.setValid(false);
                context.addMessage(toValidate.getClientId(),
                        new FacesMessage(FacesMessage.SEVERITY_ERROR, "", BundleUtil.getStringFromBundle("dataverse.permissions.explicitGroupEditDialog.groupIdentifier.taken")));
            }
        }
    }
    // -------------------- PRIVATE ---------------------

    // -------------------- SETTERS --------------------
    public void setSelectedGroup(ExplicitGroup selectedGroup) {
        this.selectedGroup = selectedGroup;
    }

    public void setDataverse(Dataverse dataverse) {
        this.dataverse = dataverse;
    }

    public void setDataverseId(Long dataverseId) {
        this.dataverseId = dataverseId;
    }

    public void setSelectedGroupRoleAssignees(List<RoleAssignee> newSelectedGroupRoleAssignees) {
        this.selectedGroupRoleAssignees = newSelectedGroupRoleAssignees;
    }

    public void setSelectedGroupAddRoleAssignees(List<RoleAssignee> ras) {
        this.selectedGroupAddRoleAssignees = ras;
    }

    public void setExplicitGroupName(String explicitGroupFriendlyName) {
        this.explicitGroupName = explicitGroupFriendlyName;
    }

    public void setExplicitGroupIdentifier(String explicitGroupName) {
        this.explicitGroupIdentifier = explicitGroupName;
    }

    public void setNewExplicitGroupRoleAssignees(List<RoleAssignee> newExplicitGroupRoleAssignees) {
        this.newExplicitGroupRoleAssignees = newExplicitGroupRoleAssignees;
    }

    public void setNewExplicitGroupDescription(String newExplicitGroupDescription) {
        this.newExplicitGroupDescription = newExplicitGroupDescription;
    }
}
