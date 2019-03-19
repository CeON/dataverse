package edu.harvard.iq.dataverse.license;

import edu.harvard.iq.dataverse.DataverseSession;
import edu.harvard.iq.dataverse.PermissionsWrapper;
import edu.harvard.iq.dataverse.license.dto.LicenseDto;
import edu.harvard.iq.dataverse.license.dto.LicenseMapper;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.apache.commons.lang.StringUtils;

import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.Serializable;
import java.util.List;


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
    private PermissionsWrapper permissionsWrapper;

    @Inject
    private LicenseDAO licenseDAO;

    @Inject
    private LicenseMapper licenseMapper;

    @Inject
    private InvalidLicensesCreator invalidLicensesCreator;

    private List<LicenseDto> licenses;

    // -------------------- GETTERS --------------------

    public List<LicenseDto> getLicenses() {
        return licenses;
    }

    // -------------------- LOGIC --------------------

    public String init() {

        if (session.getUser() == null || !session.getUser().isAuthenticated() || !session.getUser().isSuperuser()) {
            return permissionsWrapper.notAuthorized();
        }

        licenses = licenseMapper.mapToDtos(licenseDAO.findAll());

        licenses.add(invalidLicensesCreator.createAllRightsReserved(licenses.size() + 1L));
        licenses.add(invalidLicensesCreator.createRestrictedAccess(licenses.size() + 1L));

        return StringUtils.EMPTY;
    }

    /**
     * Calculates and returns count of active and inactive licenses in the whole Dataverse.
     *
     * @return active and inactive licenses count
     */
    public Tuple2<Long, Long> getActiveAndInactiveLicensesCount() {
        if (licenses == null) {
            return Tuple.of(0L, 0L);
        }

        long activeLicenses = licenses.stream().filter(LicenseDto::isActive).count();
        long inactiveLicenses = licenses.size() - activeLicenses;

        return Tuple.of(activeLicenses, inactiveLicenses);
    }

}
