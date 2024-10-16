package edu.harvard.iq.dataverse.mail.confirmemail;

import edu.harvard.iq.dataverse.DataverseDao;
import edu.harvard.iq.dataverse.authorization.AuthenticationServiceBean;
import edu.harvard.iq.dataverse.authorization.providers.shib.ShibAuthenticationProvider;
import edu.harvard.iq.dataverse.common.BundleUtil;
import edu.harvard.iq.dataverse.mail.EmailContent;
import edu.harvard.iq.dataverse.mail.MailService;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.persistence.user.ConfirmEmailData;
import edu.harvard.iq.dataverse.persistence.user.NotificationType;
import edu.harvard.iq.dataverse.persistence.user.User;
import edu.harvard.iq.dataverse.persistence.user.UserNotification;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean.Key;
import edu.harvard.iq.dataverse.util.SystemConfig;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.NonUniqueResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author bsilverstein
 */
@Stateless
public class ConfirmEmailServiceBean {

    private static final Logger logger = Logger.getLogger(ConfirmEmailServiceBean.class.getCanonicalName());

    @EJB
    AuthenticationServiceBean dataverseUserService;

    @EJB
    MailService mailService;

    @Inject
    SettingsServiceBean settingsService;

    @EJB
    SystemConfig systemConfig;

    @EJB
    DataverseDao dataverseDao;

    @EJB
    AuthenticationServiceBean authenticationService;

    @PersistenceContext(unitName = "VDCNet-ejbPU")
    private EntityManager em;

    // -------------------- LOGIC --------------------

    /**
     * Initiate the email confirmation process.
     *
     * @return {@link ConfirmEmailInitResponse}
     */
    public ConfirmEmailInitResponse beginConfirm(AuthenticatedUser user) throws ConfirmEmailException {
        deleteAllExpiredTokens();
        if (user != null) {
            return sendConfirm(user, true);
        } else {
            return new ConfirmEmailInitResponse(false);
        }
    }

    /**
     * Process the email confirmation token, allowing the user to confirm the
     * email address or report on a invalid token.
     */
    public ConfirmEmailExecResponse processToken(String tokenQueried) {
        deleteAllExpiredTokens();
        ConfirmEmailExecResponse tokenUnusable = new ConfirmEmailExecResponse(tokenQueried, null);
        ConfirmEmailData confirmEmailData = findSingleConfirmEmailDataByToken(tokenQueried);
        if (confirmEmailData != null) {
            if (confirmEmailData.isExpired()) {
                // shouldn't reach here since tokens are being expired above
                return tokenUnusable;
            } else {
                ConfirmEmailExecResponse goodTokenCanProceed = new ConfirmEmailExecResponse(tokenQueried, confirmEmailData);
                long nowInMilliseconds = new Date().getTime();
                Timestamp emailConfirmed = new Timestamp(nowInMilliseconds);
                AuthenticatedUser authenticatedUser = confirmEmailData.getAuthenticatedUser();
                authenticatedUser.setEmailConfirmed(emailConfirmed);
                em.remove(confirmEmailData);
                return goodTokenCanProceed;
            }
        } else {
            return tokenUnusable;
        }
    }

    public ConfirmEmailData findSingleConfirmEmailDataByUser(AuthenticatedUser user) {
        ConfirmEmailData confirmEmailData = null;
        TypedQuery<ConfirmEmailData> typedQuery = em.createNamedQuery("ConfirmEmailData.findByUser", ConfirmEmailData.class);
        typedQuery.setParameter("user", user);
        try {
            confirmEmailData = typedQuery.getSingleResult();
        } catch (NoResultException | NonUniqueResultException ex) {
            logger.fine("When looking up user " + user + " caught " + ex);
        }
        return confirmEmailData;
    }

    public List<ConfirmEmailData> findAllConfirmEmailDataByUser(AuthenticatedUser user) {
        return em.createNamedQuery("ConfirmEmailData.findByUser", ConfirmEmailData.class)
                .setParameter("user", user)
                .getResultList();
    }

    public List<ConfirmEmailData> findAllConfirmEmailData() {
        TypedQuery<ConfirmEmailData> typedQuery = em.createNamedQuery("ConfirmEmailData.findAll", ConfirmEmailData.class);
        return typedQuery.getResultList();
    }

    /**
     * @return True if token is deleted. False otherwise.
     */
    public boolean deleteTokenForUser(AuthenticatedUser authenticatedUser) {
        ConfirmEmailData confirmEmailData = findSingleConfirmEmailDataByUser(authenticatedUser);
        if (confirmEmailData != null) {
            em.remove(confirmEmailData);
            return true;
        }
        return false;
    }

