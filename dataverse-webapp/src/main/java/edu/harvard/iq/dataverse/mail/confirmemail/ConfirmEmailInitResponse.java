package edu.harvard.iq.dataverse.mail.confirmemail;

import edu.harvard.iq.dataverse.persistence.user.ConfirmEmailData;

/**
 * @author bsilverstein
 */
public class ConfirmEmailInitResponse {

    private boolean userFound;
    private String confirmUrl;
    private ConfirmEmailData confirmEmailData;

    public ConfirmEmailInitResponse(boolean userFound) {
        this.userFound = userFound;
    }

    public ConfirmEmailInitResponse(boolean userFound, ConfirmEmailData confirmEmailData, String confirmUrl) {
        this.userFound = userFound;
        this.confirmEmailData = confirmEmailData;
        this.confirmUrl = confirmUrl;
    }

    public boolean isUserFound() {
        return userFound;
    }

    public String getConfirmUrl() {
        return confirmUrl;
    }

    public ConfirmEmailData getConfirmEmailData() {
        return confirmEmailData;
    }
}
