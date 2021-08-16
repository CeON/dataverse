package edu.harvard.iq.dataverse.license;

import edu.harvard.iq.dataverse.persistence.datafile.license.FileTermsOfUse;
import edu.harvard.iq.dataverse.persistence.datafile.license.FileTermsOfUse.RestrictType;
import edu.harvard.iq.dataverse.persistence.datafile.license.FileTermsOfUse.TermsOfUseType;
import edu.harvard.iq.dataverse.persistence.datafile.license.LicenseRepository;
import edu.harvard.iq.dataverse.persistence.datafile.license.TermsOfUseForm;
import org.apache.commons.lang3.StringUtils;

import javax.ejb.Stateless;
import javax.inject.Inject;

/**
 * Mapper of {@link TermsOfUseForm} objects.
 *
 * @madryk
 */
@Stateless
public class TermsOfUseFormMapper {

    private LicenseRepository licenseRepository;


    // -------------------- CONSTRUCTORS --------------------

    public TermsOfUseFormMapper() {

    }

    @Inject
    public TermsOfUseFormMapper(LicenseRepository licenseRepository) {
        this.licenseRepository = licenseRepository;
    }

    // -------------------- LOGIC --------------------

    /**
     * Converts the given {@link TermsOfUseForm} to {@link FileTermsOfUse}
     */
    public FileTermsOfUse mapToFileTermsOfUse(TermsOfUseForm termsOfUseForm) {

        FileTermsOfUse termsOfUse = new FileTermsOfUse();

        if (StringUtils.equals(TermsOfUseType.ALL_RIGHTS_RESERVED.toString(), termsOfUseForm.getTypeWithLicenseId())) {
            termsOfUse.setAllRightsReserved(true);
        } else if (StringUtils.equals(TermsOfUseType.RESTRICTED.toString(), termsOfUseForm.getTypeWithLicenseId())) {
            termsOfUse.setRestrictType(RestrictType.valueOf(termsOfUseForm.getRestrictType()));
            termsOfUse.setRestrictCustomText(termsOfUseForm.getCustomRestrictText());
        } else if (termsOfUseForm.getTypeWithLicenseId().startsWith(TermsOfUseType.LICENSE_BASED.toString())) {
            String licenseId = termsOfUseForm.getTypeWithLicenseId().split(":")[1];
            termsOfUse.setLicense(licenseRepository.getById(Long.parseLong(licenseId)));
        }

        return termsOfUse;
    }

    /**
     * Converts the given {@link FileTermsOfUse} to {@link TermsOfUseForm}
     */
    public TermsOfUseForm mapToForm(FileTermsOfUse termsOfUse) {
        TermsOfUseForm termsOfUseForm = new TermsOfUseForm();

        String licenseIdSuffix = "";
        if (termsOfUse.getTermsOfUseType() == TermsOfUseType.LICENSE_BASED) {
            licenseIdSuffix = ":" + termsOfUse.getLicense().getId();
        }
        termsOfUseForm.setTypeWithLicenseId(termsOfUse.getTermsOfUseType().toString() + licenseIdSuffix);

        if (termsOfUse.getTermsOfUseType() == TermsOfUseType.RESTRICTED) {
            termsOfUseForm.setRestrictType(termsOfUse.getRestrictType().toString());
            termsOfUseForm.setCustomRestrictText(termsOfUse.getRestrictCustomText());
        }

        return termsOfUseForm;
    }
}
