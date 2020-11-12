package edu.harvard.iq.dataverse.persistence.user;

import edu.harvard.iq.dataverse.common.BitSet;
import edu.harvard.iq.dataverse.common.RoleTranslationUtil;
import edu.harvard.iq.dataverse.persistence.DvObject;
import edu.harvard.iq.dataverse.persistence.JpaEntity;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;

/**
 * A role is an annotated set of permissions. A role belongs
 * to a {@link Dataverse}. Users may assume roles from the current dataverse,
 * or from its parent dataverses, up to the first permission root dataverse.
 *
 * @author michael
 */
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
        DEPOSITOR("depositor");
        
        private String alias;
        
        BuiltInRole(String alias) {
            this.alias = alias;
        }
        
        public String getAlias() {
            return alias;
        }
        
        public static BuiltInRole fromAlias(String alias) {
            return Arrays.asList(BuiltInRole.values()).stream()
                .filter(builtInRole -> builtInRole.getAlias().equals(alias))
                .findFirst()
                .orElseThrow(IllegalArgumentException::new);
        }
    }

    public static final String NONE = "none";


    public static final Comparator<DataverseRole> CMP_BY_NAME = new Comparator<DataverseRole>() {

        @Override
        public int compare(DataverseRole o1, DataverseRole o2) {
            int cmp = o1.getName().compareTo(o2.getName());
            if (cmp != 0) {
                return cmp;
            }

            Long o1OwnerId = o1.getOwner() == null ? 0l : o1.getOwner().getId();
            Long o2OwnerId = o2.getOwner() == null ? 0l : o2.getOwner().getId();

            return o1OwnerId.compareTo(o2OwnerId);
        }
    };

    public static Set<Permission> permissionSet(Iterable<DataverseRole> roles) {
        long miniset = 0l;
        for (DataverseRole role : roles) {
            miniset |= role.permissionBits;
        }
        return new BitSet(miniset).asSetOf(Permission.class);
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Pattern(regexp = ".+", message = "{role.name}")
    @Column(nullable = false)
    private String name;

    @Size(max = 255, message = "{role.desc.maxLength}")
    private String description;

    @Size(max = 16, message = "{role.alias.maxLength}")
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
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return RoleTranslationUtil.getLocaleNameFromAlias(alias, name);
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return RoleTranslationUtil.getLocaleDescriptionFromAlias(alias, description);
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public DvObject getOwner() {
        return owner;
    }

    public void setOwner(DvObject owner) {
        this.owner = owner;
    }

    public void addPermissions(Collection<Permission> ps) {
        for (Permission p : ps) {
            addPermission(p);
        }
    }

    public void addPermission(Permission p) {
        permissionBits = new BitSet(permissionBits).set(p.ordinal()).getBits();
    }

    public void clearPermissions() {
        permissionBits = 0l;
    }

    public Set<Permission> permissions() {
        return new BitSet(permissionBits).asSetOf(Permission.class);
    }

    public long getPermissionsBits() {
        return permissionBits;
    }

    @Override
    public String toString() {
        return "DataverseRole{" + "id=" + id + ", alias=" + alias + '}';
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + Objects.hashCode(this.id);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final DataverseRole other = (DataverseRole) obj;
        return Objects.equals(this.id, other.id);
    }

    /**
     * Given a DvObject object, see if this role contains a Permission
     * applicable to that object
     *
     * @param dvObject
     * @return
     */
    public boolean doesDvObjectHavePermissionForObject(DvObject dvObject) {

        if (dvObject == null) {
            return false;
        }

        return this.doesDvObjectClassHavePermissionForObject(dvObject.getClass());

    } // doesDvObjectHavePermissionForObject


    /**
     * Given a DvObject object class, see if this role contains a Permission
     * applicable to that object
     * <p>
     * Initial user is for MyData page and displaying role tags
     *
     * @param dvObjectClass
     * @return
     */
    public boolean doesDvObjectClassHavePermissionForObject(Class<? extends DvObject> dvObjectClass) {

        if (dvObjectClass == null) {
            return false;
        }

        // Iterate through permissions.  If one applies to this class, return true
        //
        for (Permission perm : this.permissions()) {
            if (perm.appliesTo(dvObjectClass)) {
                return true;
            }
        }

        return false;

    } // doesDvObjectClassHavePermissionForObject


}
