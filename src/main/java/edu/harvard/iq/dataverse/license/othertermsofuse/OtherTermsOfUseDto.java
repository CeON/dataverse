package edu.harvard.iq.dataverse.license.othertermsofuse;

public class OtherTermsOfUseDto {

    private String key;

    private String universalDisplayName;

    private boolean active;

    // -------------------- CONSTRUCTORS --------------------

    public OtherTermsOfUseDto(String key, String universalDisplayName, boolean active) {
        this.key = key;
        this.universalDisplayName = universalDisplayName;
        this.active = active;
    }

    // -------------------- GETTERS --------------------

    /**
     * Key that represents value in the properties file or db.
     */
    public String getKey() {
        return key;
    }

    /**
     * Display name property, so we can have the same name regardless of selected language.
     */
    public String getUniversalDisplayName() {
        return universalDisplayName;
    }

    public boolean isActive() {
        return active;
    }

    // -------------------- SETTERS --------------------

    public void setKey(String key) {
        this.key = key;
    }

    public void setUniversalDisplayName(String universalDisplayName) {
        this.universalDisplayName = universalDisplayName;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
