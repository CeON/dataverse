package edu.harvard.iq.dataverse.engine.command.impl;

import edu.harvard.iq.dataverse.engine.command.AbstractCommand;
import edu.harvard.iq.dataverse.engine.command.CommandContext;
import edu.harvard.iq.dataverse.engine.command.DataverseRequest;
import edu.harvard.iq.dataverse.engine.command.RequiredPermissions;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.harvest.HarvestingClient;
import edu.harvard.iq.dataverse.persistence.user.Permission;

/**
 * @author Leonid Andreev
 */
@RequiredPermissions(Permission.EditDataverse)
public class UpdateHarvestingClientCommand extends AbstractCommand<HarvestingClient> {

    private final Dataverse dv;
    private final HarvestingClient harvestingClient;

    public UpdateHarvestingClientCommand(DataverseRequest aRequest, HarvestingClient harvestingClient) {
        super(aRequest, harvestingClient.getDataverse());
        this.harvestingClient = harvestingClient;
        this.dv = harvestingClient.getDataverse();
    }

    @Override
    public HarvestingClient execute(CommandContext ctxt) {
        // TODO: check that the harvesting client config is attached to a legit 
        // dataverse; and that we are in fact modifying a config that already 
        // exists. -- L.A. 4.4
        return ctxt.em().merge(this.harvestingClient);
    }

}
