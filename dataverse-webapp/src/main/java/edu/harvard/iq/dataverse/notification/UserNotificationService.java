package edu.harvard.iq.dataverse.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.harvard.iq.dataverse.mail.MailService;
import edu.harvard.iq.dataverse.notification.dto.EmailNotificationDto;
import edu.harvard.iq.dataverse.notification.dto.EmailNotificationMapper;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.persistence.user.NotificationType;
import edu.harvard.iq.dataverse.persistence.user.UserNotification;
import edu.harvard.iq.dataverse.persistence.user.UserNotificationRepository;
import org.apache.commons.lang3.StringUtils;
import org.awaitility.Awaitility;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Class responsible for managing user notifications.
 */
@Stateless
public class UserNotificationService {

    private UserNotificationRepository userNotificationRepository;
    private MailService mailService;
    private EmailNotificationMapper mailMapper;
    private ObjectMapper objectMapper;
    private TypeReference<HashMap<String, String>> parametersTypeRef = new TypeReference<HashMap<String, String>>() {};

    private ExecutorService executorService;

    // -------------------- CONSTRUCTORS --------------------

    @Deprecated /* JEE requirement*/
    public UserNotificationService() {
        this.objectMapper = new ObjectMapper();
    }

    @Inject
    public UserNotificationService(UserNotificationRepository userNotificationRepository, MailService mailService, EmailNotificationMapper mailMapper) {
        this.userNotificationRepository = userNotificationRepository;
        this.mailService = mailService;
        this.mailMapper = mailMapper;
        this.objectMapper = new ObjectMapper();
        executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }

    // -------------------- LOGIC --------------------

    /**
     * Saves notification to database, hereby sends notification to users dashboard.
     */
    public void sendNotification(AuthenticatedUser dataverseUser, Timestamp sendDate, String type) {
        UserNotification userNotification = createUserNotification(dataverseUser, sendDate, type);
        userNotificationRepository.save(userNotification);
    }

    /**
     * Saves notification to database, then sends email asynchronously.
     *
     * @param notificationObjectType - type has to match correct #{@link NotificationType}
     */
    public void sendNotificationWithEmail(AuthenticatedUser dataverseUser, Timestamp sendDate, String type, Long dvObjectId,
                                          NotificationObjectType notificationObjectType) {
        UserNotification userNotification = createUserNotification(dataverseUser, sendDate, type, dvObjectId);
        userNotificationRepository.saveAndFlush(userNotification);
        executorService.submit(() -> sendEmail(userNotification.getId(), notificationObjectType));
    }


//    /**
//     * Saves notification to database, then sends email asynchronously.
//     *
//     * @param notificationObjectType - type has to match correct #{@link NotificationType}
//     */
//    public void sendNotificationWithEmail(AuthenticatedUser dataverseUser,
//                                          Timestamp sendDate,
//                                          String type,
//                                          Long dvObjectId,
//                                          NotificationObjectType notificationObjectType,
//                                          AuthenticatedUser requestor) {
//
//        UserNotification userNotification = createUserNotification(dataverseUser, sendDate, type, dvObjectId, requestor);
//
//        userNotificationRepository.saveAndFlush(userNotification);
//
//        executorService.submit(() -> sendEmail(userNotification.getId(), notificationObjectType));
//    }
//
//    /**
//     * Saves notification to database, then sends email asynchronously.
//     *
//     * @param notificationObjectType - type has to match correct #{@link NotificationType}
//     */
//    public void sendNotificationWithEmail(AuthenticatedUser dataverseUser,
//                                          Timestamp sendDate,
//                                          String type,
//                                          Long dvObjectId,
//                                          NotificationObjectType notificationObjectType,
//                                          AuthenticatedUser requestor,
//                                          String comment) {
//
//        UserNotification userNotification = createUserNotification(dataverseUser, sendDate, type, dvObjectId, requestor, comment);
//
//        userNotificationRepository.saveAndFlush(userNotification);
//
//        executorService.submit(() -> sendEmail(userNotification.getId(), notificationObjectType));
//    }
//
//    /**
//     * Saves notification to database, then sends email asynchronously.
//     *
//     * @param notificationObjectType - type has to match correct #{@link NotificationType}
//     * @param comment                - custom user message added to notification on '{@link NotificationType#RETURNEDDS}
//     */
//    public void sendNotificationWithEmail(AuthenticatedUser dataverseUser,
//                                          Timestamp sendDate,
//                                          String type,
//                                          Long dvObjectId,
//                                          NotificationObjectType notificationObjectType,
//                                          String comment) {
//
//        UserNotification userNotification = createUserNotification(dataverseUser, sendDate, type, dvObjectId, comment);
//
//        userNotificationRepository.saveAndFlush(userNotification);
//
//        executorService.submit(() -> sendEmail(userNotification.getId(), notificationObjectType));
//    }

