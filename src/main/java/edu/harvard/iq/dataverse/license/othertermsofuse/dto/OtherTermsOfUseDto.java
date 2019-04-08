package edu.harvard.iq.dataverse.license.othertermsofuse.dto;

public class OtherTermsOfUseDto {

    private Long id;

    private String name;

    private boolean active;

    // -------------------- CONSTRUCTORS --------------------

    public OtherTermsOfUseDto(Long id, String name, boolean active) {
        this.id = id;
        this.name = name;
        this.active = active;
    }

    // -------------------- GETTERS --------------------

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }

    // -------------------- SETTERS --------------------


    public void setId(Long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
