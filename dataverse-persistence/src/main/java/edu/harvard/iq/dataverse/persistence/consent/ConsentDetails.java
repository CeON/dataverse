package edu.harvard.iq.dataverse.persistence.consent;

import edu.harvard.iq.dataverse.persistence.config.LocaleConverter;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import java.util.Locale;

@Entity
public class ConsentDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(nullable = false, name = "consent_id")
    private Consent consent;

    @Column(nullable = false, updatable = false)
    @Convert(converter = LocaleConverter.class)
    private Locale language;

    @Column(nullable = false, updatable = false)
    private String text;

    @Column(nullable = false)
    private boolean required;

    @Column(nullable = false)
    private boolean hidden;

    // -------------------- CONSTRUCTORS --------------------

    protected ConsentDetails() {
    }

    public ConsentDetails(Consent consent, Locale language, String text, boolean required, boolean hidden) {
        this.consent = consent;
        this.language = language;
        this.text = text;
        this.required = required;
        this.hidden = hidden;
    }

    // -------------------- GETTERS --------------------

    public Long getId() {
        return id;
    }

    public Consent getConsent() {
        return consent;
    }

    /**
     * Language that the consent text is in.
     */
    public Locale getLanguage() {
        return language;
    }

    public String getText() {
        return text;
    }

    /**
     * Determines if the consent is required to be accepted.
     */
    public boolean isRequired() {
        return required;
    }

    /**
     * Determines if the consent is hidden from the user.
     */
    public boolean isHidden() {
        return hidden;
    }

    // -------------------- SETTERS --------------------

    public void setRequired(boolean required) {
        this.required = required;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }
}
