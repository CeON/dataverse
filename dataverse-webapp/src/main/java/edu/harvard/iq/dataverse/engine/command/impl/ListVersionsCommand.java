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
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.user.Permission;

import java.util.LinkedList;
import java.util.List;

/**
 * @author Naomi
 */
// No permission needed to view published dvObjects
@RequiredPermissions({})
public class ListVersionsCommand extends AbstractCommand<List<DatasetVersion>> {

    private final Dataset ds;

    public ListVersionsCommand(DataverseRequest aRequest, Dataset aDataset) {
        super(aRequest, aDataset);
        ds = aDataset;
    }

    @Override
    public List<DatasetVersion> execute(CommandContext ctxt)  {
        List<DatasetVersion> outputList = new LinkedList<>();
        for (DatasetVersion dsv : ds.getVersions()) {
            if (dsv.isReleased() || ctxt.permissions().requestOn(getRequest(), ds).has(Permission.EditDataset)) {
                outputList.add(dsv);
            }
        }
        return outputList;
    }
}
