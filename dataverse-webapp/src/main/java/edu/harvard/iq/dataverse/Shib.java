package edu.harvard.iq.dataverse;

import edu.harvard.iq.dataverse.authorization.AuthenticationServiceBean;
import edu.harvard.iq.dataverse.authorization.UserRecordIdentifier;
import edu.harvard.iq.dataverse.authorization.groups.GroupServiceBean;
import edu.harvard.iq.dataverse.authorization.providers.shib.ShibAuthenticationProvider;
import edu.harvard.iq.dataverse.authorization.providers.shib.ShibServiceBean;
import edu.harvard.iq.dataverse.authorization.providers.shib.ShibUserNameFields;
import edu.harvard.iq.dataverse.authorization.providers.shib.ShibUtil;
import edu.harvard.iq.dataverse.common.BundleUtil;
import edu.harvard.iq.dataverse.consent.ConsentDto;
import edu.harvard.iq.dataverse.consent.ConsentService;
import edu.harvard.iq.dataverse.notification.UserNotificationService;
import edu.harvard.iq.dataverse.persistence.config.EMailValidator;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUserDisplayInfo;
import edu.harvard.iq.dataverse.persistence.user.BuiltinUser;
import edu.harvard.iq.dataverse.persistence.user.NotificationType;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean.Key;
import edu.harvard.iq.dataverse.settings.SettingsWrapper;
import edu.harvard.iq.dataverse.util.JsfHelper;
import edu.harvard.iq.dataverse.util.SystemConfig;
import io.vavr.control.Option;
import org.apache.commons.lang.StringUtils;
import org.omnifaces.cdi.ViewScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.faces.application.FacesMessage;
import javax.faces.component.UIComponent;
import javax.faces.component.UIInput;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@ViewScoped
@Named("Shib")
public class Shib implements java.io.Serializable {

    private static final Logger logger = LoggerFactory.getLogger(Shib.class);

    @Inject
    DataverseSession session;

    @EJB
    AuthenticationServiceBean authSvc;
    @EJB
    ShibServiceBean shibService;
    @EJB
    DataverseDao dataverseDao;
    @EJB
    GroupServiceBean groupService;
    @EJB
    UserNotificationService userNotificationService;
    @Inject
    private SettingsWrapper settingsWrapper;
    @Inject
    private SystemConfig systemConfig;
    @Inject
    private SettingsServiceBean settingsService;

    @Inject
    private ConsentService consentService;

    private List<ConsentDto> consents = new ArrayList<>();

    HttpServletRequest request;
    private String userPersistentId;
    private String internalUserIdentifer;
    AuthenticatedUserDisplayInfo displayInfo;
    /**
     * @todo Remove this boolean some day? Now the mockups show a popup. Should
     * be re-worked. See also the comment about the lack of a Cancel button.
     */
    private boolean visibleTermsOfUse;
    private final String loginpage = "/loginpage.xhtml";
    private final String identityProviderProblem = "Problem with Identity Provider";

    /**
     * We only have one field in which to store a unique
     * useridentifier/persistentuserid so we have to jam the the "entityId" for
     * a Shibboleth Identity Provider (IdP) and the unique persistent identifier
     * per user into the same field and a separator between these two would be
     * nice, in case we ever want to answer questions like "How many users
     * logged in from Harvard's Identity Provider?".
     * <p>
     * A pipe ("|") is used as a separator because it's considered "unwise" to
     * use in a URL and the "entityId" for a Shibboleth Identity Provider (IdP)
     * looks like a URL:
     * http://stackoverflow.com/questions/1547899/which-characters-make-a-url-invalid
     */
    private String persistentUserIdSeparator = "|";

