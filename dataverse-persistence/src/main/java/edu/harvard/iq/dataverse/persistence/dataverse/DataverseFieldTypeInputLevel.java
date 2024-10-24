package edu.harvard.iq.dataverse.persistence.dataverse;

import edu.harvard.iq.dataverse.persistence.dataset.DatasetFieldType;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import java.io.Serializable;

/**
 * When dataverse is created/edited, the user can customize which fields are optional/required
 * and that data is stored here.
 *
 * @author skraffmiller
 */
@NamedQueries({
        @NamedQuery(name = "DataverseFieldTypeInputLevel.removeByOwnerId",
                query = "DELETE FROM DataverseFieldTypeInputLevel f WHERE f.dataverse.id=:ownerId"),
        @NamedQuery(name = "DataverseFieldTypeInputLevel.findByDataverseId",
                query = "select f from DataverseFieldTypeInputLevel f where f.dataverse.id = :dataverseId"),
        @NamedQuery(name = "DataverseFieldTypeInputLevel.findByDataverseIdDatasetFieldTypeId",
                query = "select f from DataverseFieldTypeInputLevel f where f.dataverse.id = :dataverseId and f.datasetFieldType.id = :datasetFieldTypeId"),
        @NamedQuery(name = "DataverseFieldTypeInputLevel.findByDataverseIdAndDatasetFieldTypeIdList",
                query = "select f from DataverseFieldTypeInputLevel f where f.dataverse.id = :dataverseId and f.datasetFieldType.id in :datasetFieldIdList")

})
@Table(name = "DataverseFieldTypeInputLevel"
        , uniqueConstraints = {
        @UniqueConstraint(columnNames = {"dataverse_id", "datasetfieldtype_id"})}
        , indexes = {@Index(columnList = "dataverse_id")
        , @Index(columnList = "datasetfieldtype_id")
        , @Index(columnList = "required")}
)
@Entity
public class DataverseFieldTypeInputLevel implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "dataverse_id")
    private Dataverse dataverse;

    @ManyToOne
    @JoinColumn(name = "datasetfieldtype_id")
    private DatasetFieldType datasetFieldType;
    private boolean include;
    private boolean required;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash += (id != null ? id.hashCode() : 0);
        return hash;
    }

    public Dataverse getDataverse() {
        return dataverse;
    }

    public void setDataverse(Dataverse dataverse) {
        this.dataverse = dataverse;
    }

    public DatasetFieldType getDatasetFieldType() {
        return datasetFieldType;
    }

    public void setDatasetFieldType(DatasetFieldType datasetFieldType) {
        this.datasetFieldType = datasetFieldType;
    }

    public boolean isInclude() {
        return include;
    }

    public void setInclude(boolean include) {
        this.include = include;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    @Override
    public boolean equals(Object object) {
        // TODO: Warning - this method won't work in the case the id fields are not set
        if (!(object instanceof DataverseFieldTypeInputLevel)) {
            return false;
        }
        DataverseFieldTypeInputLevel other = (DataverseFieldTypeInputLevel) object;
        return (this.id != null || other.id == null) && (this.id == null || this.id.equals(other.id));
    }

    @Override
    public String toString() {
        return "DataverseFieldTypeInputLevel[ id=" + id + " ]";
    }

}
