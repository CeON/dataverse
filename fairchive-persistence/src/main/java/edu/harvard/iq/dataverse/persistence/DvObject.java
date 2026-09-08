package edu.harvard.iq.dataverse.persistence;

import static javax.persistence.CascadeType.ALL;
import static javax.persistence.CascadeType.MERGE;
import static javax.persistence.CascadeType.PERSIST;
import static javax.persistence.CascadeType.REMOVE;
import static javax.persistence.GenerationType.IDENTITY;
import static javax.persistence.InheritanceType.JOINED;
import static javax.persistence.TemporalType.TIMESTAMP;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.SPACE;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import java.io.Serializable;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.DiscriminatorColumn;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Inheritance;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.UniqueConstraint;

import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.persistence.user.RoleAssignment;

@SuppressWarnings("serial")
@Entity
// Inheritance strategy "JOINED" will create 4 db tables - 
// the top-level dvobject, with the common columns, and the 3 child classes - 
// dataverse, dataset and datafile. The ids from the main table will be reused
// in the child tables. (i.e., the id sequences will be "sparse" in the 3 
// child tables). Tested, appears to be working properly. -- L.A. Nov. 4 2014
@Inheritance(strategy = JOINED)
@DiscriminatorColumn(name = "dtype")
@Table(indexes = {@Index(columnList = "dtype")
        , @Index(columnList = "owner_id")
        , @Index(columnList = "creator_id")
        , @Index(columnList = "releaseuser_id")},
        uniqueConstraints = @UniqueConstraint(columnNames = {"authority","protocol","identifier"}))
public abstract class DvObject extends DataverseEntity implements Serializable, JpaEntity<Long> {

    public static final String DATAVERSE_DTYPE_STRING = "Dataverse";
    public static final String DATASET_DTYPE_STRING = "Dataset";
    public static final String DATAFILE_DTYPE_STRING = "DataFile";
    public static final List<String> DTYPE_LIST = Arrays.asList(DATAVERSE_DTYPE_STRING, DATASET_DTYPE_STRING, DATAFILE_DTYPE_STRING);

    public static final Visitor<String> NamePrinter = new Visitor<String>() {

        @Override
        public String visit(Dataverse dv) {
            return dv.getName();
        }

        @Override
        public String visit(Dataset ds) {
            return ds.getLatestVersion().getParsedTitle();
        }

        @Override
        public String visit(DataFile df) {
            return df.getFileMetadata().getLabel();
        }
    };
    public static final Visitor<String> NameIdPrinter = new Visitor<String>() {

        @Override
        public String visit(Dataverse dv) {
            return "[" + dv.getId() + SPACE + dv.getName() + ']';
        }

        @Override
        public String visit(Dataset ds) {
            return "[" + ds.getId() + (ds.getLatestVersion() != null ? SPACE + ds.getLatestVersion().getParsedTitle() : EMPTY) + ']';
        }

        @Override
        public String visit(DataFile df) {
            return "[" + df.getId() + (df.getFileMetadata() != null ? SPACE + df.getFileMetadata().getLabel() : EMPTY) + ']';
        }
    };

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @ManyToOne
    private DvObject owner;

    private Timestamp publicationDate;

    /**
     * The user that released this dataverse
     */
    @ManyToOne
    private AuthenticatedUser releaseUser;

    @Column(nullable = false)
    private Timestamp createDate;

    @Column(nullable = false)
    private Timestamp modificationTime;

    /**
     * @todo Rename this to contentIndexTime (or something) to differentiate it
     * from permissionIndexTime. Content Solr docs vs. permission Solr docs.
     */
    private Timestamp indexTime;

    @Column(nullable = true)
    private Timestamp permissionModificationTime;

    private Timestamp permissionIndexTime;

    @Column
    private String storageIdentifier;

    @Column(insertable = false, updatable = false)
    private String dtype;

    /*
     * Add DOI related fields
     */

    private String protocol;
    private String authority;

