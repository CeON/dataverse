package edu.harvard.iq.dataverse.persistence.dataverse;

import edu.harvard.iq.dataverse.persistence.JpaEntity;
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
import java.io.Serializable;
import java.util.Objects;

/**
 * @author skraffmiller
 */

@NamedQueries({
        @NamedQuery(name = "DataverseFeaturedDataverse.removeByOwnerId",
                query = "DELETE FROM DataverseFeaturedDataverse f WHERE f.dataverse.id=:ownerId")
})

@Entity
@Table(indexes = {@Index(columnList = "dataverse_id")
        , @Index(columnList = "featureddataverse_id")
        , @Index(columnList = "displayorder")})
public class DataverseFeaturedDataverse implements Serializable, JpaEntity<Long> {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @ManyToOne
    @JoinColumn(name = "dataverse_id")
    private Dataverse dataverse;

    @ManyToOne
    @JoinColumn(name = "featureddataverse_id")
    private Dataverse featuredDataverse;

    private int displayOrder;

    public Dataverse getDataverse() {
        return dataverse;
    }

    public void setDataverse(Dataverse dataverse) {
        this.dataverse = dataverse;
    }

    public Dataverse getFeaturedDataverse() {
        return featuredDataverse;
    }

    public void setFeaturedDataverse(Dataverse featuredDataverse) {
        this.featuredDataverse = featuredDataverse;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash += (this.id != null ? this.id.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof DataverseFeaturedDataverse)) {
            return false;
        }
        DataverseFeaturedDataverse other = (DataverseFeaturedDataverse) object;
        return !(!Objects.equals(this.id, other.id) && (this.id == null || !this.id.equals(other.id)));
    }

    @Override
    public String toString() {
        return "DataverseFeaturedDataverse[ id=" + id + " ]";
    }

}
