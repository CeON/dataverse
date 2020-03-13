package edu.harvard.iq.dataverse.consent.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import edu.harvard.iq.dataverse.persistence.consent.ConsentActionType;

import java.util.Objects;
import java.util.Optional;

public class ConsentActionApiDto {

    private Long id;
    private ConsentActionType consentActionType;
    private String actionOptions;

    // -------------------- CONSTRUCTORS --------------------

    public ConsentActionApiDto(@JsonProperty(value = "id") Long id,
                               @JsonProperty(value = "consentActionType", required = true) ConsentActionType consentActionType,
                               @JsonProperty(value = "actionOptions", required = true) String actionOptions) {
        Objects.requireNonNull(consentActionType);
        Objects.requireNonNull(actionOptions);

        this.id = id;
        this.consentActionType = consentActionType;
        this.actionOptions = actionOptions;
    }

    // -------------------- GETTERS --------------------

    public Optional<Long> getId() {
        return Optional.ofNullable(id);
    }

    public ConsentActionType getConsentActionType() {
        return consentActionType;
    }

    public String getActionOptions() {
        return actionOptions;
    }
}