    public void sendNotificationWithEmail(AuthenticatedUser dataverseUser, Timestamp sendDate, String type, Long dvObjectId,
                                          NotificationObjectType notificationObjectType, Map<String, String> parameters) {
        UserNotification userNotification = createUserNotification(dataverseUser, sendDate, type, dvObjectId, parameters);
        userNotificationRepository.saveAndFlush(userNotification);
        executorService.submit(() -> sendEmail(userNotification.getId(), notificationObjectType));
    }


    public Map<String, String> getParameters(UserNotification notification) {
        String json = notification.getParameters();
        return StringUtils.isNotBlank(json) ? getParametersMap(json) : new HashMap<>();
    }

    public void setParameters(UserNotification notification, Map<String, String> parameters) {
        notification.setParameters(parametersToString(parameters));
    }

    // -------------------- PRIVATE --------------------

    private Map<String, String> getParametersMap(String json) {
        try {
            return objectMapper.readValue(json, parametersTypeRef);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    private String parametersToString(Map<String, String> parametersMap) {
        try {
            return objectMapper.writeValueAsString(parametersMap);
        } catch (JsonProcessingException jpe) {
            throw new RuntimeException(jpe);
        }
    }

    private boolean sendEmail(long emailNotificationid, NotificationObjectType notificationObjectType) {
        UserNotification notification = Awaitility.await()
                .with()
                .pollDelay(Duration.ofSeconds(1))
                .pollInterval(Duration.ofSeconds(1))
                .atMost(Duration.ofSeconds(5))
                .until(() -> userNotificationRepository.findById(emailNotificationid), Optional::isPresent)
                .get();

        EmailNotificationDto emailNotificationDto = mailMapper.toDto(notification,
                notificationObjectType);

        Boolean emailSent = mailService.sendNotificationEmail(emailNotificationDto);

        if (emailSent) {
            userNotificationRepository.updateEmailSent(emailNotificationDto.getUserNotificationId());
        }

        return emailSent;
    }

    private UserNotification createUserNotification(AuthenticatedUser dataverseUser, Timestamp sendDate, String type, Long dvObjectId) {
        return createUserNotification(dataverseUser, sendDate, type, dvObjectId, Collections.emptyMap());
    }

    private UserNotification createUserNotification(AuthenticatedUser dataverseUser, Timestamp sendDate, String type) {
        return createUserNotification(dataverseUser, sendDate, type, null, Collections.emptyMap());
    }

//    private UserNotification createUserNotification(AuthenticatedUser dataverseUser, Timestamp sendDate, String type,
//                                                    Long dvObjectId, AuthenticatedUser requestor) {
//        return createUserNotification(dataverseUser, sendDate, type, dvObjectId, requestor, null);
//    }

//    private UserNotification createUserNotification(AuthenticatedUser dataverseUser, Timestamp sendDate, String type, Long dvObjectId, String userMessage) {
//        return createUserNotification(dataverseUser, sendDate, type, dvObjectId, null, userMessage);
//    }
//
//    private UserNotification createUserNotification(AuthenticatedUser dataverseUser, Timestamp sendDate, String type,
//                                              Long dvObjectId, AuthenticatedUser requestor, String userMessage) {
//        UserNotification userNotification = new UserNotification();
//        userNotification.setUser(dataverseUser);
//        userNotification.setSendDate(sendDate);
//        userNotification.setType(type);
//        userNotification.setObjectId(dvObjectId);
//        userNotification.setRequestor(requestor);
//        userNotification.setAdditionalMessage(userMessage);
//
//        return userNotification;
//    }

    private UserNotification createUserNotification(AuthenticatedUser dataverseUser, Timestamp sendDate, String type,
                                                    Long dvObjectId, Map<String, String> parameters) {
        UserNotification userNotification = new UserNotification();
        userNotification.setUser(dataverseUser);
        userNotification.setSendDate(sendDate);
        userNotification.setType(type);
        userNotification.setObjectId(dvObjectId);
        if (parameters != null && !parameters.isEmpty()) {
            userNotification.setParameters(parametersToString(parameters));
        }

        return userNotification;
    }
}
