/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.harvard.iq.dataverse.persistence.guestbook;

import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author skraffmiller
 */
@Entity
@Table(indexes = {
        @Index(columnList = "guestbook_id"),
        @Index(columnList = "datafile_id"),
        @Index(columnList = "dataset_id")
})
@NamedQueries(
        @NamedQuery(name = "GuestbookResponse.findByAuthenticatedUserId",
                query = "SELECT gbr FROM GuestbookResponse gbr WHERE gbr.authenticatedUser.id=:authenticatedUserId")
)
public class GuestbookResponse implements Serializable {
    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(nullable = false)
    private Guestbook guestbook;

    @ManyToOne
    @JoinColumn(nullable = false)
    private DataFile dataFile;

    @ManyToOne
    @JoinColumn(nullable = false)
    private Dataset dataset;

    @ManyToOne
    @JoinColumn(nullable = true)
    private DatasetVersion datasetVersion;

    @ManyToOne
    @JoinColumn(nullable = true)
    private AuthenticatedUser authenticatedUser;

    @OneToMany(mappedBy = "guestbookResponse", cascade = {CascadeType.REMOVE, CascadeType.MERGE, CascadeType.PERSIST}, orphanRemoval = true)
    @OrderBy("id")
    private List<CustomQuestionResponse> customQuestionResponses;


    private String name;
    private String email;
    private String institution;
    private String position;
    /**
     * Possible values for downloadType include "Download", "Subset",
     * "WorldMap", or the displayName of an ExternalTool.
     * <p>
     * TODO: Types like "Download" and "Subset" and probably "WorldMap" should
     * be defined once as constants (likely an enum) rather than having these
     * strings duplicated in various places when setDownloadtype() is called.
     * (Some day it would be nice to convert WorldMap into an ExternalTool but
     * it's not worth the effort at this time.)
     */
    private String downloadtype;
    private String sessionId;

    @Temporal(value = TemporalType.TIMESTAMP)
    private Date responseTime;


    public GuestbookResponse() {

    }

    public GuestbookResponse(GuestbookResponse source) {
        //makes a clone of a response for adding of studyfiles in case of multiple downloads
        this.setName(source.getName());
        this.setEmail(source.getEmail());
        this.setInstitution(source.getInstitution());
        this.setPosition(source.getPosition());
        this.setResponseTime(source.getResponseTime());
        this.setDataset(source.getDataset());
        this.setDatasetVersion(source.getDatasetVersion());
        this.setAuthenticatedUser(source.getAuthenticatedUser());
        this.setSessionId(source.getSessionId());
        List<CustomQuestionResponse> customQuestionResponses = new ArrayList<>();
        if (!source.getCustomQuestionResponses().isEmpty()) {
            for (CustomQuestionResponse customQuestionResponse : source.getCustomQuestionResponses()) {
                CustomQuestionResponse customQuestionResponseAdd = new CustomQuestionResponse();
                customQuestionResponseAdd.setResponse(customQuestionResponse.getResponse());
                customQuestionResponseAdd.setCustomQuestion(customQuestionResponse.getCustomQuestion());
                customQuestionResponseAdd.setGuestbookResponse(this);
                customQuestionResponses.add(customQuestionResponseAdd);
            }
        }
        this.setCustomQuestionResponses(customQuestionResponses);
        this.setGuestbook(source.getGuestbook());
    }


    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Guestbook getGuestbook() {
        return guestbook;
    }

    public void setGuestbook(Guestbook guestbook) {
        this.guestbook = guestbook;
    }

    public String getInstitution() {
        return institution;
    }

    public void setInstitution(String institution) {
        this.institution = institution;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Date getResponseTime() {
        return responseTime;
    }

    public void setResponseTime(Date responseTime) {
        this.responseTime = responseTime;
    }

    public String getResponseDate() {
        return new SimpleDateFormat("MMMM d, yyyy").format(responseTime);
    }

    public String getResponseDateForDisplay() {
        return null; //    SimpleDateFormat("yyyy").format(new Timestamp(new Date().getTime()));
    }


    public List<CustomQuestionResponse> getCustomQuestionResponses() {
        return customQuestionResponses;
    }

    public void setCustomQuestionResponses(List<CustomQuestionResponse> customQuestionResponses) {
        this.customQuestionResponses = customQuestionResponses;
    }

    public Dataset getDataset() {
        return dataset;
    }

    public void setDataset(Dataset dataset) {
        this.dataset = dataset;
    }

    public DataFile getDataFile() {
        return dataFile;
    }

    public void setDataFile(DataFile dataFile) {
        this.dataFile = dataFile;
    }

    public DatasetVersion getDatasetVersion() {
        return datasetVersion;
    }

    public void setDatasetVersion(DatasetVersion datasetVersion) {
        this.datasetVersion = datasetVersion;
    }

    public AuthenticatedUser getAuthenticatedUser() {
        return authenticatedUser;
    }

    public void setAuthenticatedUser(AuthenticatedUser authenticatedUser) {
        this.authenticatedUser = authenticatedUser;
    }

    public String getDownloadtype() {
        return downloadtype;
    }

    public void setDownloadtype(String downloadtype) {
        this.downloadtype = downloadtype;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash += (id != null ? id.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object object) {
        // TODO: Warning - this method won't work in the case the id fields are not set
        if (!(object instanceof GuestbookResponse)) {
            return false;
        }
        GuestbookResponse other = (GuestbookResponse) object;
        return (this.id != null || other.id == null) && (this.id == null || this.id.equals(other.id));
    }

    @Override
    public String toString() {
        return "GuestbookResponse [id=" + id + ", guestbook=" + guestbook + ", dataFile=" + dataFile + ", dataset="
                + dataset + ", datasetVersion=" + datasetVersion + ", authenticatedUser=" + authenticatedUser
                + ", customQuestionResponses=" + customQuestionResponses + ", name=" + name + ", email=" + email
                + ", institution=" + institution + ", position=" + position + ", downloadtype=" + downloadtype
                + ", sessionId=" + sessionId + "]";
    }

}

