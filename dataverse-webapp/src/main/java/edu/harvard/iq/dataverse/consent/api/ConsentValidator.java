package edu.harvard.iq.dataverse.consent.api;

import edu.harvard.iq.dataverse.persistence.consent.Consent;
import edu.harvard.iq.dataverse.persistence.consent.ConsentDetails;
import io.vavr.Tuple2;
import io.vavr.control.Option;

import javax.ejb.Stateless;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Stateless
class ConsentValidator {

    List<String> validateConsentEditing(ConsentApiDto consentApiDto, Consent consent) {
        ArrayList<String> errors = new ArrayList<>();

        validateName(consentApiDto.getName(), consent.getName())
                .peek(errors::add);

        validateDisplayOrder(consentApiDto.getDisplayOrder(), consent.getDisplayOrder())
                .peek(errors::add);

        validateConsentDetails(consentApiDto.getConsentDetails(), consent.getConsentDetails())
                .peek(errors::add);

        return errors;
    }

    // -------------------- PRIVATE --------------------

    private Option<String> validateName(String editedName, String originalName) {
        if (!editedName.equals(originalName)) {
            return Option.of("Consent names must be equal");
        }

        return Option.none();
    }

    private Option<String> validateDisplayOrder(int editedDisplayOrder, int originalDisplayOrder) {
        if (!(editedDisplayOrder == originalDisplayOrder)) {
            return Option.of("Consent display order must be equal");
        }

        return Option.none();
    }

    private Option<String> validateConsentDetails(List<ConsentDetailsApiDto> editedConsentDetails, List<ConsentDetails> originalConsentDetails) {

        if (editedConsentDetails.size() == originalConsentDetails.size()) {
            editedConsentDetails.sort(Comparator.comparing(consent -> consent.getId().get()));
            originalConsentDetails.sort(Comparator.comparing(ConsentDetails::getId));

            io.vavr.collection.List<Tuple2<ConsentDetailsApiDto, ConsentDetails>> zippedConsents = io.vavr.collection.List
                    .ofAll(editedConsentDetails)
                    .zip(originalConsentDetails);

            return zippedConsents
                    .map(consents -> validateConsentDetail(consents._1(), consents._2()))
                    .filter(Option::isDefined)
                    .get();
        }

        List<ConsentDetailsApiDto> freshConsents = editedConsentDetails.stream()
                .filter(consent -> consent.getId().isEmpty())
                .collect(Collectors.toList());

        if (isFreshConsentContainsDuplicatedLocale(freshConsents, originalConsentDetails)) {
            return Option.of("New consent detail has duplicated language");
        }

        for (ConsentDetailsApiDto freshConsent : freshConsents) {
            if (freshConsent.getText().isEmpty()) {
                return Option.of("New consent detail text cannot be empty");
            }
        }

        return Option.none();
    }

    private Option<String> validateConsentDetail(ConsentDetailsApiDto editedConsentDetail, ConsentDetails originalConsentDetail) {
        if (!editedConsentDetail.getText().equals(originalConsentDetail.getText()) ||
                !editedConsentDetail.getLanguage().equals(originalConsentDetail.getLanguage())) {
            return Option.of("Consent details cannot be edited!");
        }

        return Option.none();
    }

    private boolean isFreshConsentContainsDuplicatedLocale(List<ConsentDetailsApiDto> freshConsents, List<ConsentDetails> originalConsentDetails) {
        return freshConsents.stream()
                .anyMatch(consent -> isLocaleAmongConsentDetails(consent.getLanguage(), originalConsentDetails));

    }

    private boolean isLocaleAmongConsentDetails(Locale locale, List<ConsentDetails> originalConsentDetails) {
        return originalConsentDetails.stream()
                .anyMatch(consentDetails -> consentDetails.getLanguage().equals(locale));
    }
}
