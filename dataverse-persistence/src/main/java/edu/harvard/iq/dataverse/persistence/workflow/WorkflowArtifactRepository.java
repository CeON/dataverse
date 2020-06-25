package edu.harvard.iq.dataverse.persistence.workflow;

import edu.harvard.iq.dataverse.persistence.JpaRepository;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;

import javax.ejb.Singleton;
import java.util.List;

@Singleton
public class WorkflowArtifactRepository extends JpaRepository<Long, WorkflowArtifact> {

    // -------------------- CONSTRUCTORS --------------------

    public WorkflowArtifactRepository() {
        super(WorkflowArtifact.class);
    }

    // -------------------- LOGIC --------------------

    public List<WorkflowArtifact> findAllByDatasetVersion(DatasetVersion datasetVersion) {
        Long datasetVersionId = datasetVersion.getId();
        return em.createQuery(
                "SELECT a FROM WorkflowArtifacts a WHERE a.datasetVersionId = :datasetVersionId",
                WorkflowArtifact.class)
                .setParameter("datasetVersionId", datasetVersionId)
                .getResultList();
    }

    public int deleteAllByDatasetVersion(DatasetVersion datasetVersion) {
        Long datasetVersionId = datasetVersion.getId();
        return em.createQuery("DELETE a FROM WorkflowArtifacts a WHERE a.datasetVersionId = :datasetVersionId")
                .setParameter("datasetVersionId", datasetVersionId)
                .executeUpdate();
    }
}
