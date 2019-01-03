package edu.harvard.iq.dataverse.dataverse.messages;

import edu.harvard.iq.dataverse.Dataverse;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.Version;
import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

/**
 *
 * @author tjanek
 */
@Entity
@Table(indexes = {@Index(columnList="dataverse_id")})
public class DataverseTextMessage implements Serializable {

    private static final Logger logger = Logger.getLogger(DataverseTextMessage.class.getCanonicalName());

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private boolean active;

    @Temporal(value = TemporalType.TIMESTAMP)
    @Column( nullable=false )
    private Date fromTime;

    @Temporal(value = TemporalType.TIMESTAMP)
    @Column( nullable=false )
    private Date toTime;

    @Version
    private Long version;

    @ManyToOne
    private Dataverse dataverse;

    @OneToMany(mappedBy = "dataverseTextMessage", cascade = CascadeType.ALL)
    private List<DataverseTextMessageLocale> locales;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
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

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Dataverse getDataverse() {
        return dataverse;
    }

    public void setDataverse(Dataverse dataverse) {
        this.dataverse = dataverse;
    }

    public List<DataverseTextMessageLocale> getLocales() {
        return locales;
    }

    public void setLocales(List<DataverseTextMessageLocale> locales) {
        this.locales = locales;
    }
}
