package edu.harvard.iq.dataverse;

import com.google.common.collect.Lists;
import edu.harvard.iq.dataverse.engine.command.impl.UpdateDatasetVersionCommand;
import edu.harvard.iq.dataverse.persistence.datafile.FileMetadata;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.validation.ValidationException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DatasetVersionServiceBeanTest {

    @InjectMocks
    private DatasetVersionServiceBean datasetVersionService;

    @Mock
    private EjbDataverseEngine commandEngine;

    @Mock
    private DataverseRequestServiceBean dvRequestService;

    @BeforeEach
    void setUp() {
        when(commandEngine.submit(any(UpdateDatasetVersionCommand.class))).thenReturn(new Dataset());
    }

    @Test
    public void updateDatasetVersion() {
        //given
        DatasetVersion testDatasetVersion = new DatasetVersion();
        testDatasetVersion.setVersionState(DatasetVersion.VersionState.DRAFT);
        Dataset testDataset = new Dataset();
        testDataset.setVersions(Lists.newArrayList(testDatasetVersion));
        testDatasetVersion.setDataset(testDataset);

        //when & then
        Assertions.assertDoesNotThrow(() -> datasetVersionService.updateDatasetVersion(testDatasetVersion, true));

    }

    @Test
    public void updateDatasetVersion_WithValidationException() {
        //given
        DatasetVersion testDatasetVersion = new DatasetVersion();
        testDatasetVersion.setVersionState(DatasetVersion.VersionState.DRAFT);
        Dataset testDataset = new Dataset();
        testDataset.setId(1L);
        testDataset.setVersions(Lists.newArrayList(testDatasetVersion));
        testDatasetVersion.setDataset(testDataset);

        FileMetadata fileToDelete = new FileMetadata();
        fileToDelete.setLabel("");
        fileToDelete.setDatasetVersion(testDatasetVersion);
        fileToDelete.getDatasetVersion().setFileMetadatas(Lists.newArrayList(fileToDelete));

        //when & then
        Assertions.assertThrows(ValidationException.class, () -> datasetVersionService.updateDatasetVersion(testDatasetVersion, true));

    }
}