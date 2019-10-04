package edu.harvard.iq.dataverse;

import edu.harvard.iq.dataverse.authorization.groups.impl.explicit.ExplicitGroupServiceBean;
import edu.harvard.iq.dataverse.engine.command.exception.CommandException;
import edu.harvard.iq.dataverse.engine.command.impl.CreateExplicitGroupCommand;
import edu.harvard.iq.dataverse.engine.command.impl.DeleteExplicitGroupCommand;
import edu.harvard.iq.dataverse.engine.command.impl.UpdateExplicitGroupCommand;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.group.ExplicitGroup;
import edu.harvard.iq.dataverse.persistence.group.GroupException;
import edu.harvard.iq.dataverse.persistence.user.RoleAssignee;
import org.apache.commons.collections4.CollectionUtils;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.List;

@Stateless
public class ManageGroupsCRUDService {

    private EjbDataverseEngine engineService;
    private DataverseRequestServiceBean dvRequestService;
    private ExplicitGroupServiceBean explicitGroupService;

    // -------------------- CONSTRUCTORS --------------------
    @Deprecated
    public ManageGroupsCRUDService() {
    }

    @Inject
    public ManageGroupsCRUDService(EjbDataverseEngine engineService,
                                   DataverseRequestServiceBean dvRequestService,
                                   ExplicitGroupServiceBean explicitGroupService) {
        this.engineService = engineService;
        this.dvRequestService = dvRequestService;
        this.explicitGroupService = explicitGroupService;
    }

    // -------------------- LOGIC --------------------
    public ExplicitGroup create(Dataverse dataverse, String explicitGroupName, String explicitGroupIdentifier, String explicitGroupDescription,
                                List<RoleAssignee> explicitGroupRoleAssignees)
            throws CommandException, GroupException {
        ExplicitGroup explicitGroup = explicitGroupService.getProvider().makeGroup();
        explicitGroup.setDisplayName(explicitGroupName);
        explicitGroup.setGroupAliasInOwner(explicitGroupIdentifier);
        explicitGroup.setDescription(explicitGroupDescription);

        if(CollectionUtils.isNotEmpty(explicitGroupRoleAssignees)) {
            for (RoleAssignee ra : explicitGroupRoleAssignees) {
                explicitGroup.add(ra);
            }
        }

        return engineService.submit(new CreateExplicitGroupCommand(dvRequestService.getDataverseRequest(), dataverse, explicitGroup));
    }

    public ExplicitGroup update(ExplicitGroup selectedGroup, List<RoleAssignee> selectedGroupAddRoleAssignees)
            throws CommandException, GroupException {

        if(CollectionUtils.isNotEmpty(selectedGroupAddRoleAssignees)) {
            for (RoleAssignee ra : selectedGroupAddRoleAssignees) {
                selectedGroup.add(ra);
            }
        }
        return engineService.submit(new UpdateExplicitGroupCommand(dvRequestService.getDataverseRequest(), selectedGroup));
    }

    public void delete(ExplicitGroup explicitGroup) throws CommandException {
        engineService.submit(new DeleteExplicitGroupCommand(dvRequestService.getDataverseRequest(), explicitGroup));
    }
}
