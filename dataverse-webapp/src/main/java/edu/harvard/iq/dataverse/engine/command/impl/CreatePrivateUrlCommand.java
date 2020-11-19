package edu.harvard.iq.dataverse.engine.command.impl;

import edu.harvard.iq.dataverse.engine.command.AbstractCommand;
import edu.harvard.iq.dataverse.engine.command.CommandContext;
import edu.harvard.iq.dataverse.engine.command.DataverseRequest;
import edu.harvard.iq.dataverse.engine.command.RequiredPermissions;
import edu.harvard.iq.dataverse.engine.command.exception.IllegalCommandException;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.user.DataverseRole;
import edu.harvard.iq.dataverse.persistence.user.DataverseRole.BuiltInRole;
import edu.harvard.iq.dataverse.persistence.user.Permission;
import edu.harvard.iq.dataverse.persistence.user.PrivateUrlUser;
import edu.harvard.iq.dataverse.persistence.user.RoleAssignment;
import edu.harvard.iq.dataverse.privateurl.PrivateUrl;

import java.util.UUID;
import java.util.logging.Logger;

@RequiredPermissions(value = {Permission.ManageDatasetPermissions, Permission.ManageMinorDatasetPermissions}, isAllPermissionsRequired = false)
public class CreatePrivateUrlCommand extends AbstractCommand<PrivateUrl> {

    private static final Logger logger = Logger.getLogger(CreatePrivateUrlCommand.class.getCanonicalName());

    final Dataset dataset;

    public CreatePrivateUrlCommand(DataverseRequest dataverseRequest, Dataset theDataset) {
        super(dataverseRequest, theDataset);
        dataset = theDataset;
    }

    @Override
    public PrivateUrl execute(CommandContext ctxt) {
        logger.fine("Executing CreatePrivateUrlCommand...");
        if (dataset == null) {
            /**
             * @todo Internationalize this.
             */
            String message = "Can't create Private URL. Dataset is null.";
            logger.info(message);
            throw new IllegalCommandException(message, this);
        }
        PrivateUrl existing = ctxt.privateUrl().getPrivateUrlFromDatasetId(dataset.getId());
        if (existing != null) {
            /**
             * @todo Internationalize this.
             */
            String message = "Private URL already exists for dataset id " + dataset.getId() + ".";
            logger.info(message);
            throw new IllegalCommandException(message, this);
        }
        PrivateUrlUser privateUrlUser = new PrivateUrlUser(dataset.getId());
        DataverseRole memberRole = ctxt.roles().findBuiltinRoleByAlias(BuiltInRole.MEMBER);
        final String privateUrlToken = UUID.randomUUID().toString();
        RoleAssignment roleAssignment = ctxt.engine().submit(new AssignRoleCommand(privateUrlUser, memberRole, dataset, getRequest(), privateUrlToken));
        PrivateUrl privateUrl = new PrivateUrl(roleAssignment, dataset, ctxt.systemConfig().getDataverseSiteUrl());
        return privateUrl;
    }

}
