package edu.harvard.iq.dataverse;

import edu.harvard.iq.dataverse.authorization.AuthenticationProvider;
import edu.harvard.iq.dataverse.authorization.AuthenticationProviderDisplayInfo;
import edu.harvard.iq.dataverse.authorization.AuthenticationRequest;
import edu.harvard.iq.dataverse.authorization.AuthenticationResponse;
import edu.harvard.iq.dataverse.authorization.AuthenticationServiceBean;
import edu.harvard.iq.dataverse.authorization.CredentialsAuthenticationProvider;
import edu.harvard.iq.dataverse.authorization.exceptions.AuthenticationFailedException;
import edu.harvard.iq.dataverse.authorization.providers.shib.ShibAuthenticationProvider;
import edu.harvard.iq.dataverse.common.BundleUtil;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import edu.harvard.iq.dataverse.util.JsfHelper;
import org.omnifaces.cdi.ViewScoped;

import javax.ejb.EJB;
import javax.faces.application.FacesMessage;
import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.event.AjaxBehaviorEvent;
import javax.faces.validator.ValidatorException;
import javax.inject.Inject;
import javax.inject.Named;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author xyang
 * @author Michael Bar-Sinai
 */
@ViewScoped
@Named("LoginPage")
public class LoginPage implements java.io.Serializable {
    private static final Logger logger = Logger.getLogger(LoginPage.class.getName());

    public static class FilledCredential {
        CredentialsAuthenticationProvider.Credential credential;
        String value;

        public FilledCredential() {
        }

        public FilledCredential(CredentialsAuthenticationProvider.Credential credential, String value) {
            this.credential = credential;
            this.value = value;
        }

        public CredentialsAuthenticationProvider.Credential getCredential() {
            return credential;
        }

        public void setCredential(CredentialsAuthenticationProvider.Credential credential) {
            this.credential = credential;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

    }

    public enum EditMode {LOGIN, SUCCESS, FAILED}

    @Inject
    DataverseSession session;

    @EJB
    DataverseDao dataverseDao;

    @EJB
    AuthenticationServiceBean authSvc;

    @EJB
    SettingsServiceBean settingsService;

    @Inject
    DataverseRequestServiceBean dvRequestService;

    private String credentialsAuthProviderId;

    private List<FilledCredential> filledCredentials;

    private String redirectPage = "dataverse.xhtml";
    private AuthenticationProvider authProvider;
    private int numFailedLoginAttempts;
    Random random;
    long op1;
    long op2;
    Long userSum;

    public String init() {
        if(dvRequestService.getDataverseRequest().getUser().isAuthenticated()) {
            if(redirectPage.contains("loginpage.xhtml")) {
                return redirectToRoot();
            } else
            {
                return redirectPage;
            }
        }

        Iterator<String> credentialsIterator = authSvc.getAuthenticationProviderIdsOfType(CredentialsAuthenticationProvider.class).iterator();
        if (credentialsIterator.hasNext()) {
            setCredentialsAuthProviderId(credentialsIterator.next());
        }
        resetFilledCredentials(null);
        authProvider = authSvc.getAuthenticationProvider(settingsService.getValueForKey(SettingsServiceBean.Key.DefaultAuthProvider));
        random = new Random();

        return "";
    }

    public List<AuthenticationProviderDisplayInfo> listCredentialsAuthenticationProviders() {
        List<AuthenticationProviderDisplayInfo> infos = new LinkedList<>();
        for (String id : authSvc.getAuthenticationProviderIdsOfType(CredentialsAuthenticationProvider.class)) {
            AuthenticationProvider authenticationProvider = authSvc.getAuthenticationProvider(id);
            infos.add(authenticationProvider.getInfo());
        }
        return infos;
    }

    public List<AuthenticationProviderDisplayInfo> listAuthenticationProviders() {
        List<AuthenticationProviderDisplayInfo> infos = new LinkedList<>();
        for (String id : authSvc.getAuthenticationProviderIdsSorted()) {
            AuthenticationProvider authenticationProvider = authSvc.getAuthenticationProvider(id);
            if (authenticationProvider != null) {
                if (ShibAuthenticationProvider.PROVIDER_ID.equals(authenticationProvider.getId())) {
                    infos.add(authenticationProvider.getInfo());
                } else {
                    infos.add(authenticationProvider.getInfo());
                }
            }
        }
        return infos;
    }

    public CredentialsAuthenticationProvider selectedCredentialsProvider() {
        return (CredentialsAuthenticationProvider) authSvc.getAuthenticationProvider(getCredentialsAuthProviderId());
    }

