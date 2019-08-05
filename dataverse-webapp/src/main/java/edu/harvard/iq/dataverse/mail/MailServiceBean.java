/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.harvard.iq.dataverse.mail;

import edu.harvard.iq.dataverse.DataFileServiceBean;
import edu.harvard.iq.dataverse.DatasetServiceBean;
import edu.harvard.iq.dataverse.DatasetVersionServiceBean;
import edu.harvard.iq.dataverse.DataverseServiceBean;
import edu.harvard.iq.dataverse.PermissionServiceBean;
import edu.harvard.iq.dataverse.UserNotification;
import edu.harvard.iq.dataverse.authorization.groups.GroupServiceBean;
import edu.harvard.iq.dataverse.authorization.users.AuthenticatedUser;
import edu.harvard.iq.dataverse.mail.confirmemail.ConfirmEmailServiceBean;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean.Key;
import edu.harvard.iq.dataverse.util.MailUtil;
import edu.harvard.iq.dataverse.util.SystemConfig;
import io.vavr.Tuple2;
import io.vavr.control.Try;
import org.simplejavamail.email.Email;
import org.simplejavamail.email.EmailBuilder;
import org.simplejavamail.mailer.Mailer;
import org.simplejavamail.mailer.MailerBuilder;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import java.util.logging.Logger;

/**
 * original author: roberttreacy
 */
@Stateless
public class MailServiceBean implements java.io.Serializable {

    @EJB
    DataverseServiceBean dataverseService;
    @EJB
    DataFileServiceBean dataFileService;
    @EJB
    DatasetServiceBean datasetService;
    @EJB
    DatasetVersionServiceBean versionService;
    @EJB
    SystemConfig systemConfig;
    @EJB
    SettingsServiceBean settingsService;
    @EJB
    PermissionServiceBean permissionService;
    @EJB
    GroupServiceBean groupService;
    @EJB
    ConfirmEmailServiceBean confirmEmailService;

    @Inject
    private MailMessageCreator mailMessageCreator;

    private Mailer mailSender;

    private static final Logger logger = Logger.getLogger(MailServiceBean.class.getCanonicalName());

    private static final String charset = "UTF-8";

    /**
     * Creates a new instance of MailServiceBean
     */
    public MailServiceBean() {
    }

    @Resource(name = "mail/notifyMailSession")
    private Session session;

    // -------------------- CONSTRUCTORS --------------------

    @PostConstruct
    public void prepareMailSession() {
        mailSender = MailerBuilder
                .usingSession(session)
                .withDebugLogging(true)
                .buildMailer();
    }

    // -------------------- LOGIC --------------------

    public Boolean sendNotificationEmail(EmailNotificationDto notification, AuthenticatedUser requestor) {

        String emailAddress = notification.getUserEmail();
        Tuple2<String, String> messageAndSubject = mailMessageCreator.getMessageAndSubject(notification, requestor);

        return sendSystemEmail(emailAddress, messageAndSubject._2(), messageAndSubject._1());
    }

    public boolean sendSystemEmail(String to, String subject, String messageText) {

        String message = mailMessageCreator.createMailBodyMessage(messageText, dataverseService.findRootDataverse().getName(), getSystemAddress());

        Email email = EmailBuilder.startingBlank()
                .from(getSystemAddress())
                .withRecipients(mailMessageCreator.createRecipients(to, ""))
                .withSubject(subject)
                .appendText(message)
                .buildEmail();

        return Try.run(() -> mailSender.sendMail(email))
                .map(emailSent -> true)
                .onFailure(Throwable::printStackTrace)
                .getOrElse(false);
    }

    public boolean sendMail(String reply, String to, String subject, String messageText) {

        Email email = EmailBuilder.startingBlank()
                .from(getSystemAddress())
                .withRecipients(mailMessageCreator.createRecipients(to, mailMessageCreator.createRecipientName(reply, getSystemAddress())))
                .withSubject(subject)
                .withReplyTo(reply)
                .appendText(messageText)
                .buildEmail();

        return Try.run(() -> mailSender.sendMail(email))
                .map(emailSent -> true)
                .onFailure(Throwable::printStackTrace)
                .getOrElse(false);
    }

    private InternetAddress getSystemAddress() {
        String systemEmail = settingsService.getValueForKey(Key.SystemEmail);
        return MailUtil.parseSystemAddress(systemEmail);
    }

    private String getUserEmailAddress(UserNotification notification) {
        if (notification != null) {
            if (notification.getUser() != null) {
                if (notification.getUser().getDisplayInfo() != null) {
                    if (notification.getUser().getDisplayInfo().getEmailAddress() != null) {
                        logger.fine("Email address: " + notification.getUser().getDisplayInfo().getEmailAddress());
                        return notification.getUser().getDisplayInfo().getEmailAddress();
                    }
                }
            }
        }

        logger.fine("no email address");
        return null;
    }

}
