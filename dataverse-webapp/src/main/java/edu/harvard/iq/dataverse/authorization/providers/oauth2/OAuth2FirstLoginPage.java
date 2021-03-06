package edu.harvard.iq.dataverse.authorization.providers.oauth2;

import edu.harvard.iq.dataverse.DataverseSession;
import edu.harvard.iq.dataverse.authorization.AuthTestDataServiceBean;
import edu.harvard.iq.dataverse.authorization.AuthUtil;
import edu.harvard.iq.dataverse.authorization.AuthenticationProvider;
import edu.harvard.iq.dataverse.authorization.AuthenticationRequest;
import edu.harvard.iq.dataverse.authorization.AuthenticationServiceBean;
import edu.harvard.iq.dataverse.authorization.CredentialsAuthenticationProvider;
import edu.harvard.iq.dataverse.authorization.exceptions.AuthenticationFailedException;
import edu.harvard.iq.dataverse.authorization.providers.builtin.BuiltinAuthenticationProvider;
import edu.harvard.iq.dataverse.authorization.providers.builtin.BuiltinUserServiceBean;
import edu.harvard.iq.dataverse.common.BundleUtil;
import edu.harvard.iq.dataverse.notification.UserNotificationService;
import edu.harvard.iq.dataverse.persistence.config.EMailValidator;
import edu.harvard.iq.dataverse.persistence.config.ValidateEmail;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUserDisplayInfo;
import edu.harvard.iq.dataverse.persistence.user.NotificationType;
import edu.harvard.iq.dataverse.persistence.user.OAuth2TokenData;
import edu.harvard.iq.dataverse.settings.InstallationConfigService;
import edu.harvard.iq.dataverse.settings.SettingsWrapper;
import edu.harvard.iq.dataverse.util.JsfHelper;
import edu.harvard.iq.dataverse.util.SystemConfig;
import edu.harvard.iq.dataverse.validation.PasswordValidatorServiceBean;
import io.vavr.control.Option;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotBlank;

import javax.ejb.EJB;
import javax.enterprise.context.SessionScoped;
import javax.faces.application.FacesMessage;
import javax.faces.component.UIComponent;
import javax.faces.component.UIInput;
import javax.faces.context.FacesContext;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Backing bean for {@code oauth/welcome.xhtml}, the page that greets new users
 * when they first login through OAuth2.
 *
 * @author michael
 */
@Named("OAuth2FirstLoginPage")
@SessionScoped
public class OAuth2FirstLoginPage implements java.io.Serializable {

    private static final Logger logger = Logger.getLogger(OAuth2FirstLoginPage.class.getCanonicalName());

    @EJB
    AuthenticationServiceBean authenticationSvc;

    @EJB
    BuiltinUserServiceBean builtinUserSvc;

    @EJB
    UserNotificationService userNotificationService;

    @EJB
    SystemConfig systemConfig;

    @EJB
    AuthTestDataServiceBean authTestDataSvc;

    @EJB
    OAuth2TokenDataServiceBean oauth2Tokens;

    @EJB
    InstallationConfigService installationConfigService;

    @Inject
    DataverseSession session;

    @Inject
    private SettingsWrapper settingsWrapper;

    OAuth2UserRecord newUser;

    @NotBlank(message = "{oauth.username}")
    String username;

    @NotBlank(message = "{user.invalidEmail}")
    @ValidateEmail(message = "{user.invalidEmail}")
    String selectedEmail;

    String password;

    String installationName;

    boolean authenticationFailed = false;
    private AuthenticationProvider authProvider;
    private PasswordValidatorServiceBean passwordValidatorService;
    private Locale preferredNotificationsLanguage;