    public ConfirmEmailData createToken(AuthenticatedUser au) {
        ConfirmEmailData confirmEmailData = new ConfirmEmailData(au, settingsService.getValueForKeyAsLong(Key.MinutesUntilConfirmEmailTokenExpires));
        em.persist(confirmEmailData);
        return confirmEmailData;
    }

    public String optionalConfirmEmailAddonMsg(AuthenticatedUser user) {
        final String emptyString = "";
        if (user == null) {
            logger.info("Can't return confirm email message. AuthenticatedUser was null!");
            return emptyString;
        }
        if (ShibAuthenticationProvider.PROVIDER_ID.equals(user.getAuthenticatedUserLookup().getAuthenticationProviderId())) {
            // Shib users don't have to confirm their email address.
            return emptyString;
        }
        ConfirmEmailData confirmEmailData = findSingleConfirmEmailDataByUser(user);
        if (confirmEmailData == null) {
            logger.info("Can't return confirm email message. No ConfirmEmailData for user id " + user.getId());
            return emptyString;
        }
        String expTime = ConfirmEmailUtil.friendlyExpirationTime(settingsService.getValueForKeyAsLong(Key.MinutesUntilConfirmEmailTokenExpires), user.getNotificationsLanguage());
        String confirmEmailUrl = systemConfig.getDataverseSiteUrl() + "/confirmemail.xhtml?token=" + confirmEmailData.getToken();

        String optionalConfirmEmailMsg = BundleUtil.getStringFromBundleWithLocale("notification.email.welcomeConfirmEmailAddOn", user.getNotificationsLanguage(), confirmEmailUrl, expTime);
        return optionalConfirmEmailMsg;
    }

    /**
     * Checks if a user email has been verified.
     * @return true if verified, false otherwise
     */
    public boolean hasVerifiedEmail(AuthenticatedUser user) {
        boolean hasTimestamp = user.getEmailConfirmed() != null;
        boolean hasNoStaleVerificationTokens = findSingleConfirmEmailDataByUser(user) == null;
        boolean isVerifiedByAuthProvider = authenticationService.lookupProvider(user).isEmailVerified();

        return (hasTimestamp && hasNoStaleVerificationTokens) || isVerifiedByAuthProvider;
    }

    /**
     * This method should be used ONLY in the context of restricting
     * users with unconfirmed e-mail as it can produce different results
     * depending on app installation context.
     * <p>If the UnconfirmedMailRestrictionModeEnabled property is
     * not set to true, all calls will return false, otherwise:
     * <ul>
     *     <li>For the superuser the method returns false;
     *     <li>For the guest user the method returns false;
     *     <li>For the authenticated user which is not the superuser the
     *     method returns false or true, depending of mail confirmation status.
     * </ul>
     */
    public boolean hasEffectivelyUnconfirmedMail(User user) {
        return getEffectiveMailConfirmationStatus(user) == EffectiveMailConfirmationStatus.UNCONFIRMED;
    }

    // -------------------- PRIVATE --------------------

    /**
     * This method should be used ONLY in the context of restricting
     * users with unconfirmed e-mail as it can produce different results
     * depending on app installation context.
     * <p>If the UnconfirmedMailRestrictionModeEnabled property is
     * not set to true, all calls will return NOT_APPLICABLE, otherwise:
     * <ul>
     *     <li>For the superuser the method returns NOT_APPLICABLE;
     *     <li>For the guest user the method returns NOT_APPLICABLE;
     *     <li>For the authenticated user which is not the superuser the
     *     method returns CONFIRMED or UNCONFIRMED, depending of mail
     *     confirmation status.
     * </ul>
     */
    private EffectiveMailConfirmationStatus getEffectiveMailConfirmationStatus(User user) {
        if (!systemConfig.isUnconfirmedMailRestrictionModeEnabled() || user.isSuperuser()) {
            return EffectiveMailConfirmationStatus.NOT_APPLICABLE;
        }

        if (user instanceof AuthenticatedUser) {
            return hasVerifiedEmail((AuthenticatedUser) user)
                    ? EffectiveMailConfirmationStatus.CONFIRMED : EffectiveMailConfirmationStatus.UNCONFIRMED;
        } else {
            return EffectiveMailConfirmationStatus.NOT_APPLICABLE;
        }
    }


