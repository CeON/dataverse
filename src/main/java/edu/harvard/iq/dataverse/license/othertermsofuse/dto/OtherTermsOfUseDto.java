package edu.harvard.iq.dataverse.license.othertermsofuse.dto;

public class OtherTermsOfUseDto {

    private Long id;

    private String name;

    private boolean active;

    private Long position;

    // -------------------- CONSTRUCTORS --------------------

    public OtherTermsOfUseDto(Long id, String name, boolean active, Long position) {
        this.id = id;
        this.name = name;
        this.active = active;
        this.position = position;
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

    public Long getPosition() {
        return position;
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

    public void setPosition(Long position) {
        this.position = position;
    }
}
