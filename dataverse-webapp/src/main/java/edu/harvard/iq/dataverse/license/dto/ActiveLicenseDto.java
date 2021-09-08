package edu.harvard.iq.dataverse.license.dto;

/**
 * Helper class used for listing active licenses in @link /api/info/activeLicenses endpoint.
 */
public class ActiveLicenseDto {
    private String license;

    // -------------------- CONSTRUCTORS --------------------

    public ActiveLicenseDto(String license) {
        this.license = license;
    }

    // -------------------- GETTERS --------------------

    public String getLicense() {
        return license;
    }

    // -------------------- toString --------------------
    @Override
    public String toString() {
        return "'license' : '" + license + "'";
    }
}