    /**
     * Attempts to init the page. Redirects the user to {@code /} in case the
     * initialization fails.
     *
     * @throws IOException If the redirection fails to be sent. Should not
     *                     happen*
     *                     <p>
     *                     <p>
     *                     <p>
     *                     * Famous last sentences etc.
     */
    public String init() throws IOException {
        logger.fine("init called");
        if (systemConfig.isReadonlyMode()) {
            return "/403.xhtml";
        }

        DevOAuthAccountType devMode = systemConfig.getDevOAuthAccountType();
        logger.log(Level.FINE, "devMode: {0}", devMode);
        if (!DevOAuthAccountType.PRODUCTION.equals(devMode)) {
            if (devMode.toString().startsWith("RANDOM")) {
                Map<String, String> randomUser = authTestDataSvc.getRandomUser();
                String lastName = randomUser.get("lastName");
                String firstName = randomUser.get("firstName");
                String email = null;
                List<String> extraEmails = null;
                String authProviderId = "orcid";
                switch (devMode) {
                    case RANDOM_EMAIL0:
                        authProviderId = "github";
                        break;
                    case RANDOM_EMAIL1:
                        firstName = null;
                        lastName = null;
                        authProviderId = "google";
                        email = randomUser.get("email");
                        break;
                    case RANDOM_EMAIL2:
                        firstName = null;
                        email = randomUser.get("email");
                        extraEmails = new ArrayList<>();
                        extraEmails.add("extra1@example.com");
                        break;
                    case RANDOM_EMAIL3:
                        lastName = null;
                        email = randomUser.get("email");
                        extraEmails = new ArrayList<>();
                        extraEmails.add("extra1@example.com");
                        extraEmails.add("extra2@example.com");
                        break;
                    default:
                        break;
                }
                String randomUsername = randomUser.get("username");
                String eppn = randomUser.get("eppn");
                OAuth2TokenData accessToken = new OAuth2TokenData();
                accessToken.setAccessToken("qwe-addssd-iiiiie");
                setNewUser(new OAuth2UserRecord(authProviderId, eppn, randomUsername, accessToken,
                                                new AuthenticatedUserDisplayInfo(firstName, lastName, email, "myAffiliation", "myPosition"),
                                                extraEmails));
            }
        }

        if (newUser == null) {
            // There's no new user to welcome, so we're out of the "normal" OAuth2 flow.
            // e.g., someone might have directly accessed this page.
            // return to sanity be redirection to /index
            FacesContext.getCurrentInstance().getExternalContext().redirect("/");
            return StringUtils.EMPTY;
        }

        // Suggest the best email we can.
        String emailToSuggest = null;
        String emailFromDisplayInfo = newUser.getDisplayInfo().getEmailAddress();
        if (emailFromDisplayInfo != null && !emailFromDisplayInfo.isEmpty()) {
            emailToSuggest = emailFromDisplayInfo;
        } else {
            List<String> extraEmails = newUser.getAvailableEmailAddresses();
            if (extraEmails != null && !extraEmails.isEmpty()) {
                String firstExtraEmail = extraEmails.get(0);
                if (firstExtraEmail != null && !firstExtraEmail.isEmpty()) {
                    emailToSuggest = firstExtraEmail;
                }
            }
        }
        setSelectedEmail(emailToSuggest);
        preferredNotificationsLanguage = session.getLocale();

        authProvider = authenticationSvc.getAuthenticationProvider(newUser.getServiceId());

        installationName = installationConfigService.getNameOfInstallation();

        return StringUtils.EMPTY;
    }

    public String createNewAccount() {

        AuthenticatedUserDisplayInfo newAud = new AuthenticatedUserDisplayInfo(newUser.getDisplayInfo().getFirstName(),
                                                                               newUser.getDisplayInfo().getLastName(),
                                                                               getSelectedEmail(),
                                                                               newUser.getDisplayInfo().getAffiliation(),
                                                                               newUser.getDisplayInfo().getPosition());
        final AuthenticatedUser user = authenticationSvc.createAuthenticatedUser(newUser.getUserRecordIdentifier(), getUsername(),
                newAud, true, preferredNotificationsLanguage).getOrNull();
        session.setUser(user);
        /**
         * @todo Move this to AuthenticationServiceBean.createAuthenticatedUser
         */
        userNotificationService.sendNotification(user,
                                                 new Timestamp(new Date().getTime()),
                                                 NotificationType.CREATEACC);

        final OAuth2TokenData tokenData = newUser.getTokenData();
        if (tokenData != null) {
            tokenData.setUser(user);
            tokenData.setOauthProviderId(newUser.getServiceId());
            oauth2Tokens.store(tokenData);
        }
        return "/dataverse.xhtml?faces-redirect=true";
    }

