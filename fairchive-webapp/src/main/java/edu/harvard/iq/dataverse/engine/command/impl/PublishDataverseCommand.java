package edu.harvard.iq.dataverse.engine.command.impl;

import edu.harvard.iq.dataverse.engine.command.AbstractCommand;
import edu.harvard.iq.dataverse.engine.command.CommandContext;
import edu.harvard.iq.dataverse.engine.command.DataverseRequest;
import edu.harvard.iq.dataverse.engine.command.RequiredPermissions;
import edu.harvard.iq.dataverse.engine.command.exception.IllegalCommandException;
import edu.harvard.iq.dataverse.notification.NotificationObjectType;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.persistence.user.NotificationType;
import edu.harvard.iq.dataverse.persistence.user.Permission;

import java.sql.Timestamp;
import java.util.Date;

@SuppressWarnings("serial")
@RequiredPermissions(Permission.PublishDataverse)
public class PublishDataverseCommand extends AbstractCommand<Dataverse> {

    private final Dataverse dataverse;

    public PublishDataverseCommand(DataverseRequest aRequest, Dataverse dataverse) {
        super(aRequest, dataverse);
        this.dataverse = dataverse;
    }

    @Override
    public Dataverse execute(CommandContext ctxt)  {
        if (dataverse.isReleased()) {
            throw new IllegalCommandException("Dataverse " + 
                        dataverse.getAlias() + " has already been published.", this);
        }

        Dataverse parent = dataverse.getOwner();
        // root dataverse doesn't have a parent
        if (parent != null) {
            if (!parent.isReleased()) {
                throw new IllegalCommandException("Dataverse " + dataverse.getAlias() 
                    + " may not be published because its host dataverse (" 
                        + parent.getAlias() + ") has not been published.", this);
            }
        }

        //Before setting dataverse to released send notifications to users with download file
        sendAssignRoleNotifications(ctxt);

        dataverse.setPublicationDate(new Timestamp(new Date().getTime()));
        dataverse.setReleaseUser((AuthenticatedUser) getUser());
        Dataverse savedDataverse = ctxt.dataverses().save(dataverse);
        /**
         * @todo consider also
         * ctxt.solrIndex().indexPermissionsOnSelfAndChildren(savedDataverse.getId());
         */
        ctxt.solrIndex().indexPermissionsForOneDvObject(savedDataverse);
        return savedDataverse;
    }

    /**
     * Sends notifications about assigned roles in a dataverse, but only
     * when role did not allow to view unpublished dataverse.
     * Since for this cases we postponed sending the notification
     * until the dataverse can be accessed and now when dataverse is
     * published it can be accessed by anyone
     */
    private void sendAssignRoleNotifications(CommandContext ctxt) {
        ctxt.roles().directRoleAssignments(dataverse).stream()
            .filter(ra -> !ra.has(Permission.ViewUnpublishedDataverse))
            .flatMap(ra -> ctxt.roleAssignees().getExplicitUsers(ctxt.roleAssignees().getRoleAssignee(ra.getAssigneeIdentifier())).stream())
            .distinct()
            .forEach(au -> ctxt.notifications().sendNotificationWithEmail(
                                au, new Timestamp(new Date().getTime()), NotificationType.ASSIGNROLE,
                                dataverse.getId(), NotificationObjectType.DATAVERSE));
    }
}