    @Temporal(value = TIMESTAMP)
    private Date globalIdCreateTime;

    private String identifier;

    private boolean identifierRegistered;

    @OneToMany(mappedBy = "dvObject", cascade = ALL, orphanRemoval = true)
    private Set<AlternativePersistentIdentifier> alternativePersistentIndentifiers;
    
    @OneToMany(mappedBy = "definitionPoint", cascade = {REMOVE, MERGE, PERSIST}, orphanRemoval = true)
    private List<RoleAssignment> roleAssignments;

    public Set<AlternativePersistentIdentifier> getAlternativePersistentIndentifiers() {
        return alternativePersistentIndentifiers;
    }

    public void setAlternativePersistentIndentifiers(Set<AlternativePersistentIdentifier> alternativePersistentIndentifiers) {
        this.alternativePersistentIndentifiers = alternativePersistentIndentifiers;
    }


    /**
     * previewImageAvailable could also be thought of as "thumbnail has been
     * generated. However, were all three thumbnails generated? We might need a
     * boolean per thumbnail size.
     */
    private boolean previewImageAvailable;

    public boolean isPreviewImageAvailable() {
        return previewImageAvailable;
    }

    public void setPreviewImageAvailable(boolean status) {
        this.previewImageAvailable = status;
    }

    public Timestamp getModificationTime() {
        return modificationTime;
    }

    /**
     * modificationTime is used for comparison with indexTime so we know if the
     * Solr index is stale.
     *
     * @param modificationTime
     */
    public void setModificationTime(Timestamp modificationTime) {
        this.modificationTime = modificationTime;
    }

    public Timestamp getIndexTime() {
        return indexTime;
    }
    
    public boolean isIndexed() {
        
        return this.indexTime != null;
    }

    /**
     * indexTime is used for comparison with modificationTime so we know if the
     * Solr index is stale.
     *
     * @param indexTime
     */
    public void setIndexTime(Timestamp indexTime) {
        this.indexTime = indexTime;
    }

    @ManyToOne
    private AuthenticatedUser creator;

    public interface Visitor<T> {
        T visit(Dataverse dv);

        T visit(Dataset ds);

        T visit(DataFile df);
    }

    /**
     * Sets the owner of the object. This is {@code protected} rather than
     * {@code public}, since different sub-classes have different possible owner
     * types: a {@link DataFile} can only have a {@link Dataset}, for example.
     *
     * @param newOwner
     */
    protected void setOwner(DvObjectContainer newOwner) {
        owner = newOwner;
    }

    public DvObjectContainer getOwner() {
        return (DvObjectContainer) owner;
    }
    
