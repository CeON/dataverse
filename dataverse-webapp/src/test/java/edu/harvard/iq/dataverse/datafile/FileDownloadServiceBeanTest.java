package edu.harvard.iq.dataverse.datafile;

import edu.harvard.iq.dataverse.arquillian.facesmock.FacesContextMocker;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.util.FileUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileDownloadServiceBeanTest {

    private final FileDownloadServiceBean fileDownloadService = new FileDownloadServiceBean();

    @Test
    void redirectToDownloadWholeDataset() throws IOException {
        //given
        FacesContextMocker.mockContext();

        DatasetVersion dsv = new DatasetVersion();
        dsv.setId(1L);
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dsv.setDataset(dataset);
        FileUtil.ApiBatchDownloadType defaultDownloadOption = FileUtil.ApiBatchDownloadType.DEFAULT;

        //execute
        String redirectUrl = fileDownloadService.redirectToDownloadWholeDataset(dsv, true, defaultDownloadOption);

        //assert
        assertEquals("/api/1/versions/1/files?gbrecs=true", redirectUrl);
    }

    @Test
    void redirectToDownloadWholeDataset_WithoutGbRecs() throws IOException {
        //given
        FacesContextMocker.mockContext();

        DatasetVersion dsv = new DatasetVersion();
        dsv.setId(1L);
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dsv.setDataset(dataset);
        FileUtil.ApiBatchDownloadType defaultDownloadOption = FileUtil.ApiBatchDownloadType.DEFAULT;

        //execute
        String redirectUrl = fileDownloadService.redirectToDownloadWholeDataset(dsv, false, defaultDownloadOption);

        //assert
        assertEquals("/api/1/versions/1/files", redirectUrl);
    }

    @Test
    void redirectToDownloadWholeDataset_WithOriginalFormat() throws IOException {
        //given
        FacesContextMocker.mockContext();

        DatasetVersion dsv = new DatasetVersion();
        dsv.setId(1L);
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dsv.setDataset(dataset);
        FileUtil.ApiBatchDownloadType downloadOption = FileUtil.ApiBatchDownloadType.ORIGINAL;

        //execute
        String redirectUrl = fileDownloadService.redirectToDownloadWholeDataset(dsv, false, downloadOption);

        //assert
        assertEquals("/api/1/versions/1/files?format=original", redirectUrl);
    }
}