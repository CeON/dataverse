package edu.harvard.iq.dataverse.persistence.dataset;

import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;

import javax.persistence.Column;
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
import java.sql.Timestamp;

/**
 * Records the last time a {@link User} handled a {@link DatasetVersion}.
 *
 * @author skraffmiller
 */
@Entity
@Table(indexes = {@Index(columnList = "authenticateduser_id"), @Index(columnList = "datasetversion_id")})
@NamedQueries({
        @NamedQuery(
                name = "DatasetVersionUser.findByVersionIdAndUserId",
                query = "select dvu from DatasetVersionUser dvu where dvu.datasetVersion.id =:versionId and dvu.authenticatedUser.id =:userId"
        )
})
public class DatasetVersionUser implements Serializable {

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
    @JoinColumn(name = "authenticatedUser_id")
    private AuthenticatedUser authenticatedUser;

    public AuthenticatedUser getAuthenticatedUser() {
        return authenticatedUser;
    }

    public void setAuthenticatedUser(AuthenticatedUser authenticatedUser) {
        this.authenticatedUser = authenticatedUser;
    }

    @ManyToOne
    @JoinColumn(name = "datasetversion_id")
    private DatasetVersion datasetVersion;

    @Column(nullable = false)
    private Timestamp lastUpdateDate;


    public Timestamp getLastUpdateDate() {
        return lastUpdateDate;
    }

    public void setLastUpdateDate(Timestamp lastUpdateDate) {
        this.lastUpdateDate = lastUpdateDate;
    }

    public DatasetVersion getDatasetVersion() {
        return datasetVersion;
    }

    public void setDatasetVersion(DatasetVersion datasetVersion) {
        this.datasetVersion = datasetVersion;
    }


}
