package edu.harvard.iq.dataverse.persistence.user;

import org.hibernate.validator.constraints.NotBlank;

import java.util.Objects;

/**
 * @author gdurand
 */
public class AuthenticatedUserDisplayInfo extends RoleAssigneeDisplayInfo {

    @NotBlank(message = "{user.lastName}")
    private String lastName;
    @NotBlank(message = "{user.firstName}")
    private String firstName;
    private String position;
    private String orcId;

    /*
     * @todo Shouldn't we persist the displayName too? It still exists on the
     * authenticateduser table.
     */
    public AuthenticatedUserDisplayInfo(String firstName, String lastName, String emailAddress,
                                        String affiliation, String position) {
        super(firstName + " " + lastName, emailAddress, affiliation, null);
        this.firstName = firstName;
        this.lastName = lastName;
        this.position = position;
    }

    public AuthenticatedUserDisplayInfo(String firstName, String lastName, String emailAddress, String orcId,
                                        String affiliation, String affiliationROR, String position) {
        super(firstName + " " + lastName, emailAddress, affiliation, affiliationROR);
        this.firstName = firstName;
        this.lastName = lastName;
        this.position = position;
        this.orcId = orcId;
    }

    public AuthenticatedUserDisplayInfo() {
        super("", "", "", "");
        firstName = "";
        lastName = "";
        position = "";
        orcId = "";
    }


    /**
     * Copy constructor (old school!)
     *
     * @param src the display info {@code this} will be a copy of.
     */
    public AuthenticatedUserDisplayInfo(AuthenticatedUserDisplayInfo src) {
        this(src.getFirstName(), src.getLastName(), src.getEmailAddress(), src.getOrcId(), src.getAffiliation(),
                src.getAffiliationROR(), src.getPosition());
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public String getOrcId() {
        return orcId;
    }

    public void setOrcId(String orcId) {
        this.orcId = orcId;
    }

    @Override
    public String toString() {
        return "AuthenticatedUserDisplayInfo{firstName=" + firstName + ", lastName=" + lastName +
                ", position=" + position + ", email=" + getEmailAddress() + ", orcid=" + getOrcId() + '}';
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 61 * hash + Objects.hashCode(this.firstName);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final AuthenticatedUserDisplayInfo other = (AuthenticatedUserDisplayInfo) obj;
        if (!Objects.equals(this.lastName, other.lastName)) {
            return false;
        }
        if (!Objects.equals(this.firstName, other.firstName)) {
            return false;
        }
        return Objects.equals(this.position, other.position) && Objects.equals(this.orcId, other.orcId) && super.equals(obj);
    }

}

