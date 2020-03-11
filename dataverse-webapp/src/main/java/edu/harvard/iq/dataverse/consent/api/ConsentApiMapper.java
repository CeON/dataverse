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


        List<ConsentDetailsApiDto> consentDetails = consent.getConsentDetails().stream()
                .map(this::consentDetailsToConsentDetailsDto)
                .collect(Collectors.toList());

        List<ConsentActionApiDto> consentActionDtos = consent.getConsentActions().stream()
                .map(this::consentActionToConsentActionDto)
                .collect(Collectors.toList());

        return new ConsentApiDto(consent.getId(),
                                 consent.getName(),
                                 consent.getDisplayOrder(),
                                 consent.isRequired(),
                                 consent.isHidden(),
                                 consentDetails,
                                 consentActionDtos);
    }

    public Consent updateAllowedProperties(ConsentApiDto updatedConsent, Consent originalConsent){
        originalConsent.setHidden(updatedConsent.isHidden());
        originalConsent.setDisplayOrder(updatedConsent.getDisplayOrder());

        List<ConsentDetails> addedConsentDetails = updatedConsent.getConsentDetails().stream()
                .filter(updatedCons -> updatedCons.getId().isEmpty())
                .map(updatedCons -> consentDetailsApiDtoToConsentDetails(updatedCons, originalConsent))
                .collect(Collectors.toList());

        List<ConsentAction> freshConsentActions = updatedConsent.getConsentActions().stream()
                .filter(updatedCons -> updatedCons.getId().isEmpty())
                .map(updatedCons -> consentActionApiDtoToConsentAction(updatedCons, originalConsent))
                .collect(Collectors.toList());

        updateConsentActions(updatedConsent, originalConsent);

        originalConsent.getConsentDetails().addAll(addedConsentDetails);
        originalConsent.getConsentActions().addAll(freshConsentActions);
        originalConsent.getConsentActions().removeIf(consentAction -> !isConsentActionPresent(updatedConsent, consentAction));

        return originalConsent;
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

    private ConsentDetails consentDetailsApiDtoToConsentDetails(ConsentDetailsApiDto updatedConsentDetails, Consent detailsOwner) {
        return new ConsentDetails(detailsOwner, updatedConsentDetails.getLanguage(), updatedConsentDetails.getText());
    }

    private ConsentAction consentActionApiDtoToConsentAction(ConsentActionApiDto updatedConsentAction, Consent actionOwner) {

        return new ConsentAction(actionOwner,
                                 updatedConsentAction.getConsentActionType(),
                                 updatedConsentAction.getActionOptions());
    }

    private void updateConsentActions(ConsentApiDto updatedConsent, Consent originalConsent) {
        for (ConsentActionApiDto updatedConsentAction : updatedConsent.getConsentActions()) {
            if (updatedConsentAction.getId().isDefined()){

                for (ConsentAction originalConsAction : originalConsent.getConsentActions()){
                    if (updatedConsentAction.getId().get().equals(originalConsAction.getId())){
                        updateConsentAction(updatedConsentAction, originalConsAction);
                    }

                }

            }
        }
    }

    private ConsentAction updateConsentAction(ConsentActionApiDto updatedConsentAction, ConsentAction originalAction) {

        originalAction.setActionOptions(updatedConsentAction.getActionOptions());
        originalAction.setConsentActionType(updatedConsentAction.getConsentActionType());

        return originalAction;
    }

    private boolean isConsentActionPresent(ConsentApiDto updatedConsent, ConsentAction consentAction){
        return updatedConsent.getConsentActions().stream()
                .anyMatch(consAction -> consAction.getId()
                        .getOrElse(Long.MAX_VALUE)
                        .equals(consentAction.getId()));
    }

}
