package edu.harvard.iq.dataverse;

import com.google.common.collect.Lists;
import edu.harvard.iq.dataverse.common.BrandingUtil;
import edu.harvard.iq.dataverse.common.BundleUtil;
import edu.harvard.iq.dataverse.feedback.Feedback;
import edu.harvard.iq.dataverse.feedback.FeedbackInfo;
import edu.harvard.iq.dataverse.feedback.FeedbackRecipient;
import edu.harvard.iq.dataverse.feedback.FeedbackUtil;
import edu.harvard.iq.dataverse.mail.MailService;
import edu.harvard.iq.dataverse.persistence.DvObject;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import edu.harvard.iq.dataverse.util.JsfHelper;
import edu.harvard.iq.dataverse.util.MailUtil;
import edu.harvard.iq.dataverse.util.SystemConfig;
import org.apache.commons.validator.routines.EmailValidator;
import org.omnifaces.cdi.ViewScoped;

import javax.faces.application.FacesMessage;
import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.event.ActionEvent;
import javax.faces.validator.ValidatorException;
import javax.inject.Inject;
import javax.inject.Named;
import javax.mail.internet.InternetAddress;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.logging.Logger;

@ViewScoped
@Named
public class SendFeedbackDialog implements java.io.Serializable {

    private static final Logger logger = Logger.getLogger(SendFeedbackDialog.class.getCanonicalName());

    private MailService mailService;
    private SettingsServiceBean settingsService;
    private DataverseDao dataverseDao;
    private SystemConfig systemConfig;
    private DataverseSession dataverseSession;

    /** The email address supplied by the person filling out the contact form. */
    private String userEmail = "";

    /** Body of the message. */
    private String userMessage = "";

    /** Becomes the subject of the email. */
    private String messageSubject = "";

    /** First operand in addition problem. */
    private Long op1;

    /** Second operand in addition problem. */
    private Long op2;

    /** The guess the user makes in addition problem. */
    private Long userSum;

    /**
     * Either the dataverse or the dataset that the message is pertaining to.
     * If there is no target, the feedback message is about the repo as a whole.
     */
    private DvObject feedbackTarget;

    /** Whether a copy of the message should be sent to user's mail */
    private boolean sendCopy;

    /** :SystemEmail (the main support address for an installation). */
    private InternetAddress systemAddress;

    private FeedbackRecipient recipientOption;

    // -------------------- CONSTRUCTORS --------------------

    public SendFeedbackDialog() { }

    @Inject
    public SendFeedbackDialog(MailService mailService, SettingsServiceBean settingsService,
                              DataverseDao dataverseDao, SystemConfig systemConfig,
                              DataverseSession dataverseSession) {
        this.mailService = mailService;
        this.settingsService = settingsService;
        this.dataverseDao = dataverseDao;
        this.systemConfig = systemConfig;
        this.dataverseSession = dataverseSession;
    }

    // -------------------- GETTERS --------------------

    public String getUserEmail() {
        return userEmail;
    }

    public Long getOp1() {
        return op1;
    }

    public Long getOp2() {
        return op2;
    }

