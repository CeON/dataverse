package edu.harvard.iq.dataverse.persistence.consent;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import java.util.ArrayList;
import java.util.List;

/**
 * Class represents consents for users.
 * By nature removing/modifying anything from {@link ConsentDetails} is forbidden since we don't want to alter history.
 */
@Entity
public class Consent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, updatable = false)
    private String name;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
    private List<ConsentDetails> consentDetails = new ArrayList<>();

    @OneToMany(mappedBy = "consent", cascade = CascadeType.ALL)
    private List<ConsentAction> consentActions = new ArrayList<>();

    // -------------------- CONSTRUCTORS --------------------

    protected Consent() {
    }

    public Consent(Long id, String name, List<ConsentDetails> consentDetails) {
        this.id = id;
        this.name = name;
        this.consentDetails = consentDetails;
    }

    // -------------------- GETTERS --------------------

    public Long getId() {
        return id;
    }

    /**
     * Unique name for consent.
     */
    public String getName() {
        return name;
    }

    /**
     * Details about this consent, contains for example text that is specific to locale.
     */
    public List<ConsentDetails> getConsentDetails() {
        return consentDetails;
    }

    /**
     * Actions that will be taken for this consent if for example user will accept it.
     * Can be empty.
     */
    public List<ConsentAction> getConsentActions() {
        return consentActions;
    }
}
