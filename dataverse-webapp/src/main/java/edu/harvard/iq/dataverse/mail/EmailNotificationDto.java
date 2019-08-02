package edu.harvard.iq.dataverse.mail;

import edu.harvard.iq.dataverse.notification.NotificationType;

public class EmailNotificationDto {

    private String userEmail;
    private NotificationType notificationType;
    private long dvObjectId;
}