    public DvObject getPermissionRoot() {
    	return this;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    /**
     * @return Whether {@code this} takes no permissions from roles assigned on its parents.
     */
    public abstract boolean isEffectivelyPermissionRoot();

    public Timestamp getPublicationDate() {
        return publicationDate;
    }

    public void setPublicationDate(Timestamp publicationDate) {
        this.publicationDate = publicationDate;
    }

    public AuthenticatedUser getReleaseUser() {
        return releaseUser;
    }

    public void setReleaseUser(AuthenticatedUser releaseUser) {
        this.releaseUser = releaseUser;
    }

    public boolean isReleased() {
        return publicationDate != null;
    }

    public Timestamp getCreateDate() {
        return createDate;
    }

    public void setCreateDate(Timestamp createDate) {
        this.createDate = createDate;
    }

    public AuthenticatedUser getCreator() {
        return creator;
    }

    public void setCreator(AuthenticatedUser creator) {
        this.creator = creator;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getAuthority() {
        return authority;
    }

    public void setAuthority(String authority) {
        this.authority = authority;
    }

    public Date getGlobalIdCreateTime() {
        return globalIdCreateTime;
    }

    public void setGlobalIdCreateTime(Date globalIdCreateTime) {
        this.globalIdCreateTime = globalIdCreateTime;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public boolean isIdentifierRegistered() {
        return identifierRegistered;
    }
    
    public boolean isStale() {
        return isIndexed()
            ? this.indexTime.before(this.modificationTime)
            : true;
    }
    
    public boolean isRoot() {
        return this.getOwner() == null;
    }
    
    public boolean isNotRoot() {
        return this.getOwner() != null;
    }
    
    public boolean canBeReleased() {
    	return !isReleased() && (isRoot() || getOwner().isReleased());
    }

    public void setIdentifierRegistered(boolean identifierRegistered) {
        this.identifierRegistered = identifierRegistered;
    }

    /**
     * @return This object's global id in a string form.
     * @deprecated use {@code dvobj.getGlobalId().asString()}.
     */
    public String getGlobalIdString() {
        final GlobalId globalId = getGlobalId();
        return globalId != null ? globalId.asString() : null;
    }

    public void setGlobalId(GlobalId pid) {
        if (pid == null) {
            setProtocol(null);
            setAuthority(null);
            setIdentifier(null);
        } else {
            setProtocol(pid.getProtocol());
            setAuthority(pid.getAuthority());
            setIdentifier(pid.getIdentifier());
        }
    }

    public GlobalId getGlobalId() {
        // FIXME should return NULL when the fields are null. Currenntly, 
        //       a lot of code depends call this method, so this fix can't be 
        //       a part of the current PR.
        return new GlobalId(getProtocol(), getAuthority(), getIdentifier());
    }

    public abstract <T> T accept(Visitor<T> v);

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }

    @Override
    public abstract boolean equals(Object o);

    @Override
    public String toString() {
        String[] classNameComps = getClass().getName().split("\\.");
        return String.format("[%s id:%d %s]", classNameComps[classNameComps.length - 1],
                             getId(), toStringExtras());
    }

    /**
     * Convenience method to add data to the default toString output.
     *
     * @return
     */
    protected String toStringExtras() {
        return "";
    }

    public abstract String getDisplayName();

    // helper method used to mimic instanceof on JSF pge
    public boolean isInstanceofDataverse() {
        return this instanceof Dataverse;
    }

    public boolean isInstanceofDataset() {
        return this instanceof Dataset;
    }

    public boolean isInstanceofDataFile() {
        return this instanceof DataFile;
    }

    public Timestamp getPermissionModificationTime() {
        return permissionModificationTime;
    }

    public void setPermissionModificationTime(Timestamp permissionModificationTime) {
        this.permissionModificationTime = permissionModificationTime;
    }

    public Timestamp getPermissionIndexTime() {
        return permissionIndexTime;
    }

    public void setPermissionIndexTime(Timestamp permissionIndexTime) {
        this.permissionIndexTime = permissionIndexTime;
    }

    public Dataverse getDataverseContext() {
        return getOwner().getDataverseContext();
    }

    public abstract String getAuthorString();

    public abstract String getTargetUrl();

    public String getYearPublishedCreated() {
        return new SimpleDateFormat("yyyy").format(getPublishedCreated());
    }
    
    public Date getPublishedCreated() {
        //if published get the year if draft get when created
        if (this.isReleased()) {
            return getPublicationDate();
        } else if (this.getCreateDate() != null) {
            return this.getCreateDate();
        } else {
            return new Date();
        }
    }
    
    
    public String getStorageIdentifier() {
        return storageIdentifier;
    }
    
    protected boolean isStored() {
    	return isNotEmpty(this.storageIdentifier);
    }

    public void setStorageIdentifier(String storageIdentifier) {
        this.storageIdentifier = storageIdentifier;
    }

    /**
     * @param other
     * @return {@code true} iff {@code other} is {@code this} or below {@code this} in the containment hierarchy.
     */
    public abstract boolean isAncestorOf(DvObject other);
    
    public List<RoleAssignment> getRoleAssignments() {
        return roleAssignments;
    }

    public void setRoleAssignments(List<RoleAssignment> roleAssignments) {
        this.roleAssignments = roleAssignments;
    }
}
