package edu.harvard.iq.dataverse.consent.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import edu.harvard.iq.dataverse.persistence.consent.ConsentActionType;
import io.vavr.control.Option;

public class ConsentActionApiDto {

    private Long id;
    private ConsentActionType consentActionType;
    private String actionOptions;

    // -------------------- CONSTRUCTORS --------------------

    public ConsentActionApiDto(@JsonProperty(value = "id") Long id,
                               @JsonProperty(value = "consentActionType", required = true) ConsentActionType consentActionType,
                               @JsonProperty(value = "actionOptions", required = true) String actionOptions) {
        Preconditions.checkArgument(consentActionType != null && actionOptions != null);
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
