package edu.harvard.iq.dataverse.datasetutility;

import edu.harvard.iq.dataverse.DatasetDao;
import edu.harvard.iq.dataverse.DataverseDao;
import edu.harvard.iq.dataverse.DataverseSession;
import edu.harvard.iq.dataverse.arquillian.arquillianexamples.WebappArquillianDeployment;
import edu.harvard.iq.dataverse.authorization.AuthenticationServiceBean;
import edu.harvard.iq.dataverse.dataaccess.DataAccess;
import edu.harvard.iq.dataverse.dataaccess.StorageIO;
import edu.harvard.iq.dataverse.datafile.file.ReplaceFileHandler;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.datafile.FileMetadata;
import edu.harvard.iq.dataverse.persistence.datafile.license.FileTermsOfUse;
import edu.harvard.iq.dataverse.persistence.datafile.license.LicenseRepository;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.dataverse.DataverseContact;
import edu.harvard.iq.dataverse.util.FileUtil;
import edu.harvard.iq.dataverse.util.SystemConfig;
import org.apache.commons.io.IOUtils;
import org.jboss.arquillian.transaction.api.annotation.TransactionMode;
import org.jboss.arquillian.transaction.api.annotation.Transactional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Transactional(TransactionMode.ROLLBACK)
public class ReplaceFileHandlerIT extends WebappArquillianDeployment {

    @PersistenceContext(unitName = "VDCNet-ejbPU")
    private EntityManager em;

    @EJB
    private ReplaceFileHandler replaceFileHandler;

    @EJB
    private AuthenticationServiceBean authenticationServiceBean;


    @EJB
    private DataverseDao dataverseDao;

    @EJB
    private LicenseRepository licenseRepository;

    @Inject
    private DataverseSession dataverseSession;

    @EJB
    private DatasetDao datasetDao;

    @TempDir
    public File tempFiles;


    @BeforeEach
    public void setUp() {


        System.setProperty(SystemConfig.FILES_DIRECTORY, tempFiles.getAbsolutePath());
    }

    private static boolean isWindows() {

        return System.getProperty("os.name").toLowerCase().contains("win");
    }


    @Test
    public void shouldCreateDataFile() {

        // this test fails under Windows - the fix will require longer investigation
        if(isWindows()) {
            System.out.println("Skipped ReplaceFileHandlerIT.shouldReplaceFile. Windows detected");
            return;
        }


        //given
        Dataset dataset = new Dataset();

        fillDatasetWithRequiredData(dataset);
        em.persist(dataset);

        String fileName = "testFile";
        String fileContentType = "json";

        //when
        DataFile savedFile = replaceFileHandler.createDataFile(dataset, new ByteArrayInputStream(new byte[0]), fileName, fileContentType);

        //then
        assertEquals(fileName, savedFile.getFileMetadata().getLabel());
        assertEquals(fileContentType, savedFile.getContentType());

    }

    @Test
    public void shouldReplaceFile() throws IOException {

        // this test fails under Windows - the fix will require longer investigation
        if(isWindows()) {
            System.out.println("Skipped ReplaceFileHandlerIT.shouldReplaceFile. Windows detected");
            return;
        }

        //given
        dataverseSession.setUser(authenticationServiceBean.getAdminUser());

        Dataverse dataverse = new Dataverse();
        fillDataverseWithRequiredData(dataverse);
        em.persist(dataverse);

        Dataset dataset = new Dataset();
        dataset.setOwner(dataverse);
        fillDatasetWithExtendedData(dataset);
        em.persist(dataset);
        em.flush();

        DataFile initialFile = createTestDataFile("banner", "image/png");
        attachDataFileToDataset(initialFile, dataset.getLatestVersion());
        em.persist(initialFile);

        byte[] bytes = IOUtils.resourceToByteArray("images/coffeeshop.png", getClass().getClassLoader());
        File newFile = new File(FileUtil.getFilesTempDirectory() +"/coffeeshop.png");
        Files.write(newFile.toPath(), bytes);

        DataFile newDataFile = createTestDataFile("coffeeshop", "image/png");
        newDataFile.setStorageIdentifier("coffeeshop.png");

        //when
        replaceFileHandler.replaceFile(dataset.getFiles().get(0), dataset, newDataFile);

        //then
        Dataset dbDataset = datasetDao.find(dataset.getId());

        List<DatasetVersion> dbDatasetVersions = dbDataset.getVersions();
        Assertions.assertEquals(2, dbDatasetVersions.size());

        DatasetVersion dbOldVersion = dbDatasetVersions.get(1);
        Assertions.assertEquals(1, dbOldVersion.getFileMetadatas().size());
        Assertions.assertEquals("banner", dbOldVersion.getFileMetadatas().get(0).getLabel());

        DatasetVersion dbNewVersion = dbDatasetVersions.get(0);
        Assertions.assertEquals(1, dbNewVersion.getFileMetadatas().size());
        Assertions.assertEquals("coffeeshop", dbNewVersion.getFileMetadatas().get(0).getLabel());

        Assertions.assertEquals(2, dbDataset.getFiles().size());
        Assertions.assertEquals(dbOldVersion.getFileMetadatas().get(0).getDataFile(), dbDataset.getFiles().get(0));
        Assertions.assertEquals(dbNewVersion.getFileMetadatas().get(0).getDataFile(), dbDataset.getFiles().get(1));

        Assertions.assertEquals(dbDataset.getFiles().get(0).getId(), dbDataset.getFiles().get(0).getRootDataFileId());

        StorageIO<DataFile> newFileStorageIO = DataAccess.dataAccess().getStorageIO(dbDataset.getFiles().get(1));
        newFileStorageIO.open();
        byte[] newFileContent = IOUtils.toByteArray(newFileStorageIO.getInputStream());
        Assertions.assertArrayEquals(bytes, newFileContent);

    }
    private DataFile createTestDataFile(String filename, String fileContentType2) {
        DataFile savedFile = new DataFile(fileContentType2);
        savedFile.setChecksumType(DataFile.ChecksumType.SHA1);
        savedFile.setChecksumValue(filename);
        savedFile.setModificationTime(new Timestamp(new Date().getTime()));
        savedFile.setPermissionModificationTime(new Timestamp(new Date().getTime()));
        savedFile.setCreateDate(new Timestamp(new Date().getTime()));
        savedFile.setStorageIdentifier("testStorageId");

        FileMetadata fmd = new FileMetadata();
        fmd.setLabel(filename);

        FileTermsOfUse termsOfUse = new FileTermsOfUse();
        termsOfUse.setLicense(licenseRepository.findFirstActive());
        fmd.setTermsOfUse(termsOfUse);
        fmd.setDataFile(savedFile);
        savedFile.getFileMetadatas().add(fmd);

        return savedFile;
    }

    private void attachDataFileToDataset(DataFile dataFileToAttach, DatasetVersion datasetVersion) {
        FileMetadata fileMetadata = dataFileToAttach.getFileMetadatas().get(0);

        dataFileToAttach.setOwner(datasetVersion.getDataset());
        datasetVersion.addFileMetadata(fileMetadata);
        fileMetadata.setDatasetVersion(datasetVersion);
        datasetVersion.getDataset().getFiles().add(dataFileToAttach);
    }

    private Dataverse fillDataverseWithRequiredData(Dataverse dataverse) {
        dataverse.setName("TestDataverse");
        dataverse.setAlias("TestDataverseAlias");
        dataverse.setOwner(dataverseDao.findRootDataverse());
        dataverse.setDataverseType(Dataverse.DataverseType.LABORATORY);
        dataverse.setCreateDate(new Timestamp(System.currentTimeMillis()));
        dataverse.setModificationTime(new Timestamp(System.currentTimeMillis()));

        DataverseContact dataverseContact = new DataverseContact();
        dataverseContact.setContactEmail("testmail@test.com");
        List<DataverseContact> dataverseContacts = new ArrayList<>();
        dataverseContacts.add(dataverseContact);
        dataverse.setDataverseContacts(dataverseContacts);

        return dataverse;
    }

    private Dataset fillDatasetWithExtendedData(Dataset dataset) {
        dataset.setCreateDate(Timestamp.valueOf(LocalDateTime.of
                (LocalDate.of(2019, 12, 12), LocalTime.of(13, 15))));

        dataset.setModificationTime(Timestamp.valueOf(LocalDateTime.of
                (LocalDate.of(2019,12,12), LocalTime.of(13,15))));

        dataset.setAuthority("10.5072");
        dataset.setIdentifier("FK2/AAAAAA");
        dataset.setProtocol("doi");
        dataset.setStorageIdentifier("file://" + dataset.getAuthority() + "/" + dataset.getIdentifier());

        DatasetVersion editVersion = dataset.getEditVersion();
        editVersion.setVersionState(DatasetVersion.VersionState.RELEASED);

        editVersion.setCreateTime(Date.from(Instant.ofEpochMilli(1567763690000L)));
        editVersion.setLastUpdateTime(Date.from(Instant.ofEpochMilli(1567763690000L)));

        return dataset;
    }

    private Dataset fillDatasetWithRequiredData(Dataset dataset) {
        dataset.setCreateDate(Timestamp.valueOf(LocalDateTime.of
                (LocalDate.of(2019, 12, 12), LocalTime.of(13, 15))));

        dataset.setModificationTime(Timestamp.valueOf(LocalDateTime.of
                (LocalDate.of(2019,12,12), LocalTime.of(13,15))));

        DatasetVersion editVersion = dataset.getEditVersion();
        editVersion.setCreateTime(Date.from(Instant.ofEpochMilli(1567763690000L)));
        editVersion.setLastUpdateTime(Date.from(Instant.ofEpochMilli(1567763690000L)));

        return dataset;
    }

}
