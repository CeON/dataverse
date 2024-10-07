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
import org.jboss.arquillian.transaction.api.annotation.TransactionMode;
import org.jboss.arquillian.transaction.api.annotation.Transactional;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import java.util.List;

import static edu.harvard.iq.dataverse.datafile.DatasetIntegrationTestsHelper.DRAFT_DATASET_WITH_FILES_ID;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Transactional(TransactionMode.ROLLBACK)
public class WholeDatasetDownloadLoggerTestIT extends WebappArquillianDeployment {

    @PersistenceContext(unitName = "VDCNet-ejbPU")
    private EntityManager em;

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
        long countBeforeDownload = extractNumberOfLoggedDownloads();
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
        long countBeforeDownload = extractNumberOfLoggedDownloads();
        datasetDownloadUiLogger.incrementLogIfDownloadingWholeDataset(filesToDownload);

        // then
        assertThat(extractNumberOfLoggedDownloads(), is(countBeforeDownload));
    }

    @Test
    public void incrementLogIfDownloadingWholeDataset() {
        // given
        DatasetVersion dsv = takeDatasetVersionFromPublishedDataset();

        // when
        long countBeforeDownload = extractNumberOfLoggedDownloads();
        datasetDownloadUiLogger.incrementLogForDownloadingWholeDataset(dsv);

        // then
        assertThat(extractNumberOfLoggedDownloads(), is(countBeforeDownload + 1));
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

    private DatasetVersion takeDatasetVersionFromPublishedDataset() {
        Dataset dataset = datasetDao.find(DRAFT_DATASET_WITH_FILES_ID);
        return dataset.getLatestVersion();
    }

    private long extractNumberOfLoggedDownloads() {
        TypedQuery<Long> numberOfDownloads = em.createQuery(
                "SELECT COUNT(d) FROM DownloadDatasetLog d WHERE d.datasetId = :datasetId", Long.class);
        numberOfDownloads.setParameter("datasetId", DRAFT_DATASET_WITH_FILES_ID);
        return numberOfDownloads.getSingleResult();
    }
}