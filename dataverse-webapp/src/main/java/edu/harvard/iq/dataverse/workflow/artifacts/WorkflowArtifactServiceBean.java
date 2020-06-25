package edu.harvard.iq.dataverse.workflow.artifacts;

import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.workflow.WorkflowArtifact;
import edu.harvard.iq.dataverse.persistence.workflow.WorkflowArtifactRepository;
import edu.harvard.iq.dataverse.persistence.workflow.WorkflowExecutionStep;

import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;
import java.io.InputStream;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

@Startup
@Singleton
public class WorkflowArtifactServiceBean {
    private Map<StorageType, StorageService> services = new HashMap<>();

    private WorkflowArtifactRepository repository;

    // -------------------- CONSTRUCTORS --------------------

    public WorkflowArtifactServiceBean() { }

    @Inject
    public WorkflowArtifactServiceBean(WorkflowArtifactRepository repository) {
        this.repository = repository;
    }

    // -------------------- LOGIC --------------------

    /**
     * Registers service based on service's {@link StorageType}. If there is already a registered service supporting
     * this type of storage, it would be unregistered.
     * @param service service to be registered
     */
    public void register(StorageService service) {
        StorageType storageType = Objects.requireNonNull(service.getStorageType());
        services.put(storageType, service);
    }

    /**
     * Same as {@link WorkflowArtifactServiceBean#saveArtifact(DatasetVersion, WorkflowExecutionStep, ArtifactData, StorageType)}
     * but with the last parameter set to {@link StorageType#DATABASE}.
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public WorkflowArtifact saveArtifact(DatasetVersion version, WorkflowExecutionStep executionStep, ArtifactData data) {
        return saveArtifact(version, executionStep, data, StorageType.DATABASE);
    }

    /**
     * Saves artifact into storage of selected type. The {@link WorkflowExecutionStep} parameter is optional (ie. could
     * be null), but recommended to set.
     * Please note that in case of text streams proper encoding is up to caller.
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public WorkflowArtifact saveArtifact(DatasetVersion version, WorkflowExecutionStep executionStep, ArtifactData data, StorageType storageType) {
        StorageService service = services.get(storageType);
        WorkflowArtifact workflowArtifact = new WorkflowArtifact();
        workflowArtifact.setDatasetVersionId(version.getId());
        Optional.ofNullable(executionStep)
                .map(WorkflowExecutionStep::getId)
                .ifPresent(workflowArtifact::setWorkflowExecutionStepId);
        workflowArtifact.setEncoding(data.getEncoding());
        workflowArtifact.setArtifactName(data.getName());
        workflowArtifact.setStorageType(storageType.name());
        workflowArtifact = repository.save(workflowArtifact);

        String location = service.save(workflowArtifact, data.inputStreamSupplier);

        workflowArtifact.setLocation(location);
        workflowArtifact.setCreatedAt(Instant.now());
        return repository.save(workflowArtifact);
    }

    /**
     * Returns {@link Optional} containing {@link InputStream} of stored data for the given
     * {@link WorkflowArtifact} or empty {@link Optional} if value was not found.
     */
    public Optional<InputStream> readAsStream(WorkflowArtifact artifact) {
        return selectProperService(artifact)
                .readAsStream(artifact.getLocation());
    }

    /**
     * Deletes all stored artifacts for the given {@link DatasetVersion} object. This means that
     * data would be deleted not only from the <i>workflowartifact</i> table but also from
     * the appropriate storage.
     */
    public void deleteArtifacts(DatasetVersion version) {
        List<WorkflowArtifact> oldArtifacts = repository.findAllByDatasetVersion(version);
        oldArtifacts.forEach(this::deleteFromStorage);
        repository.deleteAllByDatasetVersion(version);
    }

    /**
     * Deletes the given {@link WorkflowArtifact} from <i>workflowartifact</i> table
     * and from the storage.
     */
    public void deleteArtifact(WorkflowArtifact artifact) {
        deleteFromStorage(artifact);
        repository.delete(artifact);
    }

    // -------------------- PRIVATE --------------------

    private void deleteFromStorage(WorkflowArtifact artifact) {
        selectProperService(artifact)
                .delete(artifact.getLocation());
    }

    private StorageService selectProperService(WorkflowArtifact artifact) {
        StorageType storageType = StorageType.valueOf(artifact.getStorageType());
        return services.get(storageType);
    }

    // -------------------- INNER CLASSES --------------------

    public static class ArtifactData {
        private String name;
        private String encoding;

        private Supplier<InputStream> inputStreamSupplier;

        public ArtifactData(String name, String encoding, Supplier<InputStream> inputStreamSupplier) {
            this.name = name;
            this.encoding = encoding;
            this.inputStreamSupplier = inputStreamSupplier;
        }

        public String getName() {
            return name;
        }

        public String getEncoding() {
            return encoding;
        }

        public Supplier<InputStream> getInputStreamSupplier() {
            return inputStreamSupplier;
        }
    }
}
