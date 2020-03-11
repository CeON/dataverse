package edu.harvard.iq.dataverse.consent.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Lists;

import java.util.List;

public class ConsentApiDto {

    private Long id;
    private String name;
    private List<ConsentDetailsApiDto> consentDetails;
    private List<ConsentActionApiDto> consentActions;
    private int displayOrder;
    private boolean required;
    private boolean hidden;

    // -------------------- CONSTRUCTORS --------------------

    public ConsentApiDto(@JsonProperty(value = "id") Long id,
                         @JsonProperty(value = "name", required = true) String name,
                         @JsonProperty(value = "displayOrder", required = true) int displayOrder,
                         @JsonProperty(value = "required", required = true) boolean required,
                         @JsonProperty(value = "hidden", required = true) boolean hidden,
                         @JsonProperty(value = "consentDetails", required = true) List<ConsentDetailsApiDto> consentDetails,
                         @JsonProperty(value = "consentActions", required = true) List<ConsentActionApiDto> consentActions){
        this.id = id;
        this.name = name;
        this.displayOrder = displayOrder;
        this.required = required;
        this.hidden = hidden;
        this.consentDetails = consentDetails != null ? consentDetails : Lists.newArrayList();
        this.consentActions = consentActions != null ? consentActions : Lists.newArrayList();
    }

    // -------------------- GETTERS --------------------

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<ConsentDetailsApiDto> getConsentDetails() {
        return consentDetails;
    }

    public List<ConsentActionApiDto> getConsentActions() {
        return consentActions;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public boolean isRequired() {
        return required;
    }

    public boolean isHidden() {
        return hidden;
    }
}
