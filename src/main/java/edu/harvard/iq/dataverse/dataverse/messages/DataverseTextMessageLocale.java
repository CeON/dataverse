package edu.harvard.iq.dataverse.dataverse.messages;

import edu.harvard.iq.dataverse.DataverseLocaleBean;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Version;
import java.io.Serializable;
import java.util.logging.Logger;

/**
 *
 * @author tjanek
 */
@Entity
@Table(indexes = {@Index(columnList="dataversetextmessage_id")})
public class DataverseTextMessageLocale implements Serializable {

    private static final Logger logger = Logger.getLogger(DataverseTextMessageLocale.class.getCanonicalName());

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column( nullable=false )
    private String message;

    @Column( nullable=false )
    private String locale = DataverseLocaleBean.DEFAULT_LOCALE;

    @Version
    private Long version;

    @ManyToOne
    private DataverseTextMessage dataverseTextMessage;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public DataverseTextMessage getDataverseTextMessage() {
        return dataverseTextMessage;
    }

    public void setDataverseTextMessage(DataverseTextMessage dataverseTextMessage) {
        this.dataverseTextMessage = dataverseTextMessage;
    }
}
