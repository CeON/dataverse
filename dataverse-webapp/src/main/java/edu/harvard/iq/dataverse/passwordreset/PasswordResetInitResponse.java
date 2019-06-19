package edu.harvard.iq.dataverse.passwordreset;

import edu.harvard.iq.dataverse.util.SystemConfig;

import javax.ejb.Stateful;
import javax.inject.Inject;
import java.net.InetAddress;
import java.net.UnknownHostException;

@Stateful
public class PasswordResetInitResponse {

    /**
     * @todo Do we really need emailFound? Just check if passwordResetData is
     * null or not instead?
     */
    private boolean emailFound;
    private String resetUrl;
    private PasswordResetData passwordResetData;

    @Inject
    private SystemConfig systemConfig;

    public PasswordResetInitResponse() {
    }

    public PasswordResetInitResponse(boolean emailFound) {
        this.emailFound = emailFound;
    }

    public PasswordResetInitResponse(boolean emailFound, PasswordResetData passwordResetData) {
        this.emailFound = emailFound;
        this.passwordResetData = passwordResetData;
        this.resetUrl = createResetUrl();
    }

    private String createResetUrl() {
        // default to localhost
        String finalHostname = "localhost";
        String configuredHostname = systemConfig.getFqdnProperty();
        if (configuredHostname != null) {
            if (configuredHostname.equals("localhost")) {
                // must be a dev environment
                finalHostname = "localhost:8181";
            } else {
                finalHostname = configuredHostname;
            }
        } else {
            try {
                finalHostname = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException ex) {
                // just use the dev address
            }
        }
        return "https://" + finalHostname + "/passwordreset.xhtml?token=" + passwordResetData.getToken();
    }

    public boolean isEmailFound() {
        return emailFound;
    }

    public String getResetUrl() {
        return resetUrl;
    }

    public PasswordResetData getPasswordResetData() {
        return passwordResetData;
    }

    public void setResetUrl(String resetUrl) {
        this.resetUrl = resetUrl;
    }
}
