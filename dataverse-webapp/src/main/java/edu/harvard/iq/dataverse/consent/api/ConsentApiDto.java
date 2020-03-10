package edu.harvard.iq.dataverse.consent.api;

import java.util.ArrayList;
import java.util.List;

public class ConsentApiDto {

    private long id;
    private String name;
    private List<ConsentDetailsApiDto> consentDetails = new ArrayList<>();
    private List<ConsentActionApiDto> consentActions = new ArrayList<>();
    private int displayOrder;
    private boolean required;
    private boolean hidden;

    // -------------------- CONSTRUCTORS --------------------

    public ConsentApiDto(long id, String name, int displayOrder, boolean required, boolean hidden) {
        this.id = id;
        this.name = name;
        this.displayOrder = displayOrder;
        this.required = required;
        this.hidden = hidden;
    }

    // -------------------- GETTERS --------------------

    public long getId() {
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
