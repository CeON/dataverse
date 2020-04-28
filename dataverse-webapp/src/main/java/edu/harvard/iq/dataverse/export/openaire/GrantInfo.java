package edu.harvard.iq.dataverse.export.openaire;

public class GrantInfo {

    private String grantFunder;
    private String grantFunderShort;
    private String grantProgram;
    private String grantId;

    // -------------------- GETTERS --------------------

    public String getGrantFunder() {
        return grantFunder;
    }

    public String getGrantFunderShort() {
        return grantFunderShort;
    }

    public String getGrantProgram() {
        return grantProgram;
    }

    public String getGrantId() {
        return grantId;
    }

    // -------------------- SETTERS --------------------

    public void setGrantFunder(String grantFunder) {
        this.grantFunder = grantFunder;
    }

    public void setGrantFunderShort(String grantFunderShort) {
        this.grantFunderShort = grantFunderShort;
    }

    public void setGrantProgram(String grantProgram) {
        this.grantProgram = grantProgram;
    }

    public void setGrantId(String grantId) {
        this.grantId = grantId;
    }

    // -------------------- LOGIC --------------------

    public boolean areAllFieldsPresent() {
        return grantFunder != null && grantFunderShort != null && grantId != null && grantProgram != null;
    }

    public String createGrantInfoForOpenAire() {
        StringBuilder infoBuilder = new StringBuilder();

        infoBuilder
                .append(Cleanup.normalizeSlash(grantFunderShort))
                .append("/")
                .append(Cleanup.normalizeSlash(grantProgram))
                .append("/")
                .append(Cleanup.normalizeSlash(grantId));

        return infoBuilder.toString();
    }
}
