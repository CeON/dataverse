package edu.harvard.iq.dataverse.license.othertermsofuse;

import edu.harvard.iq.dataverse.DataverseSession;
import edu.harvard.iq.dataverse.PermissionsWrapper;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import org.apache.commons.lang.StringUtils;

import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@ViewScoped
@Named("OtherTermsOfUseTab")
public class OtherTermsOfUseTab implements Serializable {

    @Inject
    private DataverseSession session;

    @Inject
    private PermissionsWrapper permissionsWrapper;

    @Inject
    private SettingsServiceBean settingsServiceBean;

    private List<OtherTermsOfUseDto> otherTermsOfUseDto = new ArrayList<>();

    // -------------------- GETTERS --------------------

    public List<OtherTermsOfUseDto> getOtherTermsOfUseDto() {
        return otherTermsOfUseDto;
    }

    // -------------------- LOGIC --------------------

    public String init() {

        if (!session.getUser().isSuperuser()) {
            return permissionsWrapper.notAuthorized();
        }

        otherTermsOfUseDto.add(new OtherTermsOfUseDto("Allrightsreserved",
                "All rights reserved",
                Boolean.valueOf(settingsServiceBean.get("Allrightsreserved"))));

        otherTermsOfUseDto.add(new OtherTermsOfUseDto("Restrictedaccess",
                "Restricted access",
                Boolean.valueOf(settingsServiceBean.get("Restrictedaccess"))));

        return StringUtils.EMPTY;
    }

    public void saveLicenseActiveStatus(OtherTermsOfUseDto otherTermsOfUseDto) {
        settingsServiceBean.set(otherTermsOfUseDto.getKey(), String.valueOf(otherTermsOfUseDto.isActive()));
    }
}
