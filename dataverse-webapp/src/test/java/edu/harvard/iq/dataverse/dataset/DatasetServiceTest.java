package edu.harvard.iq.dataverse.dataset;

import edu.harvard.iq.dataverse.DatasetDao;
import edu.harvard.iq.dataverse.DataverseRequestServiceBean;
import edu.harvard.iq.dataverse.DataverseSession;
import edu.harvard.iq.dataverse.EjbDataverseEngine;
import edu.harvard.iq.dataverse.PermissionsWrapper;
import edu.harvard.iq.dataverse.engine.command.impl.UpdateDatasetThumbnailCommand;
import edu.harvard.iq.dataverse.persistence.MocksFactory;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetLock;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DatasetServiceTest {

    @InjectMocks
    private DatasetService datasetService;

    @Mock
    private DataverseRequestServiceBean dvRequestService;

    @Mock
    private EjbDataverseEngine commandEngine;

    @Mock
    private DatasetDao datasetDao;

    @Mock
    private PermissionsWrapper permissionsWrapper;

    @Mock
    private DataverseSession session;

    @Test
    public void changeDatasetThumbnail() {
        //given
        Dataset dataset = new Dataset();
        DataFile thumbnailFile = new DataFile();

        when(commandEngine.submit(any(UpdateDatasetThumbnailCommand.class))).thenReturn(new DatasetThumbnail("", thumbnailFile));

        //when
        datasetService.changeDatasetThumbnail(dataset, thumbnailFile);

        //then
        verify(commandEngine, times(1)).submit(any(UpdateDatasetThumbnailCommand.class));

    }

    @Test
    public void changeDatasetThumbnail_WithFileFromDisk() throws IOException {
        //given
        Dataset dataset = new Dataset();

        when(commandEngine.submit(any(UpdateDatasetThumbnailCommand.class))).thenReturn(new DatasetThumbnail("", new DataFile()));

        //when
        datasetService.changeDatasetThumbnail(dataset, IOUtils.toInputStream("", "UTF-8"));

        //then
        verify(commandEngine, times(1)).submit(any(UpdateDatasetThumbnailCommand.class));

    }

    @Test
    public void removeDatasetThumbnail() {
        //given
        Dataset dataset = new Dataset();

        when(commandEngine.submit(any(UpdateDatasetThumbnailCommand.class))).thenReturn(new DatasetThumbnail("", new DataFile()));

        //when
        datasetService.removeDatasetThumbnail(dataset);

        //then
        verify(commandEngine, times(1)).submit(any(UpdateDatasetThumbnailCommand.class));
    }

    @Test
    public void setDatasetEmbargoDate() {
        // given
        Dataset dataset = new Dataset();
        Date embargoDate = Date.from(Instant.now().truncatedTo(ChronoUnit.DAYS).plus(2, ChronoUnit.DAYS));
        when(datasetDao.merge(dataset)).thenReturn(dataset);

        // when
        Dataset result = datasetService.setDatasetEmbargoDate(dataset, embargoDate);

        // then
        Assertions.assertEquals(embargoDate, result.getEmbargoDate().getOrNull());
    }

    @Test
    public void setDatasetEmbargoDate_lockedDataset() {
        // given
        Dataset dataset = new Dataset();
        DatasetLock lock = new DatasetLock(DatasetLock.Reason.InReview, MocksFactory.makeAuthenticatedUser("Jurek", "Kiler"));
        lock.setId(1L);
        dataset.addLock(lock);
        Date embargoDate = Date.from(Instant.now().truncatedTo(ChronoUnit.DAYS).plus(2, ChronoUnit.DAYS));

        // when
        Exception exception = Assertions.assertThrows(IllegalStateException.class, () -> {
            datasetService.setDatasetEmbargoDate(dataset, embargoDate);
        });

        // then
        String message = "Update embargo date failed. Dataset is locked. [DatasetLock[ id=1 ]]";
        Assertions.assertEquals(message, exception.getMessage());
        Assertions.assertTrue(dataset.getEmbargoDate().isEmpty());
    }

//    @Test
//    public void setDatasetEmbargoDate_publishedDataset_notSuperuser() {
//        // given
//        Dataset dataset = MocksFactory.makeDataset();
//        DatasetVersion version1 = new DatasetVersion();
//        DatasetVersion version2 = new DatasetVersion();
//        dataset.setVersions(Lists.newArrayList(version1, version2));
//        when(permissionsWrapper.canUpdateDataset(dvRequestService.getDataverseRequest(), dataset)).thenReturn(true);
//        when(permissionsWrapper.canUpdateAndPublishDataset(dvRequestService.getDataverseRequest(), dataset)).thenReturn(false);
//
//        Date embargoDate = Date.from(Instant.now().truncatedTo(ChronoUnit.DAYS).plus(2, ChronoUnit.DAYS));
//
//        // when
//        Exception exception = Assertions.assertThrows(IllegalStateException.class, () -> {
//            datasetService.setDatasetEmbargoDate(dataset, embargoDate);
//        });
//
//        // then
//        String message = "Update embargo date failed. Dataset is locked. [DatasetLock[ id=1 ]]";
//        Assertions.assertEquals(message, exception.getMessage());
//        Assertions.assertTrue(dataset.getEmbargoDate().isEmpty());
//    }
}