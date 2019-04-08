package edu.harvard.iq.dataverse.license.othertermsofuse;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import java.io.Serializable;

@Entity
public class OtherTermsOfUse implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private boolean active;

    @Column(nullable = false)
    private Long position;

    //-------------------- GETTERS --------------------

    /**
     * Returns database id of otherTermsOfUse
     */
    public Long getId() {
        return id;
    }

    /**
     * Returns universal name of otherTermsOfUse that can be presented
     * in external representations (such as Dublin Core, DDI, etc.)
     * or if locale specific version of name is not present
     */
    public String getName() {
        return name;
    }

    /**
     * Returns true if otherTermsOfUse can be assigned
     * to newly created data files.
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Returns on what position otherTermsOfUse should be displayed
     * when presenting all or subset of all licenses.
     */
    public Long getPosition() {
        return position;
    }

    //-------------------- SETTERS --------------------

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