    public String convertExistingAccount() {
        BuiltinAuthenticationProvider biap = new BuiltinAuthenticationProvider(builtinUserSvc, passwordValidatorService, authenticationSvc);
        AuthenticationRequest auReq = new AuthenticationRequest();
        final List<CredentialsAuthenticationProvider.Credential> creds = biap.getRequiredCredentials();
        auReq.putCredential(creds.get(0).getKey(), getUsername());
        auReq.putCredential(creds.get(1).getKey(), getPassword());
        try {
            AuthenticatedUser existingUser = authenticationSvc.getUpdateAuthenticatedUser(BuiltinAuthenticationProvider.PROVIDER_ID, auReq);
            authenticationSvc.updateProvider(existingUser, newUser.getServiceId(), newUser.getIdInService());
            builtinUserSvc.removeUser(existingUser.getUserIdentifier());

            session.setUser(existingUser);
            AuthenticationProvider newUserAuthProvider = authenticationSvc.getAuthenticationProvider(newUser.getServiceId());
            JsfHelper.addFlashSuccessMessage(BundleUtil.getStringFromBundle("oauth2.convertAccount.success", newUserAuthProvider.getInfo().getTitle()));

            return "/dataverse.xhtml?faces-redirect=true";

        } catch (AuthenticationFailedException ex) {
            setAuthenticationFailed(true);
            return null;
        }
    }

    public boolean isEmailAvailable() {
        return authenticationSvc.isEmailAddressAvailable(getSelectedEmail());
    }