    public Long getUserSum() {
        return userSum;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public String getMessageSubject() {
        return messageSubject;
    }

    public boolean getSendCopy() {
        return sendCopy;
    }

    public DvObject getFeedbackTarget() {
        return feedbackTarget;
    }

    public FeedbackRecipient getRecipientOption() {
        return recipientOption;
    }

    // -------------------- LOGIC --------------------

    public void initUserInput() {
        userEmail = "";
        userMessage = "";
        messageSubject = "";
        Random random = new Random();
        op1 = (long) random.nextInt(10);
        op2 = (long) random.nextInt(10);
        userSum = null;
        String systemEmail = settingsService.getValueForKey(SettingsServiceBean.Key.SystemEmail);
        systemAddress = MailUtil.parseSystemAddress(systemEmail);
        sendCopy = false;
    }

    public void initUserInput(ActionEvent ae) {
        initUserInput();
    }


    public List<FeedbackRecipient> getRecipientOptions() {
        if (feedbackTarget == null) {
            return Lists.newArrayList(FeedbackRecipient.SYSTEM_SUPPORT);
        } else if (feedbackTarget.isInstanceofDataverse()) {
            return Lists.newArrayList(FeedbackRecipient.SYSTEM_SUPPORT, FeedbackRecipient.DATAVERSE_CONTACT);
        } else {
            return Lists.newArrayList(FeedbackRecipient.SYSTEM_SUPPORT, FeedbackRecipient.DATAVERSE_CONTACT,
                    FeedbackRecipient.DATASET_CONTACT);
        }
    }

    public String getRecipientOptionLabel(FeedbackRecipient option) {
        if (option == FeedbackRecipient.SYSTEM_SUPPORT) {
            return BundleUtil.getStringFromBundle("contact.to.option.repo", dataverseDao.findRootDataverse().getName());
        } else if (option == FeedbackRecipient.DATAVERSE_CONTACT) {
            return BundleUtil.getStringFromBundle("contact.to.option.dataverse");
        } else {
            return BundleUtil.getStringFromBundle("contact.to.option.dataset");
        }
    }

    public String getFormHeader() {
        if (feedbackTarget == null) {
            return BrandingUtil.getContactHeader();
        } else if (feedbackTarget.isInstanceofDataverse()) {
            return BundleUtil.getStringFromBundle("contact.dataverse.header");
        } else {
            return BundleUtil.getStringFromBundle("contact.dataset.header");
        }
    }

    public void validateUserSum(FacesContext context, UIComponent component, Object value) throws ValidatorException {
        if (op1 + op2 != (Long) value) {
            FacesMessage msg = new FacesMessage(BundleUtil.getStringFromBundle("contact.sum.invalid"));
            msg.setSeverity(FacesMessage.SEVERITY_ERROR);
            throw new ValidatorException(msg);
        }
    }

    public void validateUserEmail(FacesContext context, UIComponent component, Object value) throws ValidatorException {
        if (!EmailValidator.getInstance().isValid((String) value)) {
            FacesMessage msg = new FacesMessage(BundleUtil.getStringFromBundle("external.newAccount.emailInvalid"));
            msg.setSeverity(FacesMessage.SEVERITY_ERROR);
            throw new ValidatorException(msg);
        }
    }

    public String sendMessage() {
        // FIXME: move dataverseDao.findRootDataverse() to init
        String rootDataverseName = dataverseDao.findRootDataverse().getName();
        String installationBrandName = BrandingUtil.getInstallationBrandName(rootDataverseName);
        String supportTeamName = BrandingUtil.getSupportTeamName(systemAddress, rootDataverseName);
        List<Feedback> feedbacks = FeedbackUtil.gatherFeedback(new FeedbackInfo<>()
                .withFeedbackTarget(feedbackTarget)
                .withRecipient(recipientOption)
                .withUserEmail(dataverseSession, userEmail)
                .withSystemEmail(systemAddress)
                .withMessageSubject(messageSubject)
                .withUserMessage(userMessage)
                .withDataverseSiteUrl(systemConfig.getDataverseSiteUrl())
                .withInstallationBrandName(installationBrandName)
                .withSupportTeamName(supportTeamName));
        if (feedbacks.isEmpty()) {
            logger.warning("No feedback has been sent!");
            JsfHelper.addErrorMessage(BundleUtil.getStringFromBundle("contact.send.failure"));
            return null;
        }
        for (Feedback feedback : feedbacks) {
            logger.fine("sending feedback: " + feedback);
            mailService.sendMailAsync(feedback.getFromEmail(), feedback.getToEmail(), feedback.getSubject(), feedback.getBody());
        }
        if (sendCopy) {
            sendCopy(rootDataverseName, feedbacks.get(0));
        }
        JsfHelper.addSuccessMessage(BundleUtil.getStringFromBundle("contact.send.success"));

        return null;
    }

    public boolean isLoggedIn() {
        return dataverseSession.getUser().isAuthenticated();
    }

    public String loggedInUserEmail() {
        return dataverseSession.getUser().getDisplayInfo().getEmailAddress();
    }

    public void setFeedbackTarget(DvObject feedbackTarget) {
        this.feedbackTarget = feedbackTarget;

        if (feedbackTarget == null) {
            recipientOption = FeedbackRecipient.SYSTEM_SUPPORT;
        } else if (feedbackTarget.isInstanceofDataverse()) {
            recipientOption = FeedbackRecipient.DATAVERSE_CONTACT;
        } else {
            recipientOption = FeedbackRecipient.DATASET_CONTACT;
        }
    }

    // -------------------- PRIVATE --------------------

    private void sendCopy(String rootDataverseName, Feedback feedback) {
        String mail = isLoggedIn() ? loggedInUserEmail() : userEmail;
        Locale locale = isLoggedIn() ? loggedInUserLanguage() : BundleUtil.getCurrentLocale();
        
        String header;
        String siteUrl = systemConfig.getDataverseSiteUrl();
        if (feedbackTarget != null && feedbackTarget.isInstanceofDataverse()) {
            Dataverse dataverse = (Dataverse) feedbackTarget;
            header = BundleUtil.getStringFromBundleWithLocale("contact.copy.message.header.dataverse", locale,
                    rootDataverseName, dataverse.getName(),
                    siteUrl + "/dataverse/" + dataverse.getAlias());
        } else if (feedbackTarget != null && feedbackTarget.isInstanceofDataset()) {
            Dataset dataset = (Dataset) feedbackTarget;
            header = BundleUtil.getStringFromBundleWithLocale("contact.copy.message.header.dataset", locale,
                    rootDataverseName, dataset.getDisplayName(),
                    siteUrl + "/dataset.xhtml?persistentId=" + dataset.getGlobalId().asString());
        } else {
            header = BundleUtil.getStringFromBundleWithLocale("contact.copy.message.header.general", locale, rootDataverseName);
        }
        String content = header + BundleUtil.getStringFromBundleWithLocale("contact.copy.message.template", locale, userMessage)
                + mailService.getFooterMailMessage(null, locale);
        mailService.sendMailAsync(null, mail,
                BundleUtil.getStringFromBundleWithLocale("contact.copy.message.subject", locale, feedback.getSubject()), content);
    }

    private Locale loggedInUserLanguage() {
        return ((AuthenticatedUser)dataverseSession.getUser()).getNotificationsLanguage();
    }

    // -------------------- SETTERS --------------------

    public void setUserEmail(String uEmail) {
        userEmail = uEmail;
    }

    public void setOp1(Long op1) {
        this.op1 = op1;
    }

    public void setOp2(Long op2) {
        this.op2 = op2;
    }

    public void setUserSum(Long userSum) {
        this.userSum = userSum;
    }

    public void setUserMessage(String mess) {
        userMessage = mess;
    }

    public void setMessageSubject(String messageSubject) {
        this.messageSubject = messageSubject;
    }

    public void setSendCopy(boolean sendCopy) {
        this.sendCopy = sendCopy;
    }

    public void setRecipientOption(FeedbackRecipient recipientOption) {
        this.recipientOption = recipientOption;
    }

}