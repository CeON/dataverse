package edu.harvard.iq.dataverse.datafile;

import edu.harvard.iq.dataverse.DatasetDao;
import edu.harvard.iq.dataverse.DataverseSession;
import edu.harvard.iq.dataverse.arquillian.arquillianexamples.WebappArquillianDeployment;
import edu.harvard.iq.dataverse.authorization.AuthenticationServiceBean;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.datafile.FileMetadata;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion.VersionState;
import edu.harvard.iq.dataverse.util.SystemConfig;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.awaitility.Awaitility;
import org.jboss.arquillian.transaction.api.annotation.TransactionMode;
import org.jboss.arquillian.transaction.api.annotation.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.ejb.EJB;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static edu.harvard.iq.dataverse.datafile.DatasetIntegrationTestsHelper.DRAFT_DATASET_WITH_FILES_ID;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.in;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.fail;

@Transactional(TransactionMode.ROLLBACK)
public class FileServiceIT extends WebappArquillianDeployment {

    @EJB
    private FileService fileService;

    @EJB
    private AuthenticationServiceBean authenticationServiceBean;

    @EJB
    private DatasetDao datasetDao;

    @Inject
    private DataverseSession dataverseSession;

    // -------------------- TESTS --------------------

    @BeforeEach
    public void setUp() {
        dataverseSession.setUser(authenticationServiceBean.getAdminUser());
    }

    @Test
    public void deleteFile_forDraftDataset() {
        // given
        Dataset dataset = datasetDao.find(DRAFT_DATASET_WITH_FILES_ID);
        Tuple2<VersionState, Long> versionDataBefore = getLatestVersionData(dataset);


        List<DataFile> files = dataset.getFiles();
        DataFile fileToDelete = files.get(0);
        FileMetadata fileToDeleteMetadata = fileToDelete.getFileMetadata();
        createPhysicalFileFromMetadata(fileToDeleteMetadata);

        // when
        Dataset updatedDataset = fileService.deleteFile(fileToDeleteMetadata);

        // then
        List<DataFile> updatedFiles = updatedDataset.getFiles();
        File deletedFile = buildPathForFile(fileToDeleteMetadata).toFile();

        assertThat("State and version after delete should match",
                getLatestVersionData(updatedDataset), equalTo(versionDataBefore));
        assertThat("File list in updated draft should not contain deleted file",
                fileToDelete, not(in(updatedFiles))); // DataFile#equals(…) is based only on file's id
        Awaitility.await().atMost(1, TimeUnit.MINUTES).until(() -> {
            assertThat("File should be physically deleted", deletedFile.exists(), is(false));
            return true;
        });
    }

    @Test
    public void deleteFile_forReleasedDataset() {
        // given
        publishDataset(DRAFT_DATASET_WITH_FILES_ID);
        Dataset dataset = datasetDao.find(DRAFT_DATASET_WITH_FILES_ID);

        Tuple2<VersionState, Long> versionDataBefore = getLatestVersionData(dataset);

        DatasetVersion versionBefore = dataset.getLatestVersion();
        List<FileMetadata> filesMetadataBefore = versionBefore.getFileMetadatas();
        FileMetadata fileToDeleteMetadata = filesMetadataBefore.get(0);
        createPhysicalFileFromMetadata(fileToDeleteMetadata);

        // when
        Dataset updatedDataset = fileService.deleteFile(fileToDeleteMetadata);

        // then
        DatasetVersion versionAfter = updatedDataset.getLatestVersion();
        Tuple2<VersionState, Long> versionDataAfter = getLatestVersionData(updatedDataset);
        File deletedFile = buildPathForFile(fileToDeleteMetadata).toFile();

        assertThat("State before and after delete should be different and state after delete should be DRAFT",
                versionDataAfter._1, allOf(not(equalTo(versionDataBefore._1)), equalTo(VersionState.DRAFT)));
        assertThat("Files' metadata in new version should not contain metadata of deleted file",
                fileToDeleteMetadata, not(in(versionAfter.getFileMetadatas())));
        assertThat(
                "Deleted file should be present in dataset' file collection, as it is used by previously published version(s)",
                fileToDeleteMetadata, in(extractFileListMetadata(updatedDataset))
        );
        assertThat("File should be physically present after delete",
                deletedFile.exists(), is(true));
    }

