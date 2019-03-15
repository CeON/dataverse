package edu.harvard.iq.dataverse.license;

import com.google.common.collect.ImmutableList;
import edu.harvard.iq.dataverse.DataverseSession;
import edu.harvard.iq.dataverse.PermissionsWrapper;
import org.apache.commons.lang.StringUtils;

import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;

@ViewScoped
@Named("LicenseListingPage")
public class LicenseListingPage {

    @Inject
    private DataverseSession session;

    @Inject
    private PermissionsWrapper permissionsWrapper;

    @Inject
    private LicenseDAO licenseDAO;

    private List<License> polEngLicenses;

    // -------------------- GETTERS --------------------

    public List<License> getPolEngLicenses() {
        return polEngLicenses;
    }


    // -------------------- LOGIC --------------------

    public String init() {

        if (session.getUser() == null || !session.getUser().isAuthenticated() || !session.getUser().isSuperuser()) {
            return permissionsWrapper.notAuthorized();
        }

        polEngLicenses = licenseDAO.findLicensesWithLocales(ImmutableList.of("pl", "en"));

        return StringUtils.EMPTY;
    }
}
