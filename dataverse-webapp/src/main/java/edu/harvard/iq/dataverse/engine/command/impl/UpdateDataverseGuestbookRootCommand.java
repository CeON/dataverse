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
import edu.harvard.iq.dataverse.engine.command.exception.CommandException;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.user.Permission;

/**
 * @author skraffmi
 */
@RequiredPermissions(Permission.EditDataverse)
public class UpdateDataverseGuestbookRootCommand extends AbstractCommand<Dataverse> {

    private final boolean newValue;
    private Dataverse dv;

    public UpdateDataverseGuestbookRootCommand(boolean newValue, DataverseRequest aRequest, Dataverse anAffectedDataverse) {
        super(aRequest, anAffectedDataverse);
        this.newValue = newValue;
        this.dv = anAffectedDataverse;
    }

    @Override
    public Dataverse execute(CommandContext ctxt)  {
        if (dv.isGuestbookRoot() != newValue) {
            dv.setGuestbookRoot(newValue);
            dv = ctxt.dataverses().save(dv);
        }
        return dv;
    }

    public boolean isNewValue() {
        return newValue;
    }
}