    @Test
    public void deleteFiles_forReleasedDataset() {
        // given
        publishDataset(DRAFT_DATASET_WITH_FILES_ID);
        Dataset dataset = datasetDao.find(DRAFT_DATASET_WITH_FILES_ID);

        Tuple2<VersionState, Long> versionDataBefore = getLatestVersionData(dataset);

        DatasetVersion latestVersionBefore = dataset.getLatestVersion();
        List<FileMetadata> filesToDeleteMetadata = latestVersionBefore.getFileMetadatas();
        createPhysicalFilesFromMetadata(filesToDeleteMetadata);

        // when
        Set<Dataset> results = fileService.deleteFiles(filesToDeleteMetadata);

        // then
        assertThat("As we delete file from only one dataset, we should have one-element result set",
                results, hasSize(1));
        Dataset updatedDataset = results.iterator().next();
        DatasetVersion versionAfter = updatedDataset.getLatestVersion();
        Tuple2<VersionState, Long> versionDataAfter = getLatestVersionData(updatedDataset);

        assertThat("State before and after delete should be different and state after delete should be DRAFT",
                versionDataAfter._1, allOf(not(equalTo(versionDataBefore._1)), equalTo(VersionState.DRAFT)));
        assertThat("All files metadata should have been deleted from latest version metadata",
                versionAfter.getFileMetadatas(), empty());
        assertThat(
                "Deleted files should be present in dataset file collection, as they're used by previously published version(s)",
                filesToDeleteMetadata, everyItem(in(extractFileListMetadata(updatedDataset)))
        );
        assertThat("Files should be physically present after delete",
                allFilesStream(filesToDeleteMetadata).allMatch(File::exists), is(true));
    }

    @Test
    public void deleteFiles_forDraft() {
        // given
        Dataset dataset = datasetDao.find(DRAFT_DATASET_WITH_FILES_ID);

        Tuple2<VersionState, Long> versionDataBefore = getLatestVersionData(dataset);

        List<DataFile> files = dataset.getFiles();
        Set<FileMetadata> filesToDeleteMetadata = files.stream()
                .map(DataFile::getFileMetadata)
                .collect(Collectors.toSet());
        createPhysicalFilesFromMetadata(filesToDeleteMetadata);

        // when
        Set<Dataset> results = fileService.deleteFiles(filesToDeleteMetadata);

        // then
        assertThat("As we delete files from only one dataset, we should have one-element result set",
                results, hasSize(1));
        Dataset updatedDataset = results.iterator().next();

        assertThat("State and version after delete should match",
                getLatestVersionData(updatedDataset), equalTo(versionDataBefore));
        assertThat("All files should have been deleted from dataset file list",
                updatedDataset.getFiles(), empty());

        Awaitility.await().atMost(1, TimeUnit.MINUTES).until(() -> {
            assertThat("All files should be physically deleted",
                    allFilesStream(filesToDeleteMetadata).noneMatch(File::exists), is(true));
            return true;
        });
    }


    // -------------------- PRIVATE --------------------

    private Tuple2<VersionState, Long> getLatestVersionData(Dataset dataset) {
        DatasetVersion latestVersion = dataset.getLatestVersion();
        return Tuple.of(latestVersion.getVersionState(), latestVersion.getVersionNumber());
    }

    private Path buildPathForFile(FileMetadata fileMetadata) {
        DataFile dataFile = fileMetadata.getDataFile();
        String storageIdentifier = dataFile.getStorageIdentifier().replace("^.*://", "");
        Dataset parent = dataFile.getOwner();
        Path pathToFile = Paths.get(SystemConfig.getFilesDirectoryStatic(), parent.getAuthority(), parent.getIdentifier());
        return Paths.get(pathToFile.toString(), storageIdentifier);
    }

    private void createPhysicalFileFromMetadata(FileMetadata fileMetadata) {
        File file = buildPathForFile(fileMetadata).toFile();
        File parentFile = file.getParentFile();
        if (parentFile != null) {
            parentFile.mkdirs();
        }
        try {
            file.createNewFile();
        } catch (IOException e) {
            fail("Cannot create file for test. Exception: " + e.getMessage());
        }
    }

    private void createPhysicalFilesFromMetadata(Collection<FileMetadata> filesMetadata) {
        for (FileMetadata fileMetadata : filesMetadata) {
            createPhysicalFileFromMetadata(fileMetadata);
        }
    }

    private void publishDataset(Long datasetId) {
        DatasetIntegrationTestsHelper.publishDataset(datasetDao.find(datasetId), authenticationServiceBean.getAdminUser());
    }

    private List<FileMetadata> extractFileListMetadata(Dataset dataset) {
        List<DataFile> files = dataset.getFiles();
        return files.stream()
                .map(DataFile::getFileMetadata)
                .collect(toList());
    }

    private Stream<File> allFilesStream(Collection<FileMetadata> filesToDeleteMetadata) {
        return filesToDeleteMetadata.stream()
                .map(this::buildPathForFile)
                .map(Path::toFile);
    }
}