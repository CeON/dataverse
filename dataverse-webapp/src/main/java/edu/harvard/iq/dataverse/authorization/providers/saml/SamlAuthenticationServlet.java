package edu.harvard.iq.dataverse.authorization.providers.saml;

import com.onelogin.saml2.Auth;
import com.onelogin.saml2.authn.SamlResponse;
import com.onelogin.saml2.servlet.ServletUtils;
import com.onelogin.saml2.settings.Saml2Settings;
import edu.harvard.iq.dataverse.DataverseSession;
import edu.harvard.iq.dataverse.authorization.AuthenticationServiceBean;
import edu.harvard.iq.dataverse.authorization.SamlDataUpdateService;
import edu.harvard.iq.dataverse.authorization.SamlLoginIssue;
import edu.harvard.iq.dataverse.authorization.UserRecordIdentifier;
import edu.harvard.iq.dataverse.authorization.common.ExternalIdpUserRecord;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.util.SystemConfig;
import io.vavr.control.Either;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.security.cert.CertificateEncodingException;
import java.util.List;

public class SamlAuthenticationServlet extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(SamlAuthenticationServlet.class);

    private static final String SAML_DISABLED = "SAML authentication is currently disabled.";

    public static final String NEW_USER_SESSION_PARAM = "NewUser";
    public static final String SAML_LOGIN_ISSUE_SESSION_PARAM = "SamlLoginIssues";

    private AuthenticationServiceBean authenticationService;
    private DataverseSession session;
    private SystemConfig systemConfig;
    private SamlConfigurationService samlConfigurationService;
    private SamlDataUpdateService samlDataUpdateService;

    // -------------------- CONSTURCTORS --------------------

    @Inject
    public SamlAuthenticationServlet(AuthenticationServiceBean authenticationService, DataverseSession session,
                                     SystemConfig systemConfig, SamlConfigurationService samlConfigurationService,
                                     SamlDataUpdateService samlDataUpdateService) {
        this.authenticationService = authenticationService;
        this.session = session;
        this.systemConfig = systemConfig;
        this.samlConfigurationService = samlConfigurationService;
        this.samlDataUpdateService = samlDataUpdateService;
    }

    // -------------------- LOGIC --------------------

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (!samlConfigurationService.isSamlLoginEnabled()) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, SAML_DISABLED);
            return;
        }
        String path = request.getPathInfo();
        switch (path) {
            case "/metadata":
                metadata(response);
                break;
            default:
                super.doGet(request, response);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (!samlConfigurationService.isSamlLoginEnabled()) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, SAML_DISABLED);
            return;
        }
        String path = request.getPathInfo();
        switch (path) {
            case "/acs":
                login(request, response);
                break;
            case "/sls":
                logout(request, response);
                break;
            default:
                super.doPost(request, response);
        }
    }

    // -------------------- PRIVATE --------------------

    private void metadata(HttpServletResponse response) throws IOException {
        Saml2Settings settings = samlConfigurationService.buildSpSettings();
        List<String> errors = settings.checkSPSettings();
        if (errors.isEmpty()) {
            try (ServletOutputStream out = response.getOutputStream();
                 final OutputStreamWriter writer = new OutputStreamWriter(out)) {
                writer.write(settings.getSPMetadata());
            } catch (CertificateEncodingException cee) {
                logger.warn("There was an issue while producing metadata", cee);
            }
        } else {
            logger.warn("Errors while getting metadata for Dataverse SP: " + errors);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "");
        }
    }

    private void login(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            SamlResponse samlResponse =
                    new SamlResponse(samlConfigurationService.buildSpSettings(), ServletUtils.makeHttpRequest(request));
            String entityId = samlResponse.getResponseIssuer();

            Auth auth = new Auth(samlConfigurationService.buildSettings(entityId), request, response);
            auth.processResponse();
            if (!auth.isAuthenticated()) {
                redirectOnLoginIssue(request, response, new SamlLoginIssue(SamlLoginIssue.Type.AUTHENTICATION_ERROR));
                return;
            }
            SamlUserData userData = SamlUserDataFactory.create(auth);
            ExternalIdpUserRecord userRecord = userData.toExternalIdpUserRecord();
            UserRecordIdentifier userRecordId = userRecord.toUserRecordIdentifier();
            AuthenticatedUser user = authenticationService.lookupUser(userRecordId);
            if (user == null) {
                request.getSession().setAttribute(NEW_USER_SESSION_PARAM, userRecord);
                response.sendRedirect("/firstLogin.xhtml");
                return;
            }
            Either<SamlLoginIssue, AuthenticatedUser> updateResult = systemConfig.isReadonlyMode()
                    ? Either.right(user) // Let user in and don't update if system is in read-only state
                    : samlDataUpdateService.updateUserIfNeeded(user, userData);
            if (updateResult.isLeft()) {
                redirectOnLoginIssue(request, response, updateResult.getLeft());
                return;
            }
            AuthenticatedUser updatedUser = updateResult.get();
            session.setUser(updatedUser);
            updatedUser.setSamlIdPEntityId(userData.getIdpEntityId());
            String relayState = request.getParameter("RelayState");
            response.sendRedirect(StringUtils.isNotBlank(relayState)
                    ? relayState
                    : systemConfig.getDataverseSiteUrl());
        } catch (Exception e) {
            logger.warn("Exception during SAML Authentication: ", e);
            redirectOnLoginIssue(request, response, new SamlLoginIssue(SamlLoginIssue.Type.AUTHENTICATION_ERROR));
        }
    }

    // For future use (if we want to synchronize logouts or implement IdP-initiated logout).
    private void logout(HttpServletRequest request, HttpServletResponse response) { }

    private void redirectOnLoginIssue(HttpServletRequest request, HttpServletResponse response, SamlLoginIssue issue)
            throws IOException {
        request.getSession().setAttribute(SAML_LOGIN_ISSUE_SESSION_PARAM, issue);
        response.sendRedirect("/failedLogin.xhtml");
    }
}
