package edu.harvard.iq.dataverse.persistence.user;

import static javax.persistence.CascadeType.MERGE;
import static javax.persistence.GenerationType.IDENTITY;

import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import edu.harvard.iq.dataverse.persistence.DvObject;
import edu.harvard.iq.dataverse.persistence.JpaEntity;

/**
 * A role of a user in a Dataverse. A User may have many roles in a given Dataverse.
 * This is a realization of a Many-to-Many relationship
 * between users and dataverses, with roles as an extra column.
 *
 * @author michael
 */
@SuppressWarnings("serial")
@Entity
@Table(
        uniqueConstraints = @UniqueConstraint(columnNames = {"assigneeIdentifier", "role_id", "definitionPoint_id"})
        , indexes = {@Index(columnList = "assigneeidentifier")
        , @Index(columnList = "definitionpoint_id")
        , @Index(columnList = "role_id")}
)
@NamedQueries({
        @NamedQuery(name = "RoleAssignment.listByAssigneeIdentifier_DefinitionPointId_RoleId",
                query = "SELECT r FROM RoleAssignment r WHERE r.assigneeIdentifier=:assigneeIdentifier AND r.definitionPoint.id=:definitionPointId and r.role.id=:roleId"),
        @NamedQuery(name = "RoleAssignment.deleteAllByAssigneeIdentifier_Definition_PointId_RoleType",
                query = "DELETE FROM RoleAssignment r WHERE r.assigneeIdentifier=:assigneeIdentifier AND r.role.id=:roleId and r.definitionPoint.id=:definitionPointId")
})
public class RoleAssignment implements java.io.Serializable, JpaEntity<Long> {
    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String assigneeIdentifier;

    @ManyToOne(cascade = {MERGE})
    @JoinColumn(nullable = false)
    private DataverseRole role;

    @ManyToOne(cascade = {MERGE})
    @JoinColumn(nullable = false)
    private DvObject definitionPoint;

    @Column(nullable = true)
    private String privateUrlToken;
    
    private boolean anonymized;

    public RoleAssignment() {
    }

    public RoleAssignment(DataverseRole aRole, RoleAssignee anAssignee,
            DvObject aDefinitionPoint, String privateUrlToken) {
        this(aRole, anAssignee, aDefinitionPoint, privateUrlToken, false);
    }
    
    public RoleAssignment(DataverseRole aRole, RoleAssignee anAssignee,
            DvObject aDefinitionPoint, String privateUrlToken, boolean anonymized) {
        this.role = aRole;
        this.assigneeIdentifier = anAssignee.getIdentifier();
        this.definitionPoint = aDefinitionPoint;
        this.privateUrlToken = privateUrlToken;
        this.anonymized = anonymized;
    }

    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAssigneeIdentifier() {
        return this.assigneeIdentifier;
    }

    public void setAssigneeIdentifier(String assigneeIdentifier) {
        this.assigneeIdentifier = assigneeIdentifier;
    }

    public DataverseRole getRole() {
        return this.role;
    }

    public void setRole(DataverseRole role) {
        this.role = role;
    }

    public DvObject getDefinitionPoint() {
        return this.definitionPoint;
    }

    public void setDefinitionPoint(DvObject definitionPoint) {
        this.definitionPoint = definitionPoint;
    }

    public String getPrivateUrlToken() {
        return this.privateUrlToken;
    }

    public boolean isAnonymized() {
        return this.anonymized;
    }

    public void setAnonymized(final boolean anonymized) {
        this.anonymized = anonymized;
    }
    
    public boolean has(final Permission p) {
    	return this.role.has(p);
    }
    
    public boolean hasAny(final Permission p1, final Permission p2) {
    	return this.role.hasAny(p1, p2);
    }

    @Override
    public int hashCode() {
        int hash =  Boolean.hashCode(this.anonymized);
        hash = 97 * hash + Objects.hashCode(this.role);
        hash = 97 * hash + Objects.hashCode(this.assigneeIdentifier);
        return hash;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj instanceof RoleAssignment) {
            final RoleAssignment other = (RoleAssignment) obj;

            return Objects.equals(getRole(), other.getRole())
                    && Objects.equals(getAssigneeIdentifier(),
                            other.getAssigneeIdentifier())
                    && Objects.equals(getDefinitionPoint(),
                            other.getDefinitionPoint())
                    && this.anonymized == other.anonymized;
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return "RoleAssignment{" 
                + "id=" + id 
                + ", assignee=" + assigneeIdentifier
                + ", role=" + role + ", definitionPoint=" + definitionPoint
                + ", anonymized=" + this.anonymized + '}';
    }

}
