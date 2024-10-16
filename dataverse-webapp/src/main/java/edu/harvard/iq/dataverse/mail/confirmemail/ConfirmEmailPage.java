package edu.harvard.iq.dataverse.mail.confirmemail;

import edu.harvard.iq.dataverse.DataverseSession;
import edu.harvard.iq.dataverse.actionlogging.ActionLogServiceBean;
import edu.harvard.iq.dataverse.common.BundleUtil;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.persistence.user.ConfirmEmailData;
import edu.harvard.iq.dataverse.util.JsfHelper;
import org.omnifaces.cdi.ViewScoped;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.logging.Logger;

/**
 * @author bsilverstein
 */
@ViewScoped
@Named("ConfirmEmailPage")
public class ConfirmEmailPage implements java.io.Serializable {

    private static final Logger logger = Logger.getLogger(ConfirmEmailPage.class.getCanonicalName());

    @EJB
    ConfirmEmailServiceBean confirmEmailService;
    @Inject
    DataverseSession session;

    @EJB
    ActionLogServiceBean actionLogSvc;

    /**
     * The unique string used to look up a user and continue the email
     * confirmation.
     */
    String token;

    /**
     * The user looked up by the token who will be confirming their email.
     */
    AuthenticatedUser user;

    /**
     * The link that is emailed to the user to confirm the email that contains a
     * token.
     */
    String confirmEmailUrl;

    ConfirmEmailData confirmEmailData;

    public String init() {
        if (token != null) {
            ConfirmEmailExecResponse confirmEmailExecResponse = confirmEmailService.processToken(token);
            confirmEmailData = confirmEmailExecResponse.getConfirmEmailData();
            if (confirmEmailData != null) {
                user = confirmEmailData.getAuthenticatedUser();
                session.setUser(user);
                JsfHelper.addFlashSuccessMessage(BundleUtil.getStringFromBundle("confirmEmail.details.success"));
                return "/dataverse.xhtml?faces-redirect=true";
            }
        }
        JsfHelper.addErrorMessage(BundleUtil.getStringFromBundle("confirmEmail.details.failure"));
        /**
         * @todo It would be nice to send a 404 response but if we enable this
         * then the user sees the contents of 404.xhtml rather than the contents
         * of JsfHelper.addFlashErrorMessage above!
         */
//        try {
//            FacesContext.getCurrentInstance().getExternalContext().responseSendError(HttpServletResponse.SC_NOT_FOUND, null);
//        } catch (IOException ex) {
//        }
        return null;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public AuthenticatedUser getUser() {
        return user;
    }

    public String getConfirmEmailUrl() {
        return confirmEmailUrl;
    }

    public ConfirmEmailData getConfirmEmailData() {
        return confirmEmailData;
    }

    public void setConfirmEmailData(ConfirmEmailData confirmEmailData) {
        this.confirmEmailData = confirmEmailData;
    }

    public boolean isInvalidToken() {
        return confirmEmailData == null;
    }

    public String getRedirectToAccountInfoTab() {
        return "/dataverseuser.xhtml?selectTab=accountInfo&faces-redirect=true";
    }

}
