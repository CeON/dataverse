package edu.harvard.iq.dataverse.persistence.user;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toSet;
import static javax.persistence.GenerationType.IDENTITY;

import java.io.Serializable;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import edu.harvard.iq.dataverse.common.RoleTranslationUtil;
import edu.harvard.iq.dataverse.persistence.DvObject;
import edu.harvard.iq.dataverse.persistence.JpaEntity;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;

/**
 * A role is an annotated set of permissions. A role belongs
 * to a {@link Dataverse}. Users may assume roles from the current dataverse,
 * or from its parent dataverses, up to the first permission root dataverse.
 *
 * @author michael
 */
@SuppressWarnings("serial")
@Entity
@Table(indexes = {@Index(columnList = "owner_id")
        , @Index(columnList = "name")
        , @Index(columnList = "alias")})
public class DataverseRole implements Serializable, JpaEntity<Long> {

    //constants for the built in roles references in the code
    public enum BuiltInRole {
        ADMIN("admin"),
        FILE_DOWNLOADER("fileDownloader"),
        FULL_CONTRIBUTOR("fullContributor"),
        DV_CONTRIBUTOR("dvContributor"),
        DS_CONTRIBUTOR("dsContributor"),
        /**
         * Heads up that this says "editor" which comes from
         * scripts/api/data/role-editor.json but the name is "Contributor". The
         * *alias* is "editor". Don't be fooled!
         */
        EDITOR("editor"),
        CURATOR("curator"),
        MEMBER("member"),
        DEPOSITOR("depositor"),
    	COLLECTION_CUSTODIAN("collectionCustodian");
        
        private final String alias;
        
        BuiltInRole(final String alias) {
            this.alias = alias;
        }
        
        public String getAlias() {
            return this.alias;
        }
        
        public static BuiltInRole fromAlias(final String alias) {
            return stream(values())
                .filter(role -> role.getAlias().equals(alias))
                .findFirst()
                .orElseThrow(IllegalArgumentException::new);
        }
    }

    public static final String NONE = "none";


	public static final Comparator<DataverseRole> compareByName = (role1, role2) -> {
		final int result = role1.getName().compareTo(role2.getName());
		return result != 0 ? result : role1.getOwnerId().compareTo(role2.getOwnerId());
	};
	
	private final static Long zero = Long.valueOf(0L);

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Pattern(regexp = ".+", message = "{role.name}")
    @Column(nullable = false)
    private String name;

    @Size(max = 255, message = "{role.desc.maxLength}")
    private String description;

    @Size(max = 32, message = "{role.alias.maxLength}")
    @Pattern(regexp = "[a-zA-Z0-9\\_\\-]+", message = "{role.alias.illegalCharacters}")
    @Column(nullable = false, unique = true)
    private String alias;

    /**
     * Stores the permissions in a bit set.
     */
    private long permissionBits;

    @ManyToOne
    @JoinColumn(nullable = true)
    private DvObject owner;

    public Long getId() {
        return this.id;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public String getName() {
        return RoleTranslationUtil.getLocaleNameFromAlias(this.alias, this.name);
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getDescription() {
        return RoleTranslationUtil.getLocaleDescriptionFromAlias(this.alias, this.description);
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public String getAlias() {
        return this.alias;
    }

    public void setAlias(final String alias) {
        this.alias = alias;
    }

    public DvObject getOwner() {
        return this.owner;
    }
    
    private Long getOwnerId() {
    	return this.owner == null ? zero : this.owner.getId();
    }

    public void setOwner(final DvObject owner) {
        this.owner = owner;
    }

    public void addPermissions(final Collection<Permission> ps) {
        for (final Permission p : ps) {
            addPermission(p);
        }
    }

    public void addPermission(final Permission p) {
        this.permissionBits |= p.bitValue();
    }

    public void clearPermissions() {
        this.permissionBits = 0l;
    }

    public Set<Permission> permissions() {
        return permissionsStream().collect(toSet());
    }
    
    public Stream<Permission> permissionsStream() {
    	return Permission.streamFrom(this.permissionBits);
    }

    public long getPermissionsBits() {
        return this.permissionBits;
    }

    @Override
    public String toString() {
        return "DataverseRole{" + "id=" + this.id + ", alias=" + this.alias + '}';
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.id);
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final DataverseRole other = (DataverseRole) obj;
        return Objects.equals(this.id, other.id);
    }
    
    public boolean has(final Permission p) {
    	return p.isIn(this.permissionBits);
    }
    
    public boolean hasAny(final Permission p1, final Permission p2) {
    	return has(p1) | has(p2);
    }

    public boolean hasPermissionFor(final Class<? extends DvObject> dvObjectClass) {
    	return permissionsStream().anyMatch(p -> p.appliesTo(dvObjectClass));
    } 
    
    public boolean isContributor() {
    	return this.alias.equals(BuiltInRole.DS_CONTRIBUTOR.getAlias()) ||
    			this.alias.equals(BuiltInRole.DV_CONTRIBUTOR.getAlias()) ||
    			this.alias.equals(BuiltInRole.FULL_CONTRIBUTOR.getAlias());
    }
}
