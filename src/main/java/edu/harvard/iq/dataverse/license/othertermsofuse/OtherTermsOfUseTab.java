package edu.harvard.iq.dataverse.license.othertermsofuse;

import edu.harvard.iq.dataverse.DataverseSession;
import edu.harvard.iq.dataverse.PermissionsWrapper;
import edu.harvard.iq.dataverse.license.othertermsofuse.dto.OtherTermsOfUseDto;
import edu.harvard.iq.dataverse.license.othertermsofuse.dto.OtherTermsOfUseMapper;
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
    private OtherTermsOfUseMapper otherTermsOfUseMapper;

    @Inject
    private OtherTermsOfUseDAO otherTermsOfUseDAO;

    private List<OtherTermsOfUseDto> otherTermsOfUse = new ArrayList<>();

    // -------------------- GETTERS --------------------

    public List<OtherTermsOfUseDto> getOtherTermsOfUse() {
        return otherTermsOfUse;
    }

    // -------------------- LOGIC --------------------

    public String init() {

        if (!session.getUser().isSuperuser()) {
            return permissionsWrapper.notAuthorized();
        }

        otherTermsOfUse = otherTermsOfUseMapper.mapToDtos(otherTermsOfUseDAO.findAll());

        return StringUtils.EMPTY;
    }

    public void saveLicenseActiveStatus(OtherTermsOfUseDto otherTermsOfUseDto) {

        OtherTermsOfUse otherTermsOfUse = otherTermsOfUseDAO.find(otherTermsOfUseDto.getId());
        otherTermsOfUse.setActive(otherTermsOfUseDto.isActive());

        otherTermsOfUseDAO.saveChanges(otherTermsOfUse);
    }
}