    /**
     * The Shibboleth Identity Provider (IdP), an "entityId" which often but not
     * always looks like a URL.
     */
    String shibIdp;
    private String builtinUsername;
    private String builtinPassword;
    private String existingEmail;
    private String existingDisplayName;
    private boolean passwordRejected;
    private String displayNameToPersist;
    private String emailToPersist;
    private String affiliationToDisplayAtConfirmation = null;
    private String friendlyNameForInstitution = BundleUtil.getStringFromBundle("shib.welcomeExistingUserMessageDefaultInstitution");
    private State state;
    private String debugSummary;
    /**
     * After a successful login, we will redirect users to this page (unless
     * it's a new account).
     */
    private String redirectPage;
    //    private boolean debug = false;
    private String emailAddress;
    private Locale preferredNotificationsLanguage;

    public enum State {

        INIT,
        REGULAR_LOGIN_INTO_EXISTING_SHIB_ACCOUNT,
        PROMPT_TO_CREATE_NEW_ACCOUNT,
        PROMPT_TO_CONVERT_EXISTING_ACCOUNT,
    }

    public String init() {
        state = State.INIT;
        ExternalContext context = FacesContext.getCurrentInstance().getExternalContext();
        request = (HttpServletRequest) context.getRequest();
        ShibUtil.printAttributes(request);

        /**
         * @todo Investigate why JkEnvVar is null since it may be useful for
         * debugging per https://github.com/IQSS/dataverse/issues/2916 . See
         * also
         * http://stackoverflow.com/questions/30193117/iterate-through-all-servletrequest-attributes#comment49933342_30193117
         * and
         * http://shibboleth.1660669.n2.nabble.com/Why-doesn-t-Java-s-request-getAttributeNames-show-Shibboleth-attributes-tp7616427p7616591.html
         */
        logger.debug("JkEnvVar: " + System.getenv("JkEnvVar"));

        shibService.possiblyMutateRequestInDev(request);

        try {
            shibIdp = getRequiredValueFromAssertion(ShibUtil.shibIdpAttribute);
        } catch (Exception ex) {
            /**
             * @todo is in an antipattern to throw exceptions to control flow?
             * http://c2.com/cgi/wiki?DontUseExceptionsForFlowControl
             *
             * All this exception handling should be handled in the new
             * ShibServiceBean so it's consistently handled by the API as well.
             */
            return StringUtils.EMPTY;
        }
        String shibUserIdentifier;
        try {
            shibUserIdentifier = getRequiredValueFromAssertion(ShibUtil.uniquePersistentIdentifier);
        } catch (Exception ex) {
            return StringUtils.EMPTY;
        }
        String firstName;
        try {
            firstName = getRequiredValueFromAssertion(ShibUtil.firstNameAttribute);
        } catch (Exception ex) {
            return StringUtils.EMPTY;
        }
        String lastName;
        try {
            lastName = getRequiredValueFromAssertion(ShibUtil.lastNameAttribute);
        } catch (Exception ex) {
            return StringUtils.EMPTY;
        }
        ShibUserNameFields shibUserNameFields = ShibUtil.findBestFirstAndLastName(firstName, lastName, null);
        if (shibUserNameFields != null) {
            String betterFirstName = shibUserNameFields.getFirstName();
            if (betterFirstName != null) {
                firstName = betterFirstName;
            }
            String betterLastName = shibUserNameFields.getLastName();
            if (betterLastName != null) {
                lastName = betterLastName;
            }
        }
        String emailAddressInAssertion = null;
        try {
            emailAddressInAssertion = getRequiredValueFromAssertion(ShibUtil.emailAttribute);
        } catch (Exception ex) {
            if (shibIdp.equals(ShibUtil.testShibIdpEntityId)) {
                logger.debug("For " + shibIdp + " (which as of this writing doesn't provide the " + ShibUtil.emailAttribute + " attribute) setting email address to value of eppn: " + shibUserIdentifier);
                emailAddressInAssertion = shibUserIdentifier;
            } else {
                // forcing all other IdPs to send us an an email
                return StringUtils.EMPTY;
            }
        }

        if (!EMailValidator.isEmailValid(emailAddressInAssertion, null)) {
            String msg = "The SAML assertion contained an invalid email address: \"" + emailAddressInAssertion + "\".";
            logger.info(msg);
            msg = BundleUtil.getStringFromBundle("shib.invalidEmailAddress", emailAddressInAssertion);
            String singleEmailAddress = ShibUtil.findSingleValue(emailAddressInAssertion);
            if (EMailValidator.isEmailValid(singleEmailAddress, null)) {
                msg = "Multiple email addresses were asserted by the Identity Provider (" + emailAddressInAssertion + " ). These were sorted and the first was chosen: " + singleEmailAddress;
                logger.info(msg);
                emailAddress = singleEmailAddress;
            } else {
                msg += BundleUtil.getStringFromBundle("shib.emailAddress.error");
                FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, identityProviderProblem, msg));
                return StringUtils.EMPTY;
            }
        } else {
            emailAddress = emailAddressInAssertion;
        }

        String usernameAssertion = getValueFromAssertion(ShibUtil.usernameAttribute);
        internalUserIdentifer = ShibUtil.generateFriendlyLookingUserIdentifer(usernameAssertion, emailAddress);
        logger.debug("friendly looking identifer (backend will enforce uniqueness):" + internalUserIdentifer);

        String affiliation = shibService.getAffiliation(shibIdp, shibService.getDevShibAccountType());
        if (affiliation != null) {
            affiliationToDisplayAtConfirmation = affiliation;
            friendlyNameForInstitution = affiliation;
        }
//        emailAddress = "willFailBeanValidation"; // for testing createAuthenticatedUser exceptions
        displayInfo = new AuthenticatedUserDisplayInfo(firstName, lastName, emailAddress, affiliation, null);

        userPersistentId = shibIdp + persistentUserIdSeparator + shibUserIdentifier;
        ShibAuthenticationProvider shibAuthProvider = new ShibAuthenticationProvider();
        AuthenticatedUser au = authSvc.lookupUser(shibAuthProvider.getId(), userPersistentId);
        if (au != null) {
            state = State.REGULAR_LOGIN_INTO_EXISTING_SHIB_ACCOUNT;
            logger.debug("Found user based on " + userPersistentId + ". Logging in.");

            if (!systemConfig.isReadonlyMode()) {
                logger.debug("Updating display info for " + au.getName());
                authSvc.updateAuthenticatedUser(au, displayInfo);
            } else {
                logger.warn("Can't update user display info in readonlyMode");
            }
            logInUserAndSetShibAttributes(au);
            String prettyFacesHomePageString = getPrettyFacesHomePageString(false);
            try {
                FacesContext.getCurrentInstance().getExternalContext().redirect(prettyFacesHomePageString);
            } catch (IOException ex) {
                logger.info("Unable to redirect user to homepage at " + prettyFacesHomePageString);
            }
        } else {
            state = State.PROMPT_TO_CREATE_NEW_ACCOUNT;
            displayNameToPersist = displayInfo.getTitle();
            emailToPersist = emailAddress;

            if (systemConfig.isReadonlyMode()) {
                return "/403.xhtml";
            }
            /**
             * @todo for Harvard we plan to use the value(s) from
             * eduPersonScopedAffiliation which
             * http://iam.harvard.edu/resources/saml-shibboleth-attributes says
             * can be One or more of the following values: faculty, staff,
             * student, affiliate, and member.
             *
             * http://dataverse.nl plans to use
             * urn:mace:dir:attribute-def:eduPersonAffiliation per
             * http://irclog.iq.harvard.edu/dataverse/2015-02-13#i_16265 . Can
             * they configure shibd to map eduPersonAffiliation to
             * eduPersonScopedAffiliation?
             */
//            positionToPersist = "FIXME";
            logger.debug("Couldn't find authenticated user based on " + userPersistentId);
            visibleTermsOfUse = true;
            /**
             * Using the email address from the IdP, try to find an existing
             * user. For TestShib we convert the "eppn" to an email address.
             *
             * If found, prompt for password and offer to convert.
             *
             * If not found, create a new account. It must be a new user.
             */
            String emailAddressToLookUp = emailAddress;
            if (existingEmail != null) {
                emailAddressToLookUp = existingEmail;
            }
            AuthenticatedUser existingAuthUserFoundByEmail = shibService.findAuthUserByEmail(emailAddressToLookUp);
            BuiltinUser existingBuiltInUserFoundByEmail = null;
            if (existingAuthUserFoundByEmail != null) {
                existingDisplayName = existingAuthUserFoundByEmail.getName();
                existingBuiltInUserFoundByEmail = shibService.findBuiltInUserByAuthUserIdentifier(existingAuthUserFoundByEmail.getUserIdentifier());
                if (existingBuiltInUserFoundByEmail != null) {
                    state = State.PROMPT_TO_CONVERT_EXISTING_ACCOUNT;

                    debugSummary = "getting username from the builtin user we looked up via email";
                    builtinUsername = existingBuiltInUserFoundByEmail.getUserName();
                } else {
                    debugSummary = "Could not find a builtin account based on the username. Here we should simply create a new Shibboleth user";
                }
            } else {
                debugSummary = "Could not find an auth user based on email address";
            }
            consents = consentService.prepareConsentsForView(session.getLocale());

        }
        logger.debug("Debug summary: " + debugSummary + " (state: " + state + ").");
        logger.debug("redirectPage: " + redirectPage);
        return StringUtils.EMPTY;
    }

    public String confirmAndCreateAccount() {
        ShibAuthenticationProvider shibAuthProvider = new ShibAuthenticationProvider();
        String lookupStringPerAuthProvider = userPersistentId;
        AuthenticatedUser au = null;
        try {
            au = authSvc.createAuthenticatedUser(
                    new UserRecordIdentifier(shibAuthProvider.getId(), lookupStringPerAuthProvider), internalUserIdentifer, displayInfo, true);
        } catch (EJBException ex) {
            /**
             * @todo Show the ConstraintViolationException, if any.
             */
            logger.info("Couldn't create user " + userPersistentId + " due to exception: " + ex.getCause());
        }
        if (au != null) {
            logger.debug("created user " + au.getIdentifier());
            logInUserAndSetShibAttributes(au);
            /**
             * @todo Move this to
             * AuthenticationServiceBean.createAuthenticatedUser
             */
            userNotificationService.sendNotification(au,
                                                     new Timestamp(new Date().getTime()),
                                                     NotificationType.CREATEACC);

            consentService.executeActionsAndSaveAcceptedConsents(consents, au);
            return "/dataverseuser.xhtml?selectTab=accountInfo&faces-redirect=true";
        } else {
            JsfHelper.addFlashErrorMessage(BundleUtil.getStringFromBundle("shib.createUser.fail"));
        }
        return getPrettyFacesHomePageString(true);
    }

    public String confirmAndConvertAccount() {
        visibleTermsOfUse = false;
        ShibAuthenticationProvider shibAuthProvider = new ShibAuthenticationProvider();
        String lookupStringPerAuthProvider = userPersistentId;
        logger.debug("builtin username: " + builtinUsername);
        AuthenticatedUser builtInUserToConvert = authSvc.canLogInAsBuiltinUser(builtinUsername, builtinPassword);
        if (builtInUserToConvert != null) {
            // TODO: Switch from authSvc.convertBuiltInToShib to authSvc.convertBuiltInUserToRemoteUser
            AuthenticatedUser au = authSvc.convertBuiltInUserToRemoteUser(builtInUserToConvert, shibAuthProvider.getId(), lookupStringPerAuthProvider);
            if (au != null) {
                authSvc.updateAuthenticatedUser(au, displayInfo);
                logInUserAndSetShibAttributes(au);
                consentService.executeActionsAndSaveAcceptedConsents(consents, au);
                debugSummary = "Local account validated and successfully converted to a Shibboleth account. The old account username was " + builtinUsername;
                JsfHelper.addFlashSuccessMessage(BundleUtil.getStringFromBundle("dataverse.shib.success"));
                return "/dataverseuser.xhtml?selectTab=accountInfo&faces-redirect=true";
            } else {
                debugSummary = "Local account validated but unable to convert to Shibboleth account.";
            }
        } else {
            passwordRejected = true;
            debugSummary = "Username/password combination for local account was invalid";
        }
        return null;
    }

    private void logInUserAndSetShibAttributes(AuthenticatedUser au) {
        au.setShibIdentityProvider(shibIdp);
        session.setUser(au);
        logger.debug("Groups for user " + au.getId() + " (" + au.getIdentifier() + "): " + getGroups(au));
    }

    public List<String> getGroups(AuthenticatedUser au) {
        List<String> groups = new ArrayList<>();
        groupService.groupsFor(au, null).stream().forEach((group) -> {
            groups.add(group.getDisplayName() + " (" + group.getIdentifier() + ")");
        });
        return groups;
    }

    /**
     * @todo The mockups show a Cancel button but because we're using the
     * "requiredCheckboxValidator" you are forced to agree to Terms of Use
     * before clicking Cancel! Argh! The mockups show how we want to display
     * Terms of Use in a popup anyway so this should all be re-done. No time
     * now. Here's the mockup:
     * https://iqssharvard.mybalsamiq.com/projects/loginwithshibboleth-version3-dataverse40/Dataverse%20Account%20III%20-%20Agree%20Terms%20of%20Use
     */
    public String cancel() {
        return loginpage + "?faces-redirect=true";
    }

    /**
     * @return The trimmed value of a Shib attribute (if non-empty) or null.
     * @todo Move this to ShibUtil
     */
    private String getValueFromAssertion(String key) {
        Object attribute = request.getAttribute(key);
        if (attribute != null) {
            String attributeValue = attribute.toString();
            if(settingsService.isTrueForKey(Key.ShibAttributeCharacterSetConversionEnabled)) {
                attributeValue = new String(attributeValue.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
            }
            String trimmedValue = attributeValue.trim();
            if (!trimmedValue.isEmpty()) {
                logger.debug("The SAML assertion for \"" + key + "\" (optional) was \"" + attributeValue + "\" and was trimmed to \"" + trimmedValue + "\".");
                return trimmedValue;
            } else {
                logger.debug("The SAML assertion for \"" + key + "\" (optional) was \"" + attributeValue + "\" and was trimmed to \"" + trimmedValue + "\" (empty string). Returing null.");
                return null;
            }
        } else {
            logger.debug("The SAML assertion for \"" + key + "\" (optional) was null.");
            return null;
        }
    }

    /**
     * @return The trimmed value of a Shib attribute (if non-empty) or null.
     * @todo Move this to ShibUtil. More objects might be required since
     * sometimes we want to show messages, etc.
     */
    private String getRequiredValueFromAssertion(String key) throws Exception {
        Object attribute = request.getAttribute(key);
        if (attribute == null) {
            String msg = "The SAML assertion for \"" + key + "\" was null. Please contact support.";
            logger.info(msg);
            boolean showMessage = true;
            if (shibIdp.equals(ShibUtil.testShibIdpEntityId) && key.equals(ShibUtil.emailAttribute)) {
                showMessage = false;
            }
            if (showMessage) {
                FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, identityProviderProblem, BundleUtil.getStringFromBundle("shib.nullerror", key)));
            }
            throw new Exception(msg);
        }
        String attributeValue = attribute.toString();
        if (attributeValue.isEmpty()) {
            throw new Exception(key + " was empty");
        }
        if(settingsService.isTrueForKey(Key.ShibAttributeCharacterSetConversionEnabled)) {
            attributeValue = new String(attributeValue.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
        }
        String trimmedValue = attributeValue.trim();
        logger.debug("The SAML assertion for \"" + key + "\" (required) was \"" + attributeValue + "\" and was trimmed to \"" + trimmedValue + "\".");
        return trimmedValue;
    }

    public String getRootDataverseAlias() {
        Dataverse rootDataverse = dataverseDao.findRootDataverse();
        if (rootDataverse != null) {
            String rootDvAlias = rootDataverse.getAlias();
            return rootDvAlias;
        }
        return null;
    }

    /**
     * @param includeFacetDashRedirect if true, include "faces-redirect=true" in
     *                                 the string
     * @todo Once https://github.com/IQSS/dataverse/issues/1519 is done, revisit
     * this method and have the home page be "/" rather than "/dataverses/root".
     * @todo Like builtin users, Shibboleth should benefit from redirectPage
     * logic per https://github.com/IQSS/dataverse/issues/1551
     */
    public String getPrettyFacesHomePageString(boolean includeFacetDashRedirect) {
        if (redirectPage != null) {
            return redirectPage;
        }
        String plainHomepageString = "/dataverse.xhtml";
        String rootDvAlias = getRootDataverseAlias();
        if (includeFacetDashRedirect) {
            if (rootDvAlias != null) {
                return plainHomepageString + "?alias=" + rootDvAlias + "&faces-redirect=true";
            } else {
                return plainHomepageString + "?faces-redirect=true";
            }
        } else if (rootDvAlias != null) {
            /**
             * @todo Is there a constant for "/dataverse/" anywhere? I guess
             * we'll just hard-code it here.
             */
            return "/dataverse/" + rootDvAlias;
        } else {
            return plainHomepageString;
        }
    }

    public boolean isInit() {
        return state.equals(State.INIT);
    }

    public boolean isOfferToCreateNewAccount() {
        return state.equals(State.PROMPT_TO_CREATE_NEW_ACCOUNT);
    }

    public boolean isOfferToConvertExistingAccount() {
        return state.equals(State.PROMPT_TO_CONVERT_EXISTING_ACCOUNT);
    }

    public String getDisplayNameToPersist() {
        return displayNameToPersist;
    }

    public String getEmailToPersist() {
        return emailToPersist;
    }

    public String getAffiliationToDisplayAtConfirmation() {
        return affiliationToDisplayAtConfirmation;
    }

    public String getExistingEmail() {
        return existingEmail;
    }

    public void setExistingEmail(String existingEmail) {
        this.existingEmail = existingEmail;
    }

    public String getExistingDisplayName() {
        return existingDisplayName;
    }

    public boolean isPasswordRejected() {
        return passwordRejected;
    }

    public String getFriendlyNameForInstitution() {
        return friendlyNameForInstitution;
    }

    public void setFriendlyNameForInstitution(String friendlyNameForInstitution) {
        this.friendlyNameForInstitution = friendlyNameForInstitution;
    }

    public State getState() {
        return state;
    }

    public boolean isVisibleTermsOfUse() {
        return visibleTermsOfUse;
    }

    public String getBuiltinUsername() {
        return builtinUsername;
    }

    public void setBuiltinUsername(String builtinUsername) {
        this.builtinUsername = builtinUsername;
    }

    public String getBuiltinPassword() {
        return builtinPassword;
    }

    public void setBuiltinPassword(String builtinPassword) {
        this.builtinPassword = builtinPassword;
    }

    public String getDebugSummary() {
        return debugSummary;
    }

    public void setDebugSummary(String debugSummary) {
        this.debugSummary = debugSummary;
    }

    public String getRedirectPage() {
        return redirectPage;
    }

    public void setRedirectPage(String redirectPage) {
        this.redirectPage = redirectPage;
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

    public List<ConsentDto> getConsents() {
        return consents;
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
