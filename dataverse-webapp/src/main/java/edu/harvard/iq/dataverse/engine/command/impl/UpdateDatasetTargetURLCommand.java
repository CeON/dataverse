package edu.harvard.iq.dataverse.engine.command.impl;

import edu.harvard.iq.dataverse.engine.command.AbstractVoidCommand;
import edu.harvard.iq.dataverse.engine.command.CommandContext;
import edu.harvard.iq.dataverse.engine.command.DataverseRequest;
import edu.harvard.iq.dataverse.engine.command.RequiredPermissions;
import edu.harvard.iq.dataverse.engine.command.exception.CommandException;
import edu.harvard.iq.dataverse.engine.command.exception.PermissionException;
import edu.harvard.iq.dataverse.globalid.GlobalIdServiceBean;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.persistence.user.Permission;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.Date;

/**
 * @author skraffmi
 */
@RequiredPermissions(Permission.EditDataset)
public class UpdateDatasetTargetURLCommand extends AbstractVoidCommand {

    private final Dataset target;

    public UpdateDatasetTargetURLCommand(Dataset target, DataverseRequest aRequest) {
        super(aRequest, target);
        this.target = target;
    }

    @Override
    protected void executeImpl(CommandContext ctxt)  {

        if (!(getUser() instanceof AuthenticatedUser) || !getUser().isSuperuser()) {
            throw new PermissionException("Update Target URL can only be called by superusers.",
                                          this, Collections.singleton(Permission.EditDataset), target);
        }
        GlobalIdServiceBean idServiceBean = GlobalIdServiceBean.getBean(target.getProtocol(), ctxt);
        try {
            String doiRetString = idServiceBean.modifyIdentifierTargetURL(target);
            if (doiRetString != null && doiRetString.contains(target.getIdentifier())) {
                target.setGlobalIdCreateTime(new Timestamp(new Date().getTime()));
                ctxt.em().merge(target);
                ctxt.em().flush();
                for (DataFile df : target.getFiles()) {
                    doiRetString = idServiceBean.modifyIdentifierTargetURL(df);
                    if (doiRetString != null && doiRetString.contains(df.getIdentifier())) {
                        df.setGlobalIdCreateTime(new Timestamp(new Date().getTime()));
                        ctxt.em().merge(df);
                        ctxt.em().flush();
                    }
                }
            } else {
                //do nothing - we'll know it failed because the global id create time won't have been updated.
            }
        } catch (Exception e) {
            //do nothing - idem and the problem has been logged
        }
    }

}
