package edu.harvard.iq.dataverse.datafile;

import com.google.common.collect.Lists;
import edu.harvard.iq.dataverse.DataFileServiceBean;
import edu.harvard.iq.dataverse.DataverseRequestServiceBean;
import edu.harvard.iq.dataverse.EjbDataverseEngine;
import edu.harvard.iq.dataverse.dataset.DatasetThumbnail;
import edu.harvard.iq.dataverse.engine.command.impl.UpdateDatasetThumbnailCommand;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.datafile.FileMetadata;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataFilesServiceTest {

    @InjectMocks
    private DataFilesService dataFilesService;

    @Mock
    private EjbDataverseEngine commandEngine;

    @Mock
    private DataverseRequestServiceBean dvRequestService;

    @Mock
    private DataFileServiceBean datafileDao;

    // -------------------- TESTS --------------------

    @Test
    public void changeDatasetThumbnail() {
        //given
        Dataset dataset = new Dataset();
        when(commandEngine.submit(any(UpdateDatasetThumbnailCommand.class))).thenReturn(new DatasetThumbnail("", new DataFile()));

        //when
        dataFilesService.changeDatasetThumbnail(dataset, 1);

        //then
        verify(commandEngine, times(1)).submit(any(UpdateDatasetThumbnailCommand.class));

    }

    @Test
    public void deleteFileVersions() {
        //given

        //when

        //then

    }

    @Test
    public void deleteFileVersion() {
        //given
        FileMetadata file = prepareFileMetadata();
        //when

        //then

    }

    // -------------------- PRIVATE --------------------

    private FileMetadata prepareFileMetadata() {
        FileMetadata fileToDelete = new FileMetadata();
        DataFile dataFile = new DataFile();
        Dataset datafileOwner = new Dataset();
        DatasetVersion datasetVersion = new DatasetVersion();

        datafileOwner.setVersions(Lists.newArrayList(datasetVersion));
        datafileOwner.setThumbnailFile(dataFile);
        dataFile.setOwner(datafileOwner);
        fileToDelete.setDataFile(dataFile);
        fileToDelete.setDatasetVersion(datasetVersion);
        return fileToDelete;
    }
}