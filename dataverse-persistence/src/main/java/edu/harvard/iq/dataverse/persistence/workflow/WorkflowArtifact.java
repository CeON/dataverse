package edu.harvard.iq.dataverse.persistence.workflow;

import edu.harvard.iq.dataverse.persistence.JpaEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "workflowartifact")
public class WorkflowArtifact implements JpaEntity<Long> {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "datasetversion_id")
    private Long datasetVersionId;

    @Column(name = "workflow_execution_step_id")
    private Long workflowExecutionStepId;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "artifact_name")
    private String artifactName;

    /**
     * Allows to store encoding data in case of text artifacts.
     * The string should contain encoding's name that could be recognized by
     * {@link java.nio.charset.Charset}#forName method.
     */
    private String encoding;

    /**
     * The type of storage for the given artifact.
     */
    @Column(name = "storage_type")
    private String storageType;

    /**
     * Location of artifact within the storage.
     */
    private String location;

    // -------------------- GETTERS --------------------

    @Override
    public Long getId() {
        return id;
    }

    public Long getDatasetVersionId() {
        return datasetVersionId;
    }

    public Long getWorkflowExecutionStepId() {
        return workflowExecutionStepId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getArtifactName() {
        return artifactName;
    }

    public String getEncoding() {
        return encoding;
    }

    public String getStorageType() {
        return storageType;
    }

    public String getLocation() {
        return location;
    }

    // -------------------- SETTERS --------------------

    public void setId(Long id) {
        this.id = id;
    }

    public void setDatasetVersionId(Long datasetVersionId) {
        this.datasetVersionId = datasetVersionId;
    }

    public void setWorkflowExecutionStepId(Long workflowExecutionStepId) {
        this.workflowExecutionStepId = workflowExecutionStepId;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public void setArtifactName(String artifactName) {
        this.artifactName = artifactName;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public void setStorageType(String storageType) {
        this.storageType = storageType;
    }

    public void setLocation(String location) {
        this.location = location;
    }
}
