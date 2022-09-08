package edu.harvard.iq.dataverse.notification.dto;

import edu.harvard.iq.dataverse.notification.NotificationObjectType;
import edu.harvard.iq.dataverse.notification.UserNotificationService;
import edu.harvard.iq.dataverse.persistence.user.UserNotification;

import javax.ejb.Stateless;
import javax.inject.Inject;

@Stateless
public class EmailNotificationMapper {

    private UserNotificationService userNotificationService;

    // -------------------- CONSTRUCTORS --------------------

    @Deprecated
    public EmailNotificationMapper() { }

    @Inject
    public EmailNotificationMapper(UserNotificationService userNotificationService) {
        this.userNotificationService = userNotificationService;
    }

    // -------------------- LOGIC --------------------

    public EmailNotificationDto toDto(UserNotification userNotification,
                                      NotificationObjectType notificationObjectType) {
        return new EmailNotificationDto(userNotification.getId(),
                userNotification.getUser().getDisplayInfo().getEmailAddress(),
                userNotification.getType(),
                userNotification.getObjectId(),
                notificationObjectType,
                userNotification.getUser(),
                userNotificationService.getParameters(userNotification)
        );
    }
}
