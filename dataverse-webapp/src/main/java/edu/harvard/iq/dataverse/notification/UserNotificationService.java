package edu.harvard.iq.dataverse.notification;

import edu.harvard.iq.dataverse.mail.MailService;
import edu.harvard.iq.dataverse.notification.dto.EmailNotificationDto;
import edu.harvard.iq.dataverse.notification.dto.EmailNotificationMapper;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.persistence.user.NotificationType;
import edu.harvard.iq.dataverse.persistence.user.UserNotification;
import edu.harvard.iq.dataverse.persistence.user.UserNotificationDao;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.sql.Timestamp;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Stateless
public class UserNotificationService {

    private UserNotificationDao userNotificationDao;
    private MailService mailService;
    private EmailNotificationMapper mailMapper;

    private ExecutorService executorService;

    // -------------------- CONSTRUCTORS --------------------
    @Deprecated /* JEE requirement*/
    UserNotificationService() {
    }

    @Inject
    public UserNotificationService(UserNotificationDao userNotificationDao, MailService mailService, EmailNotificationMapper mailMapper) {
        this.userNotificationDao = userNotificationDao;
        this.mailService = mailService;
        this.mailMapper = mailMapper;

        executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }

    // -------------------- LOGIC --------------------

    public void sendNotification(AuthenticatedUser dataverseUser, Timestamp sendDate, NotificationType type) {
        UserNotification userNotification = new UserNotification();
        userNotification.setUser(dataverseUser);
        userNotification.setSendDate(sendDate);
        userNotification.setType(type);

        userNotificationDao.save(userNotification);
    }

    public void sendNotificationWithEmail(AuthenticatedUser dataverseUser,
                                          Timestamp sendDate,
                                          NotificationType type,
                                          long dvObjectId,
                                          NotificationObjectType notificationObjectType) {

        UserNotification userNotification = createUserNotification(dataverseUser, sendDate, type, dvObjectId);

        userNotificationDao.save(userNotification);
        userNotificationDao.flush();

        EmailNotificationDto emailNotificationDto = mailMapper.toDto(userNotification, dvObjectId, notificationObjectType);

        executorService.submit(() -> sendEmail(emailNotificationDto));
    }

    public void sendNotificationWithEmail(AuthenticatedUser dataverseUser,
                                          Timestamp sendDate,
                                          NotificationType type,
                                          long dvObjectId,
                                          NotificationObjectType notificationObjectType,
                                          AuthenticatedUser requestor) {

        UserNotification userNotification = createUserNotification(dataverseUser, sendDate, type, dvObjectId, requestor);

        userNotificationDao.save(userNotification);
        userNotificationDao.flush();

        EmailNotificationDto emailNotificationDto = mailMapper.toDto(userNotification, dvObjectId, notificationObjectType);

        executorService.submit(() -> sendEmail(emailNotificationDto, requestor));
    }

    // -------------------- PRIVATE --------------------

    private boolean sendEmail(EmailNotificationDto emailNotificationDto, AuthenticatedUser requester) {
        Boolean emailSent = mailService.sendNotificationEmail(emailNotificationDto, requester);

        if (emailSent) {
            userNotificationDao.updateEmailSent(emailNotificationDto.getUserNotificationId());
        }

        return emailSent;
    }

    private boolean sendEmail(EmailNotificationDto emailNotificationDto) {
        Boolean emailSent = mailService.sendNotificationEmail(emailNotificationDto);

        if (emailSent) {
            userNotificationDao.updateEmailSent(emailNotificationDto.getUserNotificationId());
        }

        return emailSent;
    }

    private UserNotification createUserNotification(AuthenticatedUser dataverseUser, Timestamp sendDate, NotificationType type,
                                                    long dvObjectId, AuthenticatedUser requestor) {
        UserNotification userNotification = new UserNotification();
        userNotification.setUser(dataverseUser);
        userNotification.setSendDate(sendDate);
        userNotification.setType(type);
        userNotification.setObjectId(dvObjectId);
        userNotification.setRequestor(requestor);
        return userNotification;
    }

    private UserNotification createUserNotification(AuthenticatedUser dataverseUser, Timestamp sendDate, NotificationType type, long dvObjectId) {
        UserNotification userNotification = new UserNotification();
        userNotification.setUser(dataverseUser);
        userNotification.setSendDate(sendDate);
        userNotification.setType(type);
        userNotification.setObjectId(dvObjectId);
        return userNotification;
    }
}
