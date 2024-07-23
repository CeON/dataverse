/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package edu.harvard.iq.dataverse.engine.command.impl;

import edu.harvard.iq.dataverse.engine.command.AbstractCommand;
import edu.harvard.iq.dataverse.engine.command.CommandContext;
import edu.harvard.iq.dataverse.engine.command.DataverseRequest;
import edu.harvard.iq.dataverse.engine.command.RequiredPermissions;
import edu.harvard.iq.dataverse.globalid.GlobalIdServiceBean;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.user.Permission;
import io.vavr.control.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

/**
 * @author skraffmiller
 */
@RequiredPermissions(Permission.PublishDataset)
public class DeaccessionDatasetVersionCommand extends AbstractCommand<DatasetVersion> {

    private static final Logger log = LoggerFactory.getLogger(DeaccessionDatasetVersionCommand.class);
    private final DatasetVersion theVersion;
    private final String deaccessionReason;
    private final String deaccessionForwardURLFor;

    public DeaccessionDatasetVersionCommand(DataverseRequest aRequest, DatasetVersion deaccessionVersion, String deaccessionReason, String deaccessionForwardURLFor) {
        super(aRequest, deaccessionVersion.getDataset());
        this.theVersion = deaccessionVersion;
        this.deaccessionReason = deaccessionReason;
        this.deaccessionForwardURLFor = deaccessionForwardURLFor;
    }

    @Override
    public DatasetVersion execute(CommandContext ctxt)  {

        theVersion.setVersionNote(deaccessionReason);
        theVersion.setArchiveNote(deaccessionForwardURLFor);
        theVersion.setVersionState(DatasetVersion.VersionState.DEACCESSIONED);
        theVersion.setLastUpdateTime(new Date());
        DatasetVersion merged = ctxt.em().merge(theVersion);

        Dataset dataset = merged.getDataset();
        ctxt.index().indexDataset(dataset, true);
        ctxt.em().merge(dataset);

        if (dataset.isDeaccessioned() && dataset.isIdentifierRegistered()) {
            Option.of(GlobalIdServiceBean.getBean(dataset.getProtocol(), ctxt))
                    .toTry()
                    .andThenTry(service -> service.deleteIdentifier(dataset))
                    .onFailure(e -> log.warn("Failed to unregister identifier {}", dataset.getGlobalId(), e));
        }

        return merged;
    }
}
