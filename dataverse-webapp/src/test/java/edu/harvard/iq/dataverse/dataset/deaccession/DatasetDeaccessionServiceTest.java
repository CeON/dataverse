package edu.harvard.iq.dataverse.dataset.deaccession;

import com.google.common.collect.Lists;
import edu.harvard.iq.dataverse.dataset.datasetversion.DatasetVersionServiceBean;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class DatasetDeaccessionServiceTest {

    @InjectMocks
    private DatasetDeaccessionService deaccesssionService;

    @Mock
    private DatasetVersionServiceBean datasetVersionService;

    @Mock
    private DatasetDeaccessionBean datasetDeaccessionBean;

    private Dataset dataset = new Dataset();

    @BeforeEach
    public void setUp() {
        dataset.setId(1L);

        DatasetVersion ver1 = buildDtsetVersion(1L, 1L, DatasetVersion.VersionState.RELEASED);
        DatasetVersion ver2 = buildDtsetVersion(2L, 2L, DatasetVersion.VersionState.RELEASED);
        DatasetVersion verDraft = buildDtsetVersion(3L, 3L, DatasetVersion.VersionState.DRAFT);

        dataset.setVersions(Lists.newArrayList(ver1, ver2, verDraft));

        when(datasetDeaccessionBean.deaccessDatasetVersion(any(), any(), anyString(), anyString())).thenReturn(this.dataset.getLatestVersion());
        when(datasetVersionService.find(1L)).thenReturn(this.dataset.getVersions().get(0));
        when(datasetVersionService.find(2L)).thenReturn(this.dataset.getVersions().get(1));
        when(datasetVersionService.find(3L)).thenReturn(this.dataset.getVersions().get(2));
    }

    @Test
    public void deaccessVersion() {
        // given
        int versionToDeaccess = 1;

        // when
        deaccesssionService.deaccessVersions(dataset, Collections.singletonList(dataset.getVersions().get(versionToDeaccess)), "testReason", "testForwardUrl");

        // then
        verify(datasetDeaccessionBean, times(1)).deaccessDatasetVersion(any(), any(), anyString(), anyString());
    }

    @Test
    public void deaccessVersions() {
        // given & when
        deaccesssionService.deaccessVersions(dataset, dataset.getVersions(), "TestReasons", "fakeUrl");

        // then
        verify(datasetDeaccessionBean, times(3)).deaccessDatasetVersion(any(), any(), anyString(), anyString());
    }

    @Test
    public void deaccessReleasedVersions() {
        // given & when
        deaccesssionService.deaccessReleasedVersions(dataset, dataset.getVersions(), "TestReasons", "fakeUrl");

        // then
        verify(datasetDeaccessionBean, times(2)).deaccessDatasetVersion(any(), any(), anyString(), anyString());
    }

    // -------------------- PRIVATE ---------------------

    private DatasetVersion buildDtsetVersion(long id, long versionNumber, DatasetVersion.VersionState state) {
        DatasetVersion version = new DatasetVersion();
        version.setId(id);
        version.setVersionNumber(versionNumber);
        version.setDataset(this.dataset);
        version.setVersionState(state);
        version.setCreateTime(Timestamp.valueOf(LocalDateTime.now()));

        return version;
    }
}
