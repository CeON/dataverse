package edu.harvard.iq.dataverse.persistence.user;

import edu.harvard.iq.dataverse.persistence.JpaEntity;
import edu.harvard.iq.dataverse.persistence.config.LocaleConverter;
import edu.harvard.iq.dataverse.persistence.config.ValidateEmail;
import edu.harvard.iq.dataverse.persistence.consent.AcceptedConsent;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetLock;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotBlank;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Transient;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * When adding an attribute to this class, be sure to update the following:
 * <p>
 * (1) AuthenticatedUser.toJSON() - within this class   (REQUIRED)
 * (2) UserServiceBean.getUserListCore() - native SQL query
 * (3) UserServiceBean.createAuthenticatedUserForView() - add values to a detached AuthenticatedUser object
 *
 * @author rmp553
 */
@NamedQueries({
        @NamedQuery(name = "AuthenticatedUser.findAll",
                query = "select au from AuthenticatedUser au"),
        @NamedQuery(name = "AuthenticatedUser.findSuperUsers",
                query = "SELECT au FROM AuthenticatedUser au WHERE au.superuser = TRUE"),
        @NamedQuery(name = "AuthenticatedUser.findByIdentifier",
                query = "select au from AuthenticatedUser au WHERE au.userIdentifier=:identifier"),
        @NamedQuery(name = "AuthenticatedUser.findByEmail",
                query = "select au from AuthenticatedUser au WHERE LOWER(au.email)=LOWER(:email)"),
        @NamedQuery(name = "AuthenticatedUser.countOfIdentifier",
                query = "SELECT COUNT(a) FROM AuthenticatedUser a WHERE a.userIdentifier=:identifier"),
        @NamedQuery(name = "AuthenticatedUser.filter",
                query = "select au from AuthenticatedUser au WHERE ("
                        + "au.userIdentifier like :query OR "
                        + "lower(concat(au.firstName,' ',au.lastName)) like lower(:query))"),
        @NamedQuery(name = "AuthenticatedUser.findAdminUser",
                query = "select au from AuthenticatedUser au WHERE "
                        + "au.superuser = true "
                        + "order by au.id")

})
@Entity
public class AuthenticatedUser implements User, Serializable, JpaEntity<Long> {

    public static final String IDENTIFIER_PREFIX = "@";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    /**
     * @todo Shouldn't there be some constraints on what the userIdentifier is
     * allowed to be? It can't be as restrictive as the "userName" field on
     * BuiltinUser because we can't predict what Shibboleth Identity Providers
     * (IdPs) will send (typically in the "eppn" SAML assertion) but perhaps
     * spaces, for example, should be disallowed. Right now "elisah.da mota" can
     * be persisted as a userIdentifier per
     * https://github.com/IQSS/dataverse/issues/2945
     */
    @NotNull
    @Column(nullable = false, unique = true)
    private String userIdentifier;

    @ValidateEmail(message = "{user.invalidEmail}")
    @NotNull
    @Column(nullable = false, unique = true)
    private String email;
    private String affiliation;
    private String position;

    @NotBlank(message = "{user.lastName}")
    private String lastName;

    @NotBlank(message = "{user.firstName}")
    private String firstName;

    @Column(nullable = true)
    private Timestamp emailConfirmed;

    @Column(nullable = false)
    private Timestamp createdTime;

    @Column(nullable = true)
    private Timestamp lastLoginTime;    // last user login timestamp

    @Column(nullable = true)
    private Timestamp lastApiUseTime;   // last API use with user's token

    @Column(nullable = false)
    @Convert(converter = LocaleConverter.class)
    private Locale notificationsLanguage = Locale.ENGLISH;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<AcceptedConsent> acceptedConsents = new ArrayList<>();

    private boolean superuser;

    /**
     * @todo Consider storing a hash of *all* potentially interesting Shibboleth
     * attribute key/value pairs, not just the Identity Provider (IdP).
     */
    @Transient
    private String shibIdentityProvider;

    @Override
    public String getIdentifier() {
        return IDENTIFIER_PREFIX + userIdentifier;
    }

    @OneToMany(mappedBy = "user", cascade = {CascadeType.REMOVE, CascadeType.MERGE, CascadeType.PERSIST})
    private List<DatasetLock> datasetLocks;

    public List<DatasetLock> getDatasetLocks() {
        return datasetLocks;
    }

    public void setDatasetLocks(List<DatasetLock> datasetLocks) {
        this.datasetLocks = datasetLocks;
    }

    @Override
    public AuthenticatedUserDisplayInfo getDisplayInfo() {
        return new AuthenticatedUserDisplayInfo(firstName, lastName, email, affiliation, position);
    }

    /**
     * Takes the passed info object and updated the internal fields according to it.
     *
     * @param inf the info from which we update the fields.
     */
    public void applyDisplayInfo(AuthenticatedUserDisplayInfo inf) {
        setFirstName(inf.getFirstName());
        setLastName(inf.getLastName());
        if (StringUtils.isNotBlank(inf.getEmailAddress())) {
            setEmail(inf.getEmailAddress());
        }
        if (StringUtils.isNotBlank(inf.getAffiliation())) {
            setAffiliation(inf.getAffiliation());
        }
        if (StringUtils.isNotBlank(inf.getPosition())) {
            setPosition(inf.getPosition());
        }
    }


    //For User List Admin dashboard
    @Transient
    private String roles;

    public String getRoles() {
        return roles;
    }

    public void setRoles(String roles) {
        this.roles = roles;
    }

    @Override
    public boolean isAuthenticated() {
        return true;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserIdentifier() {
        return userIdentifier;
    }

    public void setUserIdentifier(String userIdentifier) {
        this.userIdentifier = userIdentifier;
    }

    public String getName() {
        return firstName + " " + lastName;
    }

    public String getEmail() {
        return email;
    }

    //Stripping spaces to continue support of #2945
    public void setEmail(String email) {
        this.email = email.trim();
    }

    public String getAffiliation() {
        return affiliation;
    }

    public void setAffiliation(String affiliation) {
        this.affiliation = affiliation;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
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

    public Timestamp getEmailConfirmed() {
        return emailConfirmed;
    }

    public void setEmailConfirmed(Timestamp emailConfirmed) {
        this.emailConfirmed = emailConfirmed;
    }

    public Locale getNotificationsLanguage() {
        return notificationsLanguage;
    }

    public void setNotificationsLanguage(Locale notificationsLanguage) {
        this.notificationsLanguage = notificationsLanguage;
    }

    /**
     * Consents that were accepted by user.
     * This is history table so no element should be removed from this list.
     */
    public List<AcceptedConsent> getAcceptedConsents() {
        return acceptedConsents;
    }

    @Override
    public boolean isSuperuser() {
        return superuser;
    }

    public void setSuperuser(boolean superuser) {
        this.superuser = superuser;
    }

    @OneToOne(mappedBy = "authenticatedUser")
    private AuthenticatedUserLookup authenticatedUserLookup;

    public AuthenticatedUserLookup getAuthenticatedUserLookup() {
        return authenticatedUserLookup;
    }

    public void setAuthenticatedUserLookup(AuthenticatedUserLookup authenticatedUserLookup) {
        this.authenticatedUserLookup = authenticatedUserLookup;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    @Override
    public boolean equals(Object object) {
        // TODO: Warning - this method won't work in the case the id fields are not set
        return object instanceof AuthenticatedUser
                && Objects.equals(getId(), ((AuthenticatedUser) object).getId());
    }

    public String getShibIdentityProvider() {
        return shibIdentityProvider;
    }

    public void setShibIdentityProvider(String shibIdentityProvider) {
        this.shibIdentityProvider = shibIdentityProvider;
    }

    @Override
    public String toString() {
        return "[AuthenticatedUser identifier:" + getIdentifier() + "]";
    }

    public String getSortByString() {
        return String.format("%s %s %s", getLastName(), getFirstName(), getUserIdentifier());
    }

    public void setLastLoginTime(Timestamp lastLoginTime) {
        this.lastLoginTime = lastLoginTime;
    }

    public Timestamp getLastLoginTime() {
        return this.lastLoginTime;
    }

    public void setCreatedTime(Timestamp createdTime) {
        this.createdTime = createdTime;
    }

    public Timestamp getCreatedTime() {
        return this.createdTime;
    }

    public void setLastApiUseTime(Timestamp lastApiUseTime) {
        this.lastApiUseTime = lastApiUseTime;
    }

    public Timestamp getLastApiUseTime() {
        return this.lastApiUseTime;
    }

    public String getOrcidId() {
        String authProviderId = getAuthenticatedUserLookup().getAuthenticationProviderId();
        return AuthenticatedUserLookup.ORCID_PROVIDER_ID_PRODUCTION.equals(authProviderId)
                ? getAuthenticatedUserLookup().getPersistentUserId() : null;
    }
}
