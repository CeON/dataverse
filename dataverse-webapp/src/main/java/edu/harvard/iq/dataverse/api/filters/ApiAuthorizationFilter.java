package edu.harvard.iq.dataverse.api.filters;

import edu.harvard.iq.dataverse.DataverseSession;
import edu.harvard.iq.dataverse.UserServiceBean;
import edu.harvard.iq.dataverse.authorization.AuthenticationServiceBean;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.persistence.user.GuestUser;

import javax.inject.Inject;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

public class ApiAuthorizationFilter implements Filter {

    private DataverseSession session;
    private AuthenticationServiceBean authenticationService;
    private UserServiceBean userService;

    // -------------------- CONSTRUCTORS ---------------------

    @Inject
    public ApiAuthorizationFilter(DataverseSession session, AuthenticationServiceBean authenticationService, UserServiceBean userService) {
        this.session = session;
        this.authenticationService = authenticationService;
        this.userService = userService;
    }

    // -------------------- LOGIC ---------------------

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        boolean loggedByToken = logUserByTokenIfNeeded(servletRequest);
        chain.doFilter(servletRequest, response);
        logoutIfLoggedByToken(servletRequest, loggedByToken);
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException { }

    @Override
    public void destroy() { }

    // -------------------- PRIVATE ---------------------

    private boolean logUserByTokenIfNeeded(ServletRequest servletRequest) {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        if (GuestUser.get().equals(session.getUser())) {
            String token = getRequestApiKey(request);
            AuthenticatedUser user = authenticationService.lookupUser(token);
            if (user != null) {
                user = userService.updateLastApiUseTime(user);
                session.setUser(user);
                return true;
            }
        }
        return false;
    }

    private void logoutIfLoggedByToken(ServletRequest servletRequest, boolean loggedByToken) {
        if (loggedByToken) {
            HttpServletRequest request = (HttpServletRequest) servletRequest;
            request.getSession().invalidate();
        }
    }

    private String getRequestApiKey(HttpServletRequest request) {
        String headerParamApiKey = request.getHeader("X-Dataverse-key");
        String queryParamApiKey = request.getParameter("key");
        return headerParamApiKey != null ? headerParamApiKey : queryParamApiKey;
    }
}
