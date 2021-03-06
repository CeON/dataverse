package edu.harvard.iq.dataverse.engine.command.impl;

import edu.harvard.iq.dataverse.engine.command.AbstractCommand;
import edu.harvard.iq.dataverse.engine.command.CommandContext;
import edu.harvard.iq.dataverse.engine.command.DataverseRequest;
import edu.harvard.iq.dataverse.engine.command.RequiredPermissions;
import edu.harvard.iq.dataverse.engine.command.exception.CommandException;
import edu.harvard.iq.dataverse.persistence.group.ExplicitGroup;
import edu.harvard.iq.dataverse.persistence.user.Permission;

import java.util.Set;

/**
 * @author michael
 */
@RequiredPermissions(Permission.ManageDataversePermissions)
public class RemoveRoleAssigneesFromExplicitGroupCommand extends AbstractCommand<ExplicitGroup> {

    private final Set<String> roleAssigneeIdentifiers;
    private final ExplicitGroup explicitGroup;

    public RemoveRoleAssigneesFromExplicitGroupCommand(DataverseRequest aRequest, ExplicitGroup anExplicitGroup, Set<String> someRoleAssigneeIdentifiers) {
        super(aRequest, anExplicitGroup.getOwner());
        roleAssigneeIdentifiers = someRoleAssigneeIdentifiers;
        explicitGroup = anExplicitGroup;
    }

    @Override
    public ExplicitGroup execute(CommandContext ctxt)  {
        for (String rai : roleAssigneeIdentifiers) {
            explicitGroup.removeByRoleAssgineeIdentifier(rai);
        }
        return ctxt.explicitGroups().persist(explicitGroup);
    }

}
