package edu.harvard.iq.dataverse.engine.command.impl;

import edu.harvard.iq.dataverse.engine.command.AbstractCommand;
import edu.harvard.iq.dataverse.engine.command.CommandContext;
import edu.harvard.iq.dataverse.engine.command.DataverseRequest;
import edu.harvard.iq.dataverse.engine.command.RequiredPermissions;
import edu.harvard.iq.dataverse.engine.command.exception.CommandException;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;

/**
 * @author Naomi
 */
// No permission needed to view published dvObjects
@RequiredPermissions({})
public class GetLatestPublishedDatasetVersionCommand extends AbstractCommand<DatasetVersion> {
    private final Dataset ds;

    public GetLatestPublishedDatasetVersionCommand(DataverseRequest aRequest, Dataset anAffectedDataset) {
        super(aRequest, anAffectedDataset);
        ds = anAffectedDataset;
    }

    @Override
    public DatasetVersion execute(CommandContext ctxt)  {
        for (DatasetVersion dsv : ds.getVersions()) {
            if (dsv.isReleased()) {
                return dsv;
            }
        }
        return null;
    }
}