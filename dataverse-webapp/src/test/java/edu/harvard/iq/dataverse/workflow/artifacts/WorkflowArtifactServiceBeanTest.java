package edu.harvard.iq.dataverse.workflow.artifacts;

import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.workflow.WorkflowArtifact;
import edu.harvard.iq.dataverse.persistence.workflow.WorkflowArtifactRepository;
import edu.harvard.iq.dataverse.workflow.artifacts.WorkflowArtifactServiceBean.ArtifactData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.enterprise.inject.Vetoed;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class WorkflowArtifactServiceBeanTest {

    private static final String ENCODING = "BINARY";
    private static final String NAME = "TEST";

    private static final Supplier<InputStream> TEST_DATA_SUPPLIER =
            () -> new ByteArrayInputStream(new byte[] { 0, 1, 2, 3 });

    private WorkflowArtifactServiceBean serviceBean;

    private WorkflowArtifactRepository repository;

    private StorageService storageService;


    @BeforeEach
    public void setUp() {
        repository = new Repository();
        serviceBean = new WorkflowArtifactServiceBean(repository);

        storageService = Mockito.spy(new TestStorageService());
        serviceBean.register(storageService);
    }

    @Test
    @DisplayName("Should save artifact data and metadata")
    public void shouldSaveDataAndMetadata() {
        // given
        DatasetVersion version = createDatasetVersion();
        ArtifactData data = new ArtifactData(NAME, ENCODING, TEST_DATA_SUPPLIER);

        // when
        WorkflowArtifact artifact =
                serviceBean.saveArtifact(version, null, data, storageService.getStorageType());

        // then
        assertThat(artifact.getId()).isNotNull();
        assertThat(artifact.getCreatedAt()).isNotNull();
        assertThat(artifact.getLocation()).isNotNull();
        assertThat(artifact.getDatasetVersionId()).isEqualTo(version.getId());
        assertThat(artifact.getStorageType()).isEqualTo(storageService.getStorageType().name());
        assertThat(artifact.getArtifactName()).isEqualTo(NAME);
        assertThat(artifact.getEncoding()).isEqualTo(ENCODING);
        assertThat(artifact.getWorkflowExecutionStepId()).isNull();
        verify(storageService).save(any(WorkflowArtifact.class), eq(TEST_DATA_SUPPLIER));
    }

    @Test
    @DisplayName("Should be able to retrieve stored artifact data")
    public void shouldRetrieveStoredData() throws IOException {
        // given
        DatasetVersion version = createDatasetVersion();
        ArtifactData data = new ArtifactData(NAME, ENCODING, TEST_DATA_SUPPLIER);

        // when
        WorkflowArtifact artifact = serviceBean.saveArtifact(version, null, data);
        Optional<InputStream> stream = serviceBean.readAsStream(artifact);

        // then
        assertThat(stream.isPresent()).isTrue();
        verify(storageService).readAsStream(artifact.getLocation());

        // cleanup
        stream.get().close();
    }

    @Test
    @DisplayName("Should delete stored artifacts for the given dataset version")
    public void shouldDeleteArtifactsForGivenDatasetVersion() {
        // given
        final int size = 10;
        DatasetVersion version = createDatasetVersion();
        ArtifactData data = new ArtifactData(NAME, ENCODING, TEST_DATA_SUPPLIER);

        // when
        IntStream.rangeClosed(1, size).forEach(i -> serviceBean.saveArtifact(version, null, data));
        List<WorkflowArtifact> before = repository.findAllByDatasetVersion(version);
        serviceBean.deleteArtifacts(version);
        List<WorkflowArtifact> after = repository.findAllByDatasetVersion(version);

        // then
        assertThat(before.size()).isEqualTo(size);
        assertThat(after).isEmpty();
        verify(storageService, times(size)).delete(anyString());
    }

    // -------------------- PRIVATE --------------------

    private DatasetVersion createDatasetVersion() {
        DatasetVersion version = new DatasetVersion();
        version.setId(new Random().nextLong());
        return version;
    }

    // -------------------- INNER CLASSES --------------------

    private static class TestStorageService implements StorageService {
        private Map<String, Supplier<InputStream>> storage = new HashMap<>();

        @Override
        public StorageType getStorageType() {
            return StorageType.DATABASE;
        }

        @Override
        public String save(WorkflowArtifact workflowArtifact, Supplier<InputStream> inputStreamSupplier) {
            String location = workflowArtifact.getId().toString();
            storage.put(location, inputStreamSupplier);
            return location;
        }

        @Override
        public Optional<InputStream> readAsStream(String location) {
            Supplier<InputStream> inputStreamSupplier = storage.get(location);
            return inputStreamSupplier != null
                    ? Optional.ofNullable(inputStreamSupplier.get())
                    : Optional.empty();
        }

        @Override
        public void delete(String location) {
            storage.remove(location);
        }
    }

    @Vetoed
    private static class Repository extends WorkflowArtifactRepository {
        private Map<Long, WorkflowArtifact> storage = new HashMap<>();
        long counter = 0L;

        public Repository() {
            super();
        }

        @Override
        public List<WorkflowArtifact> findAllByDatasetVersion(DatasetVersion datasetVersion) {
            return storage.values().stream()
                    .filter(v -> datasetVersion.getId().equals(v.getDatasetVersionId()))
                    .collect(Collectors.toList());
        }

        @Override
        public int deleteAllByDatasetVersion(DatasetVersion datasetVersion) {
            List<WorkflowArtifact> found = findAllByDatasetVersion(datasetVersion);
            storage.values().removeAll(found);
            return found.size();
        }

        @Override
        public WorkflowArtifact save(WorkflowArtifact entity) {
            long id = entity.getId() != null ? entity.getId() : counter++;
            storage.put(id, entity);
            entity.setId(id);
            return entity;
        }

        @Override
        public void delete(WorkflowArtifact entity) {
            storage.remove(entity.getId());
        }
    }
}