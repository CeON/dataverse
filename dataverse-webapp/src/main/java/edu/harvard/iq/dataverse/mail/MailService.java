/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.harvard.iq.dataverse.mail;

import edu.harvard.iq.dataverse.DataverseServiceBean;
import edu.harvard.iq.dataverse.notification.dto.EmailNotificationDto;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean.Key;
import edu.harvard.iq.dataverse.util.MailUtil;
import io.vavr.Tuple2;
import io.vavr.control.Try;
import org.simplejavamail.email.Email;
import org.simplejavamail.email.EmailBuilder;
import org.simplejavamail.mailer.Mailer;
import org.simplejavamail.mailer.MailerBuilder;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import java.util.logging.Logger;

/**
 * original author: roberttreacy
 */
@Stateless
public class MailService implements java.io.Serializable {

    private DataverseServiceBean dataverseService;
    private SettingsServiceBean settingsService;
    private MailMessageCreator mailMessageCreator;

    private Mailer mailSender;

    private static final Logger logger = Logger.getLogger(MailService.class.getCanonicalName());


    @Resource(name = "mail/notifyMailSession")
    private Session session;

    // -------------------- CONSTRUCTORS --------------------

    @Deprecated /* JEE requirement */
    MailService() {
    }

    @Inject
    public MailService(DataverseServiceBean dataverseService, SettingsServiceBean settingsService, MailMessageCreator mailMessageCreator) {
        this.dataverseService = dataverseService;
        this.settingsService = settingsService;
        this.mailMessageCreator = mailMessageCreator;
    }

    @PostConstruct
    public void prepareMailSession() {
        mailSender = MailerBuilder
                .usingSession(session)
                .withDebugLogging(true)
                .buildMailer();
    }

    // -------------------- LOGIC --------------------

    public Boolean sendNotificationEmail(EmailNotificationDto notification, AuthenticatedUser requestor) {

        String userEmail = notification.getUserEmail();
        String systemEmail = settingsService.getValueForKey(Key.SystemEmail);

        Tuple2<String, String> messageAndSubject = mailMessageCreator.getMessageAndSubject(notification, requestor, systemEmail);

        if (messageAndSubject._1().isEmpty() || messageAndSubject._2().isEmpty()) {
            return false;
        }

        return sendSystemEmail(userEmail, messageAndSubject._2(), messageAndSubject._1());
    }

    public boolean sendSystemEmail(String to, String subject, String messageText) {

        String message = mailMessageCreator.createMailFooterMessage(messageText, dataverseService.findRootDataverse().getName(), getSystemAddress());

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

    // -------------------- PRIVATE --------------------

    private InternetAddress getSystemAddress() {
        String systemEmail = settingsService.getValueForKey(Key.SystemEmail);
        return MailUtil.parseSystemAddress(systemEmail);
    }

    // -------------------- SETTERS --------------------

    void setMailSender(Mailer mailSender) {
        this.mailSender = mailSender;
    }
}
