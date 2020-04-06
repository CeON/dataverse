package edu.harvard.iq.dataverse.api.dto;

import edu.harvard.iq.dataverse.persistence.datafile.license.FileTermsOfUse;
import org.apache.commons.lang3.StringUtils;

public class FileTermsOfUseDTO {

    private String termsOfUseType;
    private String license;
    private String restrictType;
    private String restrictCustomText;

    // -------------------- CONSTRUCTORS --------------------
    public FileTermsOfUseDTO(String termsOfUseType, String license, String restrictType, String restrictCustomText) {
        this.termsOfUseType = termsOfUseType;
        this.license = license;
        this.restrictType = restrictType;
        this.restrictCustomText = restrictCustomText;
    }

    // -------------------- GETTERS --------------------
    public String getTermsOfUseType() {
        return termsOfUseType;
    }

    public String getLicense() {
        return license;
    }

    public String getRestrictType() {
        return restrictType;
    }

    public String getRestrictCustomText() {
        return restrictCustomText;
    }

    // -------------------- LOGIC --------------------
    public FileTermsOfUseDTO createLicenseBasedTermsDTO(String license) {
        return new FileTermsOfUseDTO(FileTermsOfUse.TermsOfUseType.LICENSE_BASED.toString(), license, StringUtils.EMPTY, StringUtils.EMPTY);
    }

    public FileTermsOfUseDTO createAllRightsReservedTermsDTO() {
        return new FileTermsOfUseDTO(FileTermsOfUse.TermsOfUseType.ALL_RIGHTS_RESERVED.toString(), StringUtils.EMPTY, StringUtils.EMPTY, StringUtils.EMPTY);
    }
}
