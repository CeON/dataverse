package edu.harvard.iq.dataverse;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import javax.servlet.SessionCookieConfig;

@WebListener
public class DataverseSessionConfigListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        SessionCookieConfig sessionCookieConfig = sce.getServletContext().getSessionCookieConfig();
        String cookieName = System.getenv("COOKIE_NAME");
        if (cookieName != null) {
            sessionCookieConfig.setName(cookieName);
        }
        String cookieDomain = System.getenv("COOKIE_DOMAIN");
        if (cookieDomain != null) {
            sessionCookieConfig.setDomain(cookieDomain);
        }
        String cookieSecure = System.getenv("COOKIE_SECURE");
        if (cookieSecure != null) {
            sessionCookieConfig.setSecure(Boolean.parseBoolean(cookieSecure));
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent servletContextEvent) {
        // nothing to do here
    }
}
