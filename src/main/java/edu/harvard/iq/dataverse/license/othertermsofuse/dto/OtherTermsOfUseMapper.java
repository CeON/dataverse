package edu.harvard.iq.dataverse.license.othertermsofuse.dto;

import edu.harvard.iq.dataverse.license.othertermsofuse.OtherTermsOfUse;

import javax.ejb.Stateless;
import java.util.List;
import java.util.stream.Collectors;

@Stateless
public class OtherTermsOfUseMapper {

    // -------------------- LOGIC --------------------

    public OtherTermsOfUseDto mapToDto(OtherTermsOfUse otherTermsOfUse) {

        return new OtherTermsOfUseDto(otherTermsOfUse.getId(),
                otherTermsOfUse.getName(),
                otherTermsOfUse.isActive(),
                otherTermsOfUse.getPosition());
    }

    public List<OtherTermsOfUseDto> mapToDtos(List<OtherTermsOfUse> otherTermsOfUses) {
        return otherTermsOfUses.stream().map(this::mapToDto).collect(Collectors.toList());
    }
}
