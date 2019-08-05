package edu.harvard.iq.dataverse.mail;

import edu.harvard.iq.dataverse.authorization.users.AuthenticatedUser;
import edu.harvard.iq.dataverse.notification.NotificationObjectType;
import edu.harvard.iq.dataverse.notification.NotificationType;

public class EmailNotificationDto {

    private String userEmail;
    private NotificationType notificationType;
    private long dvObjectId;
    private NotificationObjectType notificationObjectType;
    private AuthenticatedUser user;

    public NotificationObjectType getNotificationObjectType() {
        return notificationObjectType;
    }

    public AuthenticatedUser getUser() {
        return user;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public NotificationType getNotificationType() {
        return notificationType;
    }

    public long getDvObjectId() {
        return dvObjectId;
    }
}