    private ConfirmEmailInitResponse sendConfirm(AuthenticatedUser aUser, boolean sendEmail) throws ConfirmEmailException {
        // delete old tokens for the user
        ConfirmEmailData oldToken = findSingleConfirmEmailDataByUser(aUser);
        if (oldToken != null) {
            em.remove(oldToken);
        }

        aUser.setEmailConfirmed(null);
        aUser = em.merge(aUser);
        // create a fresh token for the user iff they don't have an existing token
        ConfirmEmailData confirmEmailData = new ConfirmEmailData(aUser, settingsService.getValueForKeyAsLong(Key.MinutesUntilConfirmEmailTokenExpires));
        try {
            /**
             * @todo This "persist" is causing lots of noise in Glassfish's
             * server.log if a token already exists (i.e. it isn't expired and
             * wasn't deleted above). Exercise this bug by running
             * ConfirmEmailIT.
             */
            em.persist(confirmEmailData);
            ConfirmEmailInitResponse confirmEmailInitResponse = new ConfirmEmailInitResponse(true, confirmEmailData, optionalConfirmEmailAddonMsg(aUser));
            if (sendEmail) {
                sendLinkOnEmailChange(aUser, confirmEmailInitResponse.getConfirmUrl());
            }

            return confirmEmailInitResponse;
        } catch (Exception ex) {
            String msg = "Unable to save token for " + aUser.getEmail();
            throw new ConfirmEmailException(msg, ex);
        }
    }

    /**
     * @todo: We expect to send two messages. One at signup and another at email
     * change.
     */
    private void sendLinkOnEmailChange(AuthenticatedUser aUser, String confirmationUrl) throws ConfirmEmailException {
        String messageBody = BundleUtil.getStringFromBundleWithLocale("notification.email.changeEmail", aUser.getNotificationsLanguage(),
                aUser.getFirstName(),
                confirmationUrl,
                ConfirmEmailUtil.friendlyExpirationTime(settingsService.getValueForKeyAsLong(Key.MinutesUntilConfirmEmailTokenExpires), aUser.getNotificationsLanguage())
        );
        logger.log(Level.FINE, "messageBody:{0}", messageBody);

        String toAddress = aUser.getEmail();
        try {
            Dataverse rootDataverse = dataverseDao.findRootDataverse();
            if (rootDataverse != null) {
                String rootDataverseName = rootDataverse.getName();
                // FIXME: consider refactoring this into MailServiceBean.sendNotificationEmail. CONFIRMEMAIL may be the only type where we don't want an in-app notification.
                UserNotification userNotification = new UserNotification();
                userNotification.setType(NotificationType.CONFIRMEMAIL);
                String subject = BundleUtil.getStringFromBundleWithLocale("notification.email.verifyEmail.subject", aUser.getNotificationsLanguage(), rootDataverseName);
                logger.fine("sending email to " + toAddress + " with this subject: " + subject);

                String footerMailMessage = mailService.getFooterMailMessage(userNotification.getType(), aUser.getNotificationsLanguage());
                boolean emailSent = mailService.sendMail(toAddress, null, new EmailContent(subject, messageBody, footerMailMessage));

                if (!emailSent) {
                    throw new ConfirmEmailException("Problem sending email confirmation link possibily due to mail server not being configured.");
                }
            }
        } catch (Exception e) {
            logger.info("The root dataverse is not present. Don't send a notification to dataverseAdmin.");
        }
        logger.log(Level.FINE, "attempted to send mail to {0}", aUser.getEmail());
    }

    /**
     * @return Null or a single row of email confirmation data.
     */
    private ConfirmEmailData findSingleConfirmEmailDataByToken(String token) {
        ConfirmEmailData confirmEmailData = null;
        TypedQuery<ConfirmEmailData> typedQuery = em.createNamedQuery("ConfirmEmailData.findByToken", ConfirmEmailData.class);
        typedQuery.setParameter("token", token);
        try {
            confirmEmailData = typedQuery.getSingleResult();
        } catch (NoResultException | NonUniqueResultException ex) {
            logger.fine("When looking up " + token + " caught " + ex);
        }
        return confirmEmailData;
    }

    /**
     * @return The number of tokens deleted.
     */
    private long deleteAllExpiredTokens() {
        long numDeleted = 0;
        List<ConfirmEmailData> allData = findAllConfirmEmailData();
        for (ConfirmEmailData data : allData) {
            if (data.isExpired()) {
                em.remove(data);
                numDeleted++;
            }
        }
        return numDeleted;
    }
}
