package edu.harvard.iq.dataverse.datafile.page;

import edu.harvard.iq.dataverse.DatasetDao;
import edu.harvard.iq.dataverse.GenericDao;
import edu.harvard.iq.dataverse.arquillian.arquillianexamples.WebappArquillianDeployment;
import edu.harvard.iq.dataverse.authorization.AuthenticationServiceBean;
import edu.harvard.iq.dataverse.datafile.DatasetIntegrationTestsHelper;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.datafile.FileMetadata;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.dataset.DownloadDatasetLog;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.transaction.api.annotation.TransactionMode;
import org.jboss.arquillian.transaction.api.annotation.Transactional;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;
import java.util.List;

import static edu.harvard.iq.dataverse.datafile.DatasetIntegrationTestsHelper.DRAFT_DATASET_WITH_FILES_ID;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

@RunWith(Arquillian.class)
@Transactional(TransactionMode.ROLLBACK)
public class WholeDatasetDownloadLoggerTestIT extends WebappArquillianDeployment {

    @Inject
    private WholeDatasetDownloadLogger datasetDownloadUiLogger;

    @Inject
    private DatasetDao datasetDao;

    @Inject
    private AuthenticationServiceBean authenticationServiceBean;

    @Inject
    private GenericDao genericDao;

    @Test
    public void incrementLogIfDownloadingWholeDataset_whenDownloadingAllFiles() {
        // given
        List<DataFile> filesToDownload = takeFilesMetadataFromPublishedDataset();

        // when
        int countBeforeDownload = extractNumberOfLoggedDownloads();
        datasetDownloadUiLogger.incrementLogIfDownloadingWholeDataset(filesToDownload);

        // then
        assertThat(extractNumberOfLoggedDownloads(), is(countBeforeDownload + 1));
    }

    @Test
    public void incrementLogIfDownloadingWholeDataset_whenNotDownloadingAllFiles() {
        // given
        List<DataFile> filesToDownload = takeFilesMetadataFromPublishedDataset();

        // Please be careful when modifying script with database test data: we expect at least 2 files for this dataset
        filesToDownload.remove(0);

        // when
        int countBeforeDownload = extractNumberOfLoggedDownloads();
        datasetDownloadUiLogger.incrementLogIfDownloadingWholeDataset(filesToDownload);

        // then
        assertThat(extractNumberOfLoggedDownloads(), is(countBeforeDownload));
    }

    // -------------------- PRIVATE --------------------

    private List<DataFile> takeFilesMetadataFromPublishedDataset() {
        Dataset dataset = datasetDao.find(DRAFT_DATASET_WITH_FILES_ID);
        DatasetIntegrationTestsHelper.publishDataset(dataset, authenticationServiceBean.getAdminUser());
        DatasetVersion currentVersion = dataset.getLatestVersion();
        return currentVersion.getFileMetadatas().stream()
                .map(FileMetadata::getDataFile)
                .collect(toList());
    }

    private int extractNumberOfLoggedDownloads() {
        DownloadDatasetLog downloadDatasetLog = genericDao.find(DRAFT_DATASET_WITH_FILES_ID, DownloadDatasetLog.class);
        return downloadDatasetLog != null ? downloadDatasetLog.getDownloadCount() : 0;
    }
}