package edu.harvard.iq.dataverse.authorization.providers.builtin;

import edu.harvard.iq.dataverse.DataverseDao;
import edu.harvard.iq.dataverse.DataverseSession;
import edu.harvard.iq.dataverse.PermissionsWrapper;
import edu.harvard.iq.dataverse.authorization.AuthUtil;
import edu.harvard.iq.dataverse.authorization.AuthenticationProvider;
import edu.harvard.iq.dataverse.authorization.AuthenticationServiceBean;
import edu.harvard.iq.dataverse.authorization.EditableAccountField;
import edu.harvard.iq.dataverse.authorization.UserRecordIdentifier;
import edu.harvard.iq.dataverse.authorization.providers.common.BaseUserPage;
import edu.harvard.iq.dataverse.authorization.providers.shib.ShibAuthenticationProvider;
import edu.harvard.iq.dataverse.common.BundleUtil;
import edu.harvard.iq.dataverse.consent.ConsentDto;
import edu.harvard.iq.dataverse.consent.ConsentService;
import edu.harvard.iq.dataverse.mail.confirmemail.ConfirmEmailException;
import edu.harvard.iq.dataverse.mail.confirmemail.ConfirmEmailServiceBean;
import edu.harvard.iq.dataverse.mail.confirmemail.ConfirmEmailUtil;
import edu.harvard.iq.dataverse.notification.NotificationObjectType;
import edu.harvard.iq.dataverse.notification.UserNotificationService;
import edu.harvard.iq.dataverse.notification.dto.UserNotificationDTO;
import edu.harvard.iq.dataverse.notification.dto.UserNotificationMapper;
import edu.harvard.iq.dataverse.persistence.config.EMailValidator;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUserDisplayInfo;
import edu.harvard.iq.dataverse.persistence.user.BuiltinUser;
import edu.harvard.iq.dataverse.persistence.user.ConfirmEmailData;
import edu.harvard.iq.dataverse.persistence.user.NotificationType;
import edu.harvard.iq.dataverse.persistence.user.UserNameValidator;
import edu.harvard.iq.dataverse.persistence.user.UserNotificationRepository;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import edu.harvard.iq.dataverse.settings.SettingsWrapper;
import edu.harvard.iq.dataverse.users.LazyUserNotificationsDataModel;
import edu.harvard.iq.dataverse.util.JsfHelper;
import edu.harvard.iq.dataverse.util.SystemConfig;
import edu.harvard.iq.dataverse.validation.PasswordValidatorServiceBean;
import io.vavr.control.Option;
import org.apache.commons.lang.StringUtils;
import org.hibernate.validator.constraints.NotBlank;
import org.omnifaces.cdi.ViewScoped;
import org.primefaces.event.SelectEvent;
import org.primefaces.event.TabChangeEvent;
import org.primefaces.event.ToggleSelectEvent;
import org.primefaces.event.UnselectEvent;
import org.primefaces.model.LazyDataModel;

import javax.ejb.EJB;
import javax.faces.application.FacesMessage;
import javax.faces.component.UIComponent;
import javax.faces.component.UIInput;
import javax.faces.context.FacesContext;
import javax.faces.event.ActionEvent;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.stream.Collectors.toList;

@ViewScoped
@Named("DataverseUserPage")
public class DataverseUserPage extends BaseUserPage {

    private static final Logger logger = Logger.getLogger(DataverseUserPage.class.getCanonicalName());

    public enum EditMode {
        CREATE, EDIT, CHANGE_PASSWORD
    }

    private static final int ADDITIONAL_MESSAGE_MAX_LENGTH = 512;

    @Inject
    DataverseSession session;
    @EJB
    DataverseDao dataverseDao;
    @EJB
    private UserNotificationService userNotificationService;
    @EJB
    private UserNotificationRepository userNotificationRepository;
    @EJB
    private UserNotificationMapper userNotificationMapper;
    @EJB
    BuiltinUserServiceBean builtinUserService;
    @EJB
    AuthenticationServiceBean authenticationService;
    @EJB
    ConfirmEmailServiceBean confirmEmailService;
    @EJB
    SystemConfig systemConfig;
    @EJB
    PasswordValidatorServiceBean passwordValidatorService;
    @Inject
    SettingsWrapper settingsWrapper;
    @Inject
    SettingsServiceBean settingsService;
    @Inject
    PermissionsWrapper permissionsWrapper;

    @Inject
    private ConsentService consentService;

    private AuthenticatedUser currentUser;
    private AuthenticatedUserDisplayInfo userDisplayInfo;
    private transient AuthenticationProvider userAuthProvider;
    private EditMode editMode;
    private String redirectPage = "dataverse.xhtml";

    @NotBlank(message = "{password.retype}")
    private String inputPassword;

    @NotBlank(message = "{password.current}")
    private String currentPassword;

    private LazyDataModel<UserNotificationDTO> notificationsList;
    private Set<Long> selectedNotificationIds = new HashSet<>();
    private boolean selectedAllNotifications = false;
    private int activeIndex;
    private String selectTab = "somedata";

    private Locale preferredNotificationsLanguage = null;

    private String username;
    private List<String> passwordErrors;
    private List<ConsentDto> consents = new ArrayList<>();

    private Boolean notificationLanguageSelectionEnabled;

    // -------------------- GETTERS --------------------

    public int getActiveIndex() {
        return activeIndex;
    }

    public EditMode getChangePasswordMode() {
        return EditMode.CHANGE_PASSWORD;
    }

    public List<ConsentDto> getConsents() {
        return consents;
    }

    public String getCurrentPassword() {
        return currentPassword;
    }

    public AuthenticatedUser getCurrentUser() {
        return currentUser;
    }

    public EditMode getEditMode() {
        return editMode;
    }

    public String getInputPassword() {
        return inputPassword;
    }

    public LazyDataModel<UserNotificationDTO> getNotificationsList() {
        return notificationsList;
    }

    public List<UserNotificationDTO> getSelectedNotifications() {
        return notificationsList.getWrappedData().stream()
                .filter(notification -> selectedAllNotifications || selectedNotificationIds.contains(notification.getId()))
                .collect(toList());
    }

    public boolean getSelectedAllNotifications() {
        return selectedAllNotifications;
    }

    public String getRedirectPage() {
        return redirectPage;
    }

    public String getSelectTab() {
        return selectTab;
    }

    public AuthenticatedUserDisplayInfo getUserDisplayInfo() {
        return userDisplayInfo;
    }

    public String getUsername() {
        return username;
    }

    public Boolean getNotificationLanguageSelectionEnabled() {
        return notificationLanguageSelectionEnabled;
    }

    // -------------------- LOGIC --------------------

    public String init() {
        // prevent creating a user if signup not allowed.
        boolean signupAllowed = systemConfig.isSignupAllowed();
        notificationLanguageSelectionEnabled = settingsWrapper.isLocalesConfigured();

        if (editMode == EditMode.CREATE && !signupAllowed) {
            return "/403.xhtml";
        }

        if (editMode == EditMode.CREATE) {

            if (isUserAuthenticated()) {
                editMode = null; // we can't be in create mode for an existing user
            } else {
                // in create mode for new user
                userDisplayInfo = new AuthenticatedUserDisplayInfo();
                consents = consentService.prepareConsentsForView(session.getLocale());
                if (!notificationLanguageSelectionEnabled) {
                    preferredNotificationsLanguage = Locale.forLanguageTag(getSupportedLanguages().get(0));
                }
                return "";
            }
        }

        if (!isUserAuthenticated()) {
            return permissionsWrapper.notAuthorized();
        }

        setCurrentUser((AuthenticatedUser) session.getUser());
        userAuthProvider = authenticationService.lookupProvider(currentUser);
        preferredNotificationsLanguage = currentUser.getNotificationsLanguage();

        if (editMode == EditMode.EDIT && !isAccountDetailsEditable()
            || editMode == EditMode.CHANGE_PASSWORD && !isPasswordEditable()) {
            return permissionsWrapper.notAuthorized();
        }

        switch (selectTab) {
        case "notifications":
            activeIndex = 1;
            displayNotification();
            break;
        case "accountInfo":
            activeIndex = 2;
            break;
        case "apiTokenTab":
            activeIndex = 3;
            break;
        default:
            activeIndex = 0;
            break;
        }

        return "";
    }

    public boolean isUserAuthenticated() {
        return session.getUser().isAuthenticated();
    }

    public void edit(ActionEvent e) {
        editMode = EditMode.EDIT;
    }

    public void changePassword(ActionEvent e) {
        editMode = EditMode.CHANGE_PASSWORD;
    }

    public void validateUserName(FacesContext context, UIComponent toValidate, Object value) {
        String userName = (String) value;
        boolean userNameFound = authenticationService.identifierExists(userName);

        // SF fix for issue 3752
        // checks if username has any invalid characters
        boolean userNameValid = UserNameValidator.isUserNameValid(userName, null);

        if (editMode == EditMode.CREATE && userNameFound) {
            ((UIInput) toValidate).setValid(false);
            FacesMessage message = new FacesMessage(
                    FacesMessage.SEVERITY_ERROR, BundleUtil.getStringFromBundle("user.username.taken"), null);
            context.addMessage(toValidate.getClientId(context), message);
        }

        if (editMode == EditMode.CREATE && !userNameValid) {
            ((UIInput) toValidate).setValid(false);
            FacesMessage message = new FacesMessage(
                    FacesMessage.SEVERITY_ERROR, BundleUtil.getStringFromBundle("user.username.invalid"), null);
            context.addMessage(toValidate.getClientId(context), message);
        }
    }

    public void validatePreferredNotificationsLanguage(FacesContext context, UIComponent toValidate, Object value) {
        if (Objects.isNull(value)) {
            ((UIInput) toValidate).setValid(false);
            FacesMessage message = new FacesMessage(
                    FacesMessage.SEVERITY_ERROR, BundleUtil.getStringFromBundle("user.notificationsLanguage.requiredMessage"), null);
            context.addMessage(toValidate.getClientId(context), message);
        }
    }

    public void validateUserEmail(FacesContext context, UIComponent toValidate, Object value) {
        String userEmail = (String) value;
        boolean emailValid = EMailValidator.isEmailValid(userEmail, null);
        if (!emailValid) {
            ((UIInput) toValidate).setValid(false);
            FacesMessage message = new FacesMessage(
                    FacesMessage.SEVERITY_ERROR, BundleUtil.getStringFromBundle("oauth2.newAccount.emailInvalid"), null);
            context.addMessage(toValidate.getClientId(context), message);
            logger.info("Email is not valid: " + userEmail);
            return;
        }
        boolean userEmailFound = false;
        AuthenticatedUser aUser = authenticationService.getAuthenticatedUserByEmail(userEmail);
        if (editMode == EditMode.CREATE) {
            if (aUser != null) {
                userEmailFound = true;
            }
        } else {

            // In edit mode...
            // if there's a match on edit make sure that the email belongs to the
            // user doing the editing by checking ids
            if (aUser != null && !aUser.getId().equals(currentUser.getId())) {
                userEmailFound = true;
            }
        }
        if (userEmailFound) {
            ((UIInput) toValidate).setValid(false);

            FacesMessage message = new FacesMessage(
                    FacesMessage.SEVERITY_ERROR, BundleUtil.getStringFromBundle("user.email.taken"), null);
            context.addMessage(toValidate.getClientId(context), message);
        }
    }

    public void validateNewPassword(FacesContext context, UIComponent toValidate, Object value) {
        String password = (String) value;
        if (StringUtils.isBlank(password)) {
            logger.log(Level.WARNING, "new password is blank");

            ((UIInput) toValidate).setValid(false);

            FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    BundleUtil.getStringFromBundle("passwdVal.passwdReset.valFacesError"),
                    BundleUtil.getStringFromBundle("passwdVal.passwdReset.valFacesErrorDesc"));
            context.addMessage(toValidate.getClientId(context), message);
            return;

        }

        final List<String> errors = passwordValidatorService.validate(password, new Date(), false);
        this.passwordErrors = errors;
        if (!errors.isEmpty()) {
            ((UIInput) toValidate).setValid(false);
        }
    }

    public String save() {
        boolean passwordChanged = false;
        if (editMode == EditMode.CHANGE_PASSWORD) {
            final AuthenticationProvider prv = getUserAuthProvider();
            if (prv.isPasswordUpdateAllowed()) {
                if (!prv.verifyPassword(currentUser.getAuthenticatedUserLookup().getPersistentUserId(), currentPassword)) {
                    FacesContext.getCurrentInstance().addMessage("currentPassword",
                         new FacesMessage(FacesMessage.SEVERITY_ERROR, BundleUtil.getStringFromBundle("user.error.wrongPassword"), null));
                    return null;
                }
                prv.updatePassword(currentUser.getAuthenticatedUserLookup().getPersistentUserId(), inputPassword);
                passwordChanged = true;
            } else {
                // erroneous state - we can't change the password for this user, so should not have gotten here. Log and bail out.
                logger.log(Level.WARNING,
                        "Attempt to change a password on {0}, whose provider ({1}) does not support password change",
                           new Object[] { currentUser.getIdentifier(), prv });
                JsfHelper.addErrorMessage(BundleUtil.getStringFromBundle("user.error.cannotChangePassword"), "");
                return null;
            }
        }
        if (editMode == EditMode.CREATE) {
            // Create a new built-in user.
            BuiltinUser builtinUser = new BuiltinUser();
            builtinUser.setUserName(getUsername());
            builtinUser.updateEncryptedPassword(PasswordEncryption.get().encrypt(inputPassword),
                                                PasswordEncryption.getLatestVersionNumber());

            AuthenticatedUser au = authenticationService.createAuthenticatedUser(
                    new UserRecordIdentifier(BuiltinAuthenticationProvider.PROVIDER_ID, builtinUser.getUserName()),
                    builtinUser.getUserName(), userDisplayInfo, false, preferredNotificationsLanguage)
                    .getOrNull();

            // Authenticated user registered. Save the new bulitin, and log in.
            builtinUserService.save(builtinUser);
            session.setUser(au);
            /**
             * @todo Move this to
             * AuthenticationServiceBean.createAuthenticatedUser
             */
            userNotificationService.sendNotificationWithEmail(au, new Timestamp(new Date().getTime()),
                    NotificationType.CREATEACC, null, NotificationObjectType.AUTHENTICATED_USER);

            consentService.executeActionsAndSaveAcceptedConsents(consents, au);
            // go back to where user came from

            // (but if they came from the login page, then send them to the
            // root dataverse page instead. the only situation where we do
            // want to send them back to the login page is if they hit
            // 'cancel'.

            if ("/loginpage.xhtml".equals(redirectPage) || "loginpage.xhtml".equals(redirectPage)) {
                redirectPage = "/dataverse.xhtml";
            }

            if ("dataverse.xhtml".equals(redirectPage)) {
                redirectPage = redirectPage + "?alias=" + dataverseDao.findRootDataverse().getAlias();
            }

            try {
                redirectPage = URLDecoder.decode(redirectPage, "UTF-8");
            } catch (UnsupportedEncodingException ex) {
                logger.log(Level.SEVERE, "Server does not support 'UTF-8' encoding.", ex);
                redirectPage = "dataverse.xhtml?alias=" + dataverseDao.findRootDataverse().getAlias();
            }

            logger.log(Level.FINE, "Sending user to = {0}", redirectPage);
            return redirectPage + (!redirectPage.contains("?") ? "?" : "&") + "faces-redirect=true";

            // Happens if user is logged out while editing
        } else if (!isUserAuthenticated()) {
            logger.info("Redirecting");
            return permissionsWrapper.notAuthorized() + "faces-redirect=true";
        } else {
            String emailBeforeUpdate = currentUser.getEmail();
            AuthenticatedUser savedUser = authenticationService.updateAuthenticatedUser(currentUser,
                                                                                        userDisplayInfo,
                                                                                        preferredNotificationsLanguage);
            String emailAfterUpdate = savedUser.getEmail();
            editMode = null;
            StringBuilder msg = new StringBuilder(
                    BundleUtil.getStringFromBundle(
                            passwordChanged ? "userPage.passwordChanged" : "userPage.informationUpdated"));
            if (!emailBeforeUpdate.equals(emailAfterUpdate)) {
                String expTime = ConfirmEmailUtil.friendlyExpirationTime(settingsService.getValueForKeyAsLong(
                        SettingsServiceBean.Key.MinutesUntilConfirmEmailTokenExpires));

                // delete unexpired token, if it exists (clean slate)
                confirmEmailService.deleteTokenForUser(currentUser);
                try {
                    confirmEmailService.beginConfirm(currentUser);
                } catch (ConfirmEmailException ex) {
                    logger.log(Level.INFO, "Unable to send email confirmation link to user id {0}", savedUser.getId());
                }
                session.setUser(currentUser);
                JsfHelper.addFlashSuccessMessage(BundleUtil.getStringFromBundle("confirmEmail.changed", currentUser.getEmail(), expTime));
            } else {
                JsfHelper.addFlashSuccessMessage(msg.toString());
            }
            return null;
        }
    }

    public String cancel() {
        if (editMode == EditMode.CREATE) {
            return "/dataverse.xhtml?alias=" + dataverseDao.findRootDataverse().getAlias() + "&faces-redirect=true";
        }
        editMode = null;
        return null;
    }

    public boolean canRemoveNotifications() {
        return !systemConfig.isReadonlyMode();
    }

    public void deleteSelectedNotifications() {
        if (selectedAllNotifications) {
            userNotificationRepository.deleteByUser(currentUser.getId());
        } else {
            userNotificationRepository.deleteByIds(selectedNotificationIds);
            selectedNotificationIds.clear();
        }
    }

    public void onNotificationSelect(SelectEvent event) {
        UserNotificationDTO selectedNotification = (UserNotificationDTO) event.getObject();
        selectedNotificationIds.add(selectedNotification.getId());
        setSelectedAllNotifications(false);
    }

    public void onNotificationUnSelect(UnselectEvent event) {
        UserNotificationDTO selectedNotification = (UserNotificationDTO) event.getObject();
        selectedNotificationIds.remove(selectedNotification.getId());
        setSelectedAllNotifications(false);
    }

    public void onNotificationToggleSelectPage(ToggleSelectEvent event) {
        selectedAllNotifications = false;
        if (event.isSelected()) {
            notificationsList.getWrappedData().forEach(d -> selectedNotificationIds.add(d.getId()));
        } else {
            notificationsList.getWrappedData().forEach(d -> selectedNotificationIds.remove(d.getId()));
        }
    }

    public void selectAllNotifications() {
        selectedAllNotifications = true;
        selectedNotificationIds.clear();
    }

    public void clearSelection() {
        selectedAllNotifications = false;
        selectedNotificationIds.clear();
    }

    public int getNumberOfSelectedNotifications() {
        if (selectedAllNotifications) {
            return notificationsList.getRowCount();
        }

        return selectedNotificationIds.size();
    }

    public int getPageCount() {
        return (notificationsList.getRowCount() / notificationsList.getPageSize()) + 1;
    }

    public int getNotificationCount() {
        return notificationsList.getRowCount();
    }

    public void onTabChange(TabChangeEvent event) {
        if ("notifications".equals(event.getTab().getId())) {
            displayNotification();
        }
    }

    public void sendConfirmEmail() {
        logger.fine("called sendConfirmEmail()");
        String userEmail = currentUser.getEmail();

        try {
            confirmEmailService.beginConfirm(currentUser);
            String expirationString =
                    ConfirmEmailUtil.friendlyExpirationTime(settingsService.getValueForKeyAsLong(SettingsServiceBean.Key.MinutesUntilConfirmEmailTokenExpires));
            JsfHelper.addFlashSuccessMessage(BundleUtil.getStringFromBundle("confirmEmail.submitRequest.success",
                                                                            userEmail, expirationString));
        } catch (ConfirmEmailException ex) {
            Logger.getLogger(DataverseUserPage.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Determines whether the button to send a verification email appears on user page
     */
    public boolean showVerifyEmailButton() {
        if (systemConfig.isReadonlyMode()) {
            return false;
        }
        final Timestamp emailConfirmed = currentUser.getEmailConfirmed();
        boolean allTokensExpired = confirmEmailService.findAllConfirmEmailDataByUser(currentUser)
                .stream()
                .allMatch(ConfirmEmailData::isExpired);
        return !getUserAuthProvider().isEmailVerified()
                && allTokensExpired
                && emailConfirmed == null;
    }

    public boolean isEmailIsVerified() {
        return currentUser.getEmailConfirmed() != null &&
                confirmEmailService.findSingleConfirmEmailDataByUser(currentUser) == null;
    }

    public boolean isEmailNotVerified() {
        return currentUser.getEmailConfirmed() == null ||
                confirmEmailService.findSingleConfirmEmailDataByUser(currentUser) != null;
    }

    public AuthenticationProvider getUserAuthProvider() {
        if (userAuthProvider == null) {
            userAuthProvider = authenticationService.lookupProvider(currentUser);
        }
        return userAuthProvider;
    }

    public String getUserLocalizedNotificationsLanguageForDisplay() {
        String displayLanguage = StringUtils.capitalize(
                currentUser.getNotificationsLanguage().getDisplayLanguage(session.getLocale()));

        return isUserLanguageConfigured()
                ? displayLanguage
                : String.format("%s %s", displayLanguage,
                    BundleUtil.getStringFromBundle("user.notificationsLanguage.notSupported"));
    }

    public boolean isPasswordEditable() {
        return !systemConfig.isReadonlyMode() && getUserAuthProvider().isPasswordUpdateAllowed();
    }

    public boolean isAccountDetailsEditable() {
        return !systemConfig.isReadonlyMode() && getUserAuthProvider().isUserInfoUpdateAllowed();
    }

    public boolean showShibAccountMigrateHelpMessage() {
        return getUserAuthProvider() instanceof ShibAuthenticationProvider;
    }

    public boolean showOAuthAccountMigrateHelpMessage() {
        return getUserAuthProvider().isOAuthProvider();
    }

    public void setCurrentUser(AuthenticatedUser currentUser) {
        this.currentUser = currentUser;
        userDisplayInfo = currentUser.getDisplayInfo();
        username = currentUser.getUserIdentifier();
    }

    public boolean isNonLocalLoginEnabled() {
        return AuthUtil.isNonLocalLoginEnabled(authenticationService.getAuthenticationProviders());
    }

    public String getLimitedAdditionalMessage(String additionalMessage) {
        return additionalMessage.length() <= ADDITIONAL_MESSAGE_MAX_LENGTH
                ? additionalMessage.endsWith(".")
                    ? additionalMessage
                    : additionalMessage + "."
                : BundleUtil.getStringFromBundle("notification.limitedAdditionalMessage",
                    truncateToFullWord(additionalMessage.substring(0, ADDITIONAL_MESSAGE_MAX_LENGTH)));
    }

    public String getPasswordRequirements() {
        return passwordValidatorService.getGoodPasswordDescription(passwordErrors);
    }

    public List<String> getSupportedLanguages() {
        return new ArrayList<>(settingsWrapper.getConfiguredLocales().keySet());
    }

    public String getPreferredNotificationsLanguage() {
        return Option.of(preferredNotificationsLanguage)
                    .map(Locale::getLanguage)
                    .getOrNull();
    }

    public String getLocalizedDisplayNameForLanguage(String language) {
        return getLocalizedDisplayNameForLanguage(Locale.forLanguageTag(language));
    }

    public void setPreferredNotificationsLanguage(String preferredNotificationsLanguage) {
        this.preferredNotificationsLanguage = Locale.forLanguageTag(preferredNotificationsLanguage);
    }

    public boolean isDisabledForEdit(EditableAccountField field) {
        return !getUserAuthProvider().getEditableFields().contains(field);
    }

    // -------------------- PRIVATE ---------------------

    private boolean isUserLanguageConfigured() {
        return StringUtils.isNotEmpty(settingsWrapper.getConfiguredLocaleName(currentUser.getNotificationsLanguage().toLanguageTag()));
    }

    private String getLocalizedDisplayNameForLanguage(Locale language) {
        return language.getDisplayName(session.getLocale());
    }

    private String truncateToFullWord(String input) {
        String wordSeparator = " ";
        boolean inputIsOnlyOneWord = !StringUtils.contains(input, wordSeparator);
        if (inputIsOnlyOneWord) {
            return input.substring(0, 100); // probably not a valid text, shorten it further
        }
        return StringUtils.substringBeforeLast(input, wordSeparator);
    }

    private void displayNotification() {
        notificationsList = new LazyUserNotificationsDataModel(getCurrentUser(),
                userNotificationRepository, userNotificationMapper, !systemConfig.isReadonlyMode());
    }

    // -------------------- SETTERS --------------------

    public void setActiveIndex(int activeIndex) {
        this.activeIndex = activeIndex;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    public void setEditMode(EditMode editMode) {
        this.editMode = editMode;
    }

    public void setInputPassword(String inputPassword) {
        this.inputPassword = inputPassword;
    }

    public void setNotificationsList(LazyDataModel<UserNotificationDTO> notificationsList) {
        this.notificationsList = notificationsList;
    }

    public void setSelectedNotifications(List<UserNotificationDTO> selectedNotifications) {
        // Not really necessary, but needs to exist for the datatable
    }

    public void setSelectedAllNotifications(boolean selectedAllNotifications) {
        this.selectedAllNotifications = selectedAllNotifications;
    }

    public void setRedirectPage(String redirectPage) {
        this.redirectPage = redirectPage;
    }

    public void setSelectTab(String selectTab) {
        this.selectTab = selectTab;
    }

    public void setUserDisplayInfo(AuthenticatedUserDisplayInfo userDisplayInfo) {
        this.userDisplayInfo = userDisplayInfo;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}