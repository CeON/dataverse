package edu.harvard.iq.dataverse.dataset.datasetversion;

import edu.harvard.iq.dataverse.DataverseRequestServiceBean;
import edu.harvard.iq.dataverse.EjbDataverseEngine;
import edu.harvard.iq.dataverse.engine.command.impl.UpdateDatasetVersionCommand;
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

@ExtendWith(MockitoExtension.class)
class DatasetVersionServiceTest {

    @InjectMocks
    private DatasetVersionService datasetVersionService;

    @Mock
    private DataverseRequestServiceBean dvRequestService;

    @Mock
    private EjbDataverseEngine commandEngine;

    @Test
    public void updateDatasetVersion() {
        //given
        Dataset dataset = new Dataset();
        DatasetVersion datasetVersion = new DatasetVersion();

        //when
        datasetVersionService.updateDatasetVersion(dataset, datasetVersion,true);

        //then
        verify(commandEngine, times(1)).submit(any(UpdateDatasetVersionCommand.class));

    }
}