package edu.harvard.iq.dataverse.license;

import edu.harvard.iq.dataverse.persistence.datafile.license.FileTermsOfUse;
import edu.harvard.iq.dataverse.persistence.datafile.license.License;
import edu.harvard.iq.dataverse.persistence.datafile.license.LicenseDAO;
import edu.harvard.iq.dataverse.persistence.datafile.license.FileTermsOfUse.RestrictType;

import javax.ejb.Stateless;
import javax.inject.Inject;


/**
 * Factory of {@link FileTermsOfUse} objects.
 *
 * @author madryk
 */
@Stateless
public class TermsOfUseFactory {

    private LicenseDAO licenseDao;


    // -------------------- CONSTRUCTORS --------------------

    public TermsOfUseFactory() {

    }

    @Inject
    public TermsOfUseFactory(LicenseDAO licenseDao) {
        this.licenseDao = licenseDao;
    }

    // -------------------- LOGIC --------------------

    /**
     * Returns new instance of license based {@link FileTermsOfUse}
     * with license set to first active one.
     */
    public FileTermsOfUse createTermsOfUse() {

        License defaultLicense = licenseDao.findFirstActive();

        return createTermsOfUseFromLicense(defaultLicense);
    }

    /**
     * Returns new instance of license based {@link FileTermsOfUse}
     * with the given license.
     */
    public FileTermsOfUse createTermsOfUseFromLicense(License license) {
        FileTermsOfUse termsOfUse = new FileTermsOfUse();
        termsOfUse.setLicense(license);

        return termsOfUse;
    }

    /**
     * Return new instance of all rights reserved
     * {@link FileTermsOfUse}.
     */
    public FileTermsOfUse createAllRightsReservedTermsOfUse() {
        FileTermsOfUse termsOfUse = new FileTermsOfUse();
        termsOfUse.setAllRightsReserved(true);

        return termsOfUse;
    }

    /**
     * Return new instance of restricted access
     * {@link FileTermsOfUse} with the given restrict type.
     */
    public FileTermsOfUse createRestrictedTermsOfUse(RestrictType restrictType) {
        FileTermsOfUse termsOfUse = new FileTermsOfUse();
        termsOfUse.setRestrictType(restrictType);

        return termsOfUse;
    }

    /**
     * Return new instance of restricted access
     * {@link FileTermsOfUse} with {@link FileTermsOfUse.RestrictType.CUSTOM} type
     * and custom restrict reason text
     */
    public FileTermsOfUse createRestrictedCustomTermsOfUse(String customRestrictReason) {
        FileTermsOfUse termsOfUse = new FileTermsOfUse();
        termsOfUse.setRestrictType(RestrictType.CUSTOM);
        termsOfUse.setRestrictCustomText(customRestrictReason);

        return termsOfUse;
    }
}
