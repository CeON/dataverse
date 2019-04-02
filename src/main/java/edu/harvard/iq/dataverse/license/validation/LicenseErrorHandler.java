package edu.harvard.iq.dataverse.license.validation;

import com.google.common.collect.Lists;
import edu.harvard.iq.dataverse.license.dto.LicenseDto;
import edu.harvard.iq.dataverse.license.dto.LocaleTextDto;
import edu.harvard.iq.dataverse.util.BundleUtil;
import edu.harvard.iq.dataverse.util.JsfHelper;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.UrlValidator;

import javax.ejb.Stateless;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import java.io.Serializable;
import java.util.List;

/**
 * Class responsible for validating if all information's regarding new license are correct.
 */
@Stateless
public class LicenseErrorHandler implements Serializable {

    private UrlValidator urlValidator = new UrlValidator(Lists.newArrayList("http", "https").toArray(new String[0]));

    // -------------------- LOGIC --------------------

    /**
     * Checks if new license is valid.
     *
     * @param licenseDto
     * @return list of all errors
     */
    public List<FacesMessage> validateNewLicenseErrors(LicenseDto licenseDto) {
        handleLicenseNameError(licenseDto);

        handleLicenseLocalizedNamesErrors(licenseDto);

        handleUrlErrors(licenseDto);

        return FacesContext.getCurrentInstance().getMessageList();
    }

    // -------------------- PRIVATE --------------------

    private void handleLicenseNameError(LicenseDto licenseDto) {
        if (licenseDto.getName() == null) {
            JsfHelper.addErrorMessage("form:universalName",
                    StringUtils.EMPTY,
                    BundleUtil.getStringFromBundle("dashboard.license.newLicense.missingTextField"));
        }
    }

    private void handleLicenseLocalizedNamesErrors(LicenseDto licenseDto) {
        List<LocaleTextDto> localizedNames = licenseDto.getLocalizedNames();

        localizedNames.stream()
                .filter(localeTextDto -> localeTextDto.getText() == null)
                .forEach(localeTextDto -> JsfHelper.addErrorMessage(
                        "form:repeater:" + localizedNames.indexOf(localeTextDto) + ":locale",
                        StringUtils.EMPTY,
                        BundleUtil.getStringFromBundle("dashboard.license.newLicense.missingTextField")));
    }

    private void handleUrlErrors(LicenseDto licenseDto) {
        if (licenseDto.getUrl() == null) {
            JsfHelper.addErrorMessage("form:url",
                    StringUtils.EMPTY,
                    BundleUtil.getStringFromBundle("dashboard.license.newLicense.missingTextField"));
        } else if (!urlValidator.isValid(licenseDto.getUrl())) {
            JsfHelper.addErrorMessage("form:url",
                    StringUtils.EMPTY,
                    BundleUtil.getStringFromBundle("dashboard.license.newLicense.invalidURL"));
        }
    }
}
