package edu.harvard.iq.dataverse.notification.dto;

import edu.harvard.iq.dataverse.notification.NotificationObjectType;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;

import java.util.HashMap;
import java.util.Map;

public class EmailNotificationDto {

    private long userNotificationId;
    private String userEmail;
    private String notificationType;
    private Long dvObjectId;
    private NotificationObjectType notificationObjectType;
    private AuthenticatedUser notificationReceiver;
    private Map<String, String> parameters = new HashMap<>();

    // -------------------- CONSTRUCTORS --------------------


    public EmailNotificationDto(long userNotificationId, String userEmail, String notificationType,
                                Long dvObjectId, NotificationObjectType notificationObjectType,
                                AuthenticatedUser notificationReceiver, Map<String, String> parameters) {
        this.userNotificationId = userNotificationId;
        this.userEmail = userEmail;
        this.notificationType = notificationType;
        this.dvObjectId = dvObjectId;
        this.notificationObjectType = notificationObjectType;
        this.notificationReceiver = notificationReceiver;
        if (parameters != null) {
            this.parameters.putAll(parameters);
        }
    }

    // -------------------- GETTERS --------------------

    public long getUserNotificationId() {
        return userNotificationId;
    }

    public NotificationObjectType getNotificationObjectType() {
        return notificationObjectType;
    }

    public AuthenticatedUser getNotificationReceiver() {
        return notificationReceiver;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public String getNotificationType() {
        return notificationType;
    }

    public long getDvObjectId() {
        return dvObjectId;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    // -------------------- LOGIC --------------------

    public String getParameter(String key) {
        return parameters.get(key);
    }
}
