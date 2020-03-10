package edu.harvard.iq.dataverse.consent.api;

import edu.harvard.iq.dataverse.persistence.consent.ConsentActionType;
import io.vavr.control.Option;

public class ConsentActionApiDto {

    private Long id;
    private ConsentActionType consentActionType;
    private String actionOptions;

    // -------------------- CONSTRUCTORS --------------------

    public ConsentActionApiDto(Long id, ConsentActionType consentActionType, String actionOptions) {
        this.id = id;
        this.consentActionType = consentActionType;
        this.actionOptions = actionOptions;
    }

    // -------------------- GETTERS --------------------

    public Option<Long> getId() {
        return Option.of(id);
    }

    public ConsentActionType getConsentActionType() {
        return consentActionType;
    }

    public String getActionOptions() {
        return actionOptions;
    }
}
