package edu.harvard.iq.dataverse.notification.dto;

import edu.harvard.iq.dataverse.notification.NotificationObjectType;
import edu.harvard.iq.dataverse.persistence.user.UserNotification;
import io.vavr.Tuple2;

import javax.ejb.Stateless;

@Stateless
public class EmailNotificationMapper {

    public EmailNotificationDto toDto(UserNotification userNotification, Tuple2<Long, NotificationObjectType> dvObjectIdAndType) {

        return new EmailNotificationDto(userNotification.getUser().getDisplayInfo().getEmailAddress(),
                                        userNotification.getType(),
                                        dvObjectIdAndType._1,
                                        dvObjectIdAndType._2,
                                        userNotification.getUser()
        );
    }
}
