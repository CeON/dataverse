package edu.harvard.iq.dataverse.dataverse.banners;

import edu.harvard.iq.dataverse.Dataverse;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.Transient;
import java.util.Date;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
public class DataverseBanner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date fromTime;

    @Column(nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date toTime;

    private boolean active;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "dataverseBanner")
    private Set<DataverseLocalizedBanner> dataverseLocalizedBanner = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    private Dataverse dataverse;

    @Transient
    private UUID uuid = UUID.randomUUID();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Date getFromTime() {
        return fromTime;
    }

    public void setFromTime(Date fromTime) {
        this.fromTime = fromTime;
    }

    public Date getToTime() {
        return toTime;
    }

    public void setToTime(Date toTime) {
        this.toTime = toTime;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Set<DataverseLocalizedBanner> getDataverseLocalizedBanner() {
        return dataverseLocalizedBanner;
    }

    public void setDataverseLocalizedBanner(Set<DataverseLocalizedBanner> dataverseLocalizedBanner) {
        this.dataverseLocalizedBanner = dataverseLocalizedBanner;
    }

    public Dataverse getDataverse() {
        return dataverse;
    }

    public void setDataverse(Dataverse dataverse) {
        this.dataverse = dataverse;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataverseBanner that = (DataverseBanner) o;
        return active == that.active &&
                Objects.equals(id, that.id) &&
                Objects.equals(fromTime, that.fromTime) &&
                Objects.equals(toTime, that.toTime) &&
                Objects.equals(dataverseLocalizedBanner, that.dataverseLocalizedBanner) &&
                Objects.equals(dataverse, that.dataverse) &&
                Objects.equals(uuid, that.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, fromTime, toTime, active, dataverseLocalizedBanner, dataverse, uuid);
    }
}
