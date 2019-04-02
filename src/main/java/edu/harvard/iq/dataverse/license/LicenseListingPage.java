package edu.harvard.iq.dataverse.license;

import edu.harvard.iq.dataverse.DataverseSession;
import edu.harvard.iq.dataverse.PermissionsWrapper;
import edu.harvard.iq.dataverse.SettingsWrapper;
import edu.harvard.iq.dataverse.license.dto.LicenseDto;
import edu.harvard.iq.dataverse.license.dto.LicenseIconDto;
import edu.harvard.iq.dataverse.license.dto.LicenseMapper;
import edu.harvard.iq.dataverse.license.dto.LocaleTextDto;
import edu.harvard.iq.dataverse.license.validation.LicenseErrorHandler;
import edu.harvard.iq.dataverse.util.BundleUtil;
import org.apache.commons.lang.StringUtils;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.ByteArrayContent;
import org.primefaces.model.UploadedFile;

import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


/**
 * Page that is responsible for showing all valid and invalid licenses while also giving ability to disable/enable
 * them all across Dataverse.
 */
@ViewScoped
@Named("LicenseListingPage")
public class LicenseListingPage implements Serializable {

    @Inject
    private DataverseSession session;

    @Inject
    private SettingsWrapper settingsWrapper;

    @Inject
    private PermissionsWrapper permissionsWrapper;

    @Inject
    private LicenseDAO licenseDAO;

    @Inject
    private LicenseMapper licenseMapper;

    @Inject
    private LicenseErrorHandler licenseErrorHandler;

    private List<LicenseDto> licenses = new ArrayList<>();

    private LicenseDto freshLicense;

    // -------------------- GETTERS --------------------

    public List<LicenseDto> getLicenses() {
        return licenses;
    }

    public LicenseDto getFreshLicense() {
        return freshLicense;
    }

    // -------------------- LOGIC --------------------

    public String init() {

        if (!session.getUser().isSuperuser()) {
            return permissionsWrapper.notAuthorized();
        }

        licenses = licenseMapper.mapToDtos(licenseDAO.findAll());

        freshLicense = prepareFreshLicense();

        return StringUtils.EMPTY;
    }

    /**
     * Handles image upload for new license.
     *
     * @param event
     */
    public void uploadImageForNewLicenseEvent(FileUploadEvent event) {
        UploadedFile uploadedImage = event.getFile();
        freshLicense.getIcon().setContent(new ByteArrayContent(uploadedImage.getContents(),
                uploadedImage.getContentType()));
    }

    /**
     * Used in order to reset fresh license fields in case user hits cancel and wants to add the license again.
     */
    public void resetFreshLicenseValues() {
        freshLicense.setIcon(new LicenseIconDto());
        freshLicense.setName(StringUtils.EMPTY);
        freshLicense.setUrl(StringUtils.EMPTY);

        freshLicense.getLocalizedNames().forEach(localeTextDto -> localeTextDto.setText(StringUtils.EMPTY));
    }

    /**
     * Validates and sets active license status
     *
     * @param licenseDto
     */
    public void saveLicenseActiveStatus(LicenseDto licenseDto) {

        if (licenseDAO.countActiveLicenses() <= 1 && !licenseDto.isActive()) {
            licenseDto.setActive(true);
            displayNoLicensesActiveWarningMessage();
            return;
        }

        License license = licenseDAO.find(licenseDto.getId());
        license.setActive(licenseDto.isActive());
        licenseDAO.saveChanges(license);
    }

    /**
     * Saves new license and validates if there are any errors.
     *
     * @return redirect string
     */
    public String saveNewLicense() {
        if (isThereAnyValidationErrors(freshLicense)) {
            return StringUtils.EMPTY;
        }

        License license = licenseMapper.mapToLicense(freshLicense);
        licenseDAO.save(license);

        freshLicense = prepareFreshLicense();

        return "/dashboard-licenses.xhtml?&faces-redirect=true";
    }

    public String redirectToLicenseReorderPage() {
        return "/dashboard-licenses-reorder.xhtml?&faces-redirect=true";
    }

    // -------------------- PRIVATE --------------------

    /**
     * Prepares licenseDto in order to use it for adding new license.
     *
     * @return licenseDto
     */
    private LicenseDto prepareFreshLicense() {
        LicenseDto licenseDto = new LicenseDto();

        settingsWrapper.getConfiguredLocales().keySet().forEach(localeKey ->
                licenseDto.getLocalizedNames().add(new LocaleTextDto(Locale.forLanguageTag(localeKey), StringUtils.EMPTY)));

        licenseDto.setIcon(new LicenseIconDto());
        licenseDto.setPosition(licenseDAO.countLicenses() + 1);

        return licenseDto;
    }

    private boolean isThereAnyValidationErrors(LicenseDto licenseDto) {
        return !licenseErrorHandler.validateNewLicenseErrors(licenseDto).isEmpty();
    }

    private void displayNoLicensesActiveWarningMessage() {
        FacesContext.getCurrentInstance().
                addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR,
                        StringUtils.EMPTY,
                        BundleUtil.getStringFromBundle("dashboard.license.noActiveLicensesWarning")));
    }

}
