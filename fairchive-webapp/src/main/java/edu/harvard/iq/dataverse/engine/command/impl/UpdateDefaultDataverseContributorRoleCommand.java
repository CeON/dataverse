package edu.harvard.iq.dataverse.engine.command.impl;

import edu.harvard.iq.dataverse.engine.command.AbstractCommand;
import edu.harvard.iq.dataverse.engine.command.CommandContext;
import edu.harvard.iq.dataverse.engine.command.DataverseRequest;
import edu.harvard.iq.dataverse.engine.command.RequiredPermissions;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.user.DataverseRole;
import edu.harvard.iq.dataverse.persistence.user.Permission;

/**
 * @author skraffmiller
 */
@SuppressWarnings("serial")
@RequiredPermissions(Permission.ManageDataversePermissions)
public class UpdateDefaultDataverseContributorRoleCommand extends AbstractCommand<Dataverse> {

    private final DataverseRole role;
    private Dataverse dataverse;

    public UpdateDefaultDataverseContributorRoleCommand(final DataverseRole role, 
            final DataverseRequest request, final Dataverse dataverse) {
        super(request, dataverse);
        this.role = role;
        this.dataverse = dataverse;
    }

    @Override
    public Dataverse execute(final CommandContext context) {
    	this.dataverse.setDefaultDataverseContributorRole(this.role);
    	this.dataverse = context.dataverses().save(this.dataverse);
        return this.dataverse;
    }

}