    public String login() {

        AuthenticationRequest authReq = new AuthenticationRequest();
        List<FilledCredential> filledCredentialsList = getFilledCredentials();
        if (filledCredentialsList == null) {
            logger.info("Credential list is null!");
            return null;
        }
        for (FilledCredential fc : filledCredentialsList) {
            authReq.putCredential(fc.getCredential().getKey(), fc.getValue());
        }
        authReq.setIpAddress(dvRequestService.getDataverseRequest().getSourceAddress());
        try {
            AuthenticatedUser r = authSvc.getUpdateAuthenticatedUser(credentialsAuthProviderId, authReq);
            logger.log(Level.FINE, "User authenticated: {0}", r.getEmail());
            session.setUser(r);

            if ("dataverse.xhtml".equals(redirectPage)) {
                redirectPage = redirectToRoot();
            }

            try {
                redirectPage = URLDecoder.decode(redirectPage, "UTF-8");
            } catch (UnsupportedEncodingException ex) {
                logger.log(Level.SEVERE, null, ex);
                redirectPage = redirectToRoot();
            }

            logger.log(Level.FINE, "Sending user to = {0}", redirectPage);
            return redirectPage + (!redirectPage.contains("?") ? "?" : "&") + "faces-redirect=true";


        } catch (AuthenticationFailedException ex) {
            numFailedLoginAttempts++;
            op1 = new Long(random.nextInt(10));
            op2 = new Long(random.nextInt(10));
            AuthenticationResponse response = ex.getResponse();
            switch (response.getStatus()) {
                case FAIL:
                    JsfHelper.addFlashErrorMessage(BundleUtil.getStringFromBundle("login.builtin.invalidUsernameEmailOrPassword"));
                    return null;
                case ERROR:
                    /**
                     * @todo How do we exercise this part of the code? Something
                     * with password upgrade? See
                     * https://github.com/IQSS/dataverse/pull/2922
                     */
                    JsfHelper.addFlashErrorMessage(BundleUtil.getStringFromBundle("login.error"));
                    logger.log(Level.WARNING, "Error logging in: " + response.getMessage(), response.getError());
                    return null;
                case BREAKOUT:
                    return response.getMessage();
                default:
                    JsfHelper.addFlashErrorMessage("INTERNAL ERROR");
                    return null;
            }
        }

    }

    private String redirectToRoot() {
        return "dataverse.xhtml?alias=" + dataverseDao.findRootDataverse().getAlias();
    }

    public String getCredentialsAuthProviderId() {
        return credentialsAuthProviderId;
    }

    public void resetFilledCredentials(AjaxBehaviorEvent event) {
        if (selectedCredentialsProvider() == null) {
            return;
        }

        filledCredentials = new LinkedList<>();
        for (CredentialsAuthenticationProvider.Credential c : selectedCredentialsProvider().getRequiredCredentials()) {
            filledCredentials.add(new FilledCredential(c, ""));
        }
    }

    public void setCredentialsAuthProviderId(String authProviderId) {
        this.credentialsAuthProviderId = authProviderId;
    }

    public List<FilledCredential> getFilledCredentials() {
        return filledCredentials;
    }

    public void setFilledCredentials(List<FilledCredential> filledCredentials) {
        this.filledCredentials = filledCredentials;
    }

    public boolean isMultipleProvidersAvailable() {
        return authSvc.getAuthenticationProviderIds().size() > 1;
    }

    public String getRedirectPage() {
        return redirectPage;
    }

    public void setRedirectPage(String redirectPage) {
        this.redirectPage = redirectPage;
    }

    public AuthenticationProvider getAuthProvider() {
        return authProvider;
    }

    public void setAuthProviderById(String authProviderId) {
        logger.fine("Setting auth provider to " + authProviderId);
        this.authProvider = authSvc.getAuthenticationProvider(authProviderId);
    }

    public String getLoginButtonText() {
        if (authProvider != null) {
            // Note that for ORCID we do not want the normal "Log In with..." text. There is special logic in the xhtml.
            return BundleUtil.getStringFromBundle("login.button", Arrays.asList(authProvider.getInfo().getTitle()));
        } else {
            return BundleUtil.getStringFromBundle("login.button", Arrays.asList("???"));
        }
    }

    public int getNumFailedLoginAttempts() {
        return numFailedLoginAttempts;
    }

    public boolean isRequireExtraValidation() {
        return numFailedLoginAttempts > 2;
    }

    public long getOp1() {
        return op1;
    }

    public long getOp2() {
        return op2;
    }

    public Long getUserSum() {
        return userSum;
    }

    public void setUserSum(Long userSum) {
        this.userSum = userSum;
    }

    // TODO: Consolidate with SendFeedbackDialog.validateUserSum?
    public void validateUserSum(FacesContext context, UIComponent component, Object value) throws ValidatorException {
        // The FacesMessage text is on the xhtml side.
        FacesMessage msg = new FacesMessage("");
        ValidatorException validatorException = new ValidatorException(msg);
        if (value == null) {
            throw validatorException;
        }
        if (op1 + op2 != (Long) value) {
            throw validatorException;
        }
    }

}
