package edu.harvard.iq.dataverse.consent.api;

import edu.harvard.iq.dataverse.persistence.consent.Consent;
import edu.harvard.iq.dataverse.persistence.consent.ConsentAction;
import edu.harvard.iq.dataverse.persistence.consent.ConsentDetails;

import javax.ejb.Stateless;
import java.util.List;
import java.util.stream.Collectors;

@Stateless
public class ConsentApiMapper {

    public ConsentApiDto consentToConsentApiDto(Consent consent) {

        ConsentApiDto consentDto = new ConsentApiDto(consent.getId(),
                                                     consent.getName(),
                                                     consent.getDisplayOrder(),
                                                     consent.isRequired(),
                                                     consent.isHidden());

        List<ConsentDetailsApiDto> consentDetails = consent.getConsentDetails().stream()
                .map(this::consentDetailsToConsentDetailsDto)
                .collect(Collectors.toList());

        List<ConsentActionApiDto> consentActionDtos = consent.getConsentActions().stream()
                .map(this::consentActionToConsentActionDto)
                .collect(Collectors.toList());

        consentDto.getConsentDetails().addAll(consentDetails);
        consentDto.getConsentActions().addAll(consentActionDtos);

        return consentDto;
    }

    // -------------------- PRIVATE --------------------

    private ConsentDetailsApiDto consentDetailsToConsentDetailsDto(ConsentDetails consentDetails) {
        return new ConsentDetailsApiDto(consentDetails.getId(), consentDetails.getLanguage(), consentDetails.getText());
    }

    private ConsentActionApiDto consentActionToConsentActionDto(ConsentAction consentAction) {
        return new ConsentActionApiDto(consentAction.getId(),
                                       consentAction.getConsentActionType(),
                                       consentAction.getActionOptions());
    }
}