    /*
     * @todo This was copied from DataverseUserPage and modified so consider
     * consolidating common code (DRY).
     */
    public void validateUserName(FacesContext context, UIComponent toValidate, Object value) {
        String userName = (String) value;
        logger.log(Level.FINE, "Validating username: {0}", userName);
        boolean userNameFound = authenticationSvc.identifierExists(userName);
        if (userNameFound) {
            ((UIInput) toValidate).setValid(false);
            FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR, BundleUtil.getStringFromBundle("user.username.taken"), null);
            context.addMessage(toValidate.getClientId(context), message);
        }
    }

    /*
     * @todo This was copied from DataverseUserPage and modified so consider
     * consolidating common code (DRY).
     */
    public void validateUserEmail(FacesContext context, UIComponent toValidate, Object value) {
        String userEmail = (String) value;
        boolean emailValid = EMailValidator.isEmailValid(userEmail, null);
        if (!emailValid) {
            ((UIInput) toValidate).setValid(false);
            FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR, BundleUtil.getStringFromBundle("oauth2.newAccount.emailInvalid"), null);
            context.addMessage(toValidate.getClientId(context), message);
            return;
        }
        boolean userEmailFound = false;
        AuthenticatedUser aUser = authenticationSvc.getAuthenticatedUserByEmail(userEmail);
        if (aUser != null) {
            userEmailFound = true;
        }
        if (userEmailFound) {
            ((UIInput) toValidate).setValid(false);
            FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR, BundleUtil.getStringFromBundle("user.email.taken"), null);
            context.addMessage(toValidate.getClientId(context), message);
        }
    }

    public String getWelcomeMessage() {
        AuthenticatedUserDisplayInfo displayInfo = newUser.getDisplayInfo();
        String displayName = AuthUtil.getDisplayName(displayInfo.getFirstName(), displayInfo.getLastName());
        if (displayName != null) {
            return BundleUtil.getStringFromBundle("oauth2.newAccount.welcomeWithName", displayName);
        } else {
            return BundleUtil.getStringFromBundle("oauth2.newAccount.welcomeNoName");
        }
    }

    public OAuth2UserRecord getNewUser() {
        return newUser;
    }

    public void setNewUser(OAuth2UserRecord newUser) {
        this.newUser = newUser;
        // uncomment to suggest username to user
        //setUsername(newUser.getUsername());
        setSelectedEmail(newUser.getDisplayInfo().getEmailAddress());
    }

    // It's a design decision to not suggest a username.
    public String getNeverSuggestUsername() {
        return null;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setSelectedEmail(String selectedEmail) {
        this.selectedEmail = selectedEmail;
    }

    public String getSelectedEmail() {
        return selectedEmail;
    }

    public List<String> getExtraEmails() {
        return newUser.getAvailableEmailAddresses();
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isAuthenticationFailed() {
        return authenticationFailed;
    }

    public void setAuthenticationFailed(boolean authenticationFailed) {
        this.authenticationFailed = authenticationFailed;
    }

    public AuthenticationProvider getAuthProvider() {
        return authProvider;
    }

    public String getCreateFromWhereTip() {
        if (authProvider == null) {
            return "Unknown identity provider. Are you a developer playing with :DebugOAuthAccountType? Try adding this provider to the authenticationproviderrow table: " + newUser.getServiceId();
        }
        return BundleUtil.getStringFromBundle("oauth2.newAccount.explanation", authProvider.getInfo().getTitle(), installationName);
    }

    public boolean isConvertFromBuiltinIsPossible() {
        AuthenticationProvider builtinAuthProvider = authenticationSvc.getAuthenticationProvider(BuiltinAuthenticationProvider.PROVIDER_ID);
        return builtinAuthProvider != null;
    }

    public String getSuggestConvertInsteadOfCreate() {
        return BundleUtil.getStringFromBundle("oauth2.newAccount.suggestConvertInsteadOfCreate", installationName);
    }

    public String getConvertTip() {
        if (authProvider == null) {
            return "";
        }
        return BundleUtil.getStringFromBundle("oauth2.convertAccount.explanation", installationName, authProvider.getInfo().getTitle(),
                systemConfig.getGuidesBaseUrl(preferredNotificationsLanguage), systemConfig.getGuidesVersion());
    }

    public List<String> getEmailsToPickFrom() {
        List<String> emailsToPickFrom = new ArrayList<>();
        if (selectedEmail != null) {
            emailsToPickFrom.add(selectedEmail);
        }
        List<String> extraEmails = newUser.getAvailableEmailAddresses();
        if (extraEmails != null && !extraEmails.isEmpty()) {
            for (String extra : newUser.getAvailableEmailAddresses()) {
                if (selectedEmail != null) {
                    if (!selectedEmail.equals(extra)) {
                        emailsToPickFrom.add(extra);
                    }
                } else {
                    emailsToPickFrom.add(extra);
                }
            }
        }
        logger.log(Level.FINE, "{0} emails to pick from: {1}", new Object[]{emailsToPickFrom.size(), emailsToPickFrom});
        return emailsToPickFrom;
    }

    public List<String> getSupportedLanguages() {
        return new ArrayList<>(settingsWrapper.getConfiguredLocales().keySet());
    }

    public String getPreferredNotificationsLanguage() {
        return Option.of(preferredNotificationsLanguage)
                .map(locale -> locale.getLanguage())
                .getOrNull();
    }

    public String getLocalizedPreferredNotificationsLanguage() {
        return getLocalizedDisplayNameForLanguage(preferredNotificationsLanguage);
    }

    public String getLocalizedDisplayNameForLanguage(String language) {
        return getLocalizedDisplayNameForLanguage(Locale.forLanguageTag(language));
    }

    public void validatePreferredNotificationsLanguage(FacesContext context, UIComponent toValidate, Object value) {
        if(Objects.isNull(value)) {
            ((UIInput) toValidate).setValid(false);
            FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR, BundleUtil.getStringFromBundle("user.notificationsLanguage.requiredMessage"), null);
            context.addMessage(toValidate.getClientId(context), message);
        }
    }

    // -------------------- PRIVATE ---------------------

    private String getLocalizedDisplayNameForLanguage(Locale language) {
        return language.getDisplayName(session.getLocale());
    }

    // -------------------- SETTERS --------------------

    public void setPreferredNotificationsLanguage(String preferredNotificationsLanguage) {
        this.preferredNotificationsLanguage = Locale.forLanguageTag(preferredNotificationsLanguage);
    }
}
