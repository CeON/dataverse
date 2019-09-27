package edu.harvard.iq.dataverse.datasetutility;

import edu.harvard.iq.dataverse.DataFileServiceBean;
import edu.harvard.iq.dataverse.DatasetServiceBean;
import edu.harvard.iq.dataverse.DataverseServiceBean;
import edu.harvard.iq.dataverse.DataverseSession;
import edu.harvard.iq.dataverse.MetadataBlockDao;
import edu.harvard.iq.dataverse.arquillian.arquillianexamples.WebappArquillianDeployment;
import edu.harvard.iq.dataverse.authorization.AuthenticationServiceBean;
import edu.harvard.iq.dataverse.dataaccess.DataAccess;
import edu.harvard.iq.dataverse.dataset.file.ReplaceFileHandler;
import edu.harvard.iq.dataverse.ingest.IngestServiceBean;
import edu.harvard.iq.dataverse.license.TermsOfUseFactory;
import edu.harvard.iq.dataverse.license.TermsOfUseFormMapper;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.datafile.FileMetadata;
import edu.harvard.iq.dataverse.persistence.datafile.license.FileTermsOfUse;
import edu.harvard.iq.dataverse.persistence.datafile.license.LicenseDAO;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetField;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetFieldType;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.dataset.FieldType;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.dataverse.DataverseContact;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.transaction.api.annotation.TransactionMode;
import org.jboss.arquillian.transaction.api.annotation.Transactional;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.JoinColumn;
import javax.persistence.PersistenceContext;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static edu.harvard.iq.dataverse.util.FileUtil.calculateChecksum;
import static edu.harvard.iq.dataverse.util.FileUtil.getFilesTempDirectory;
import static org.junit.Assert.assertEquals;

@RunWith(Arquillian.class)
@Transactional(TransactionMode.ROLLBACK)
public class ReplaceFileHandlerIT extends WebappArquillianDeployment {

    @PersistenceContext(unitName = "VDCNet-ejbPU")
    private EntityManager em;

    @EJB
    private IngestServiceBean ingestServiceBean;

    @EJB
    private ReplaceFileHandler replaceFileHandler;

    @EJB
    private AuthenticationServiceBean authenticationServiceBean;

    @EJB
    private MetadataBlockDao metadataBlockDao;

    @EJB
    private DataverseServiceBean dataverseServiceBean;

    @EJB
    private DataFileServiceBean datafile;

    @EJB
    private TermsOfUseFactory termsOfUseFactory;

    @EJB
    private TermsOfUseFormMapper termsOfUseFormMapper;

    @EJB
    private SettingsServiceBean settingsService;

    @EJB
    private LicenseDAO licenseDAO;

    @Inject
    private DataverseSession dataverseSession;

    @Test
    public void shouldCreateDataFile() {
        //given
        Dataset dataset = new Dataset();

        fillDatasetWithRequiredData(dataset);
        em.persist(dataset);

        String fileName = "testFile";
        String fileContentType = "json";

        //when
        DataFile savedFile = replaceFileHandler.createDataFile(dataset, new byte[0], fileName, fileContentType);

        //then
        assertEquals(fileName, savedFile.getFileMetadata().getLabel());
        assertEquals(fileContentType, savedFile.getContentType());

    }

    @Test
    public void shouldReplaceFile() throws IOException {
        //given
        dataverseSession.setUser(authenticationServiceBean.getAdminUser());

        Dataverse dataverse = new Dataverse();
        fillDataverseWithRequiredData(dataverse);
        em.persist(dataverse);

        Dataset dataset = new Dataset();
        dataset.setOwner(dataverse);
        fillDatasetWithExtendedData(dataset);
        em.persist(dataset);

        DataFile initialFile = createTestDataFile(dataset.getEditVersion(), "banner", "png/allgood", null, true);

        em.persist(initialFile);
        em.flush();

        byte[] bytes = IOUtils.resourceToByteArray("images/coffeeshop.png", getClass().getClassLoader());
        File newfile = new File("/tmp/files/10.5072/FK2/AAAAAA/coffeeshop.png");
        try {
            Files.write(newfile.toPath(), bytes);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        DataFile newDataFile = createTestDataFile(dataset.getEditVersion(), "coffeeshop", "png/allgood", newfile, false);
        em.persist(newDataFile);
        em.flush();
        //when
        replaceFileHandler.replaceFile(dataset.getFiles().get(0), dataset, newDataFile);

        //then
        Assert.assertEquals(2 ,dataset.getFiles().size());

        Assert.assertEquals(newDataFile.getFileMetadatas(), dataset.getLatestVersion().getFileMetadatas());
        Assert.assertTrue(dataset.getFiles().get(1).getFileMetadatas().get(0).getLabel().equals(newDataFile.getFileMetadatas().get(0).getLabel()));
    }

    private DataFile createTestDataFile(DatasetVersion datasetVersion, String filename, String fileContentType2, File file, boolean addToDataset) {
        DataFile.ChecksumType checksumType = DataFile.ChecksumType.fromString(settingsService.getValueForKey(SettingsServiceBean.Key.FileFixityChecksumAlgorithm));
        DataFile savedFile = new DataFile(fileContentType2);
        savedFile.setModificationTime(new Timestamp(new Date().getTime()));
        savedFile.setPermissionModificationTime(new Timestamp(new Date().getTime()));
        savedFile.setCreateDate(new Timestamp(new Date().getTime()));
        FileMetadata fmd = new FileMetadata();
        fmd.setLabel(filename);

        FileTermsOfUse termsOfUse = new FileTermsOfUse();
        termsOfUse.setLicense(licenseDAO.findFirstActive());
        fmd.setTermsOfUse(termsOfUse);
        termsOfUse.setFileMetadata(fmd);

        fmd.setTermsOfUseForm(termsOfUseFormMapper.mapToForm(termsOfUse));

        fmd.setDataFile(savedFile);
        savedFile.getFileMetadatas().add(fmd);
        em.persist(fmd);

        if(addToDataset) {
            savedFile.setOwner(datasetVersion.getDataset());
        }

        if(addToDataset) {
            datasetVersion.addFileMetadata(fmd);
            fmd.setDatasetVersion(datasetVersion);
            datasetVersion.getDataset().getFiles().add(savedFile);
        }
        datafile.generateStorageIdentifier(savedFile);
        savedFile.setChecksumType(checksumType);
        savedFile.setChecksumValue(filename);

        return savedFile;
    }

    private Dataverse fillDataverseWithRequiredData(Dataverse dataverse) {
        dataverse.setName("TestDataverse");
        dataverse.setAlias("TestDataverseAlias");
        dataverse.setOwner(dataverseServiceBean.findRootDataverse());
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

        DatasetField datasetField = new DatasetField();
        datasetField.setSingleValue("testvalue");
        DatasetFieldType datasetFieldType = new DatasetFieldType("test", FieldType.TEXT, false);
        datasetFieldType.setMetadataBlock(metadataBlockDao.findById(1L));
        datasetFieldType.setRequired(false);
        List<DatasetField> datasetFieldList = new ArrayList<>();
        datasetField.setDatasetFieldType(datasetFieldType);
        datasetFieldList.add(datasetField);
        em.persist(datasetFieldType);

        dataset.getEditVersion().setDatasetFields(datasetFieldList);
        dataset.setCreateDate(new Timestamp(System.currentTimeMillis()));

        DatasetVersion editVersion = dataset.getEditVersion();

        editVersion.setCreateTime(Date.from(Instant.ofEpochMilli(1567763690000L)));
        editVersion.setLastUpdateTime(Date.from(Instant.ofEpochMilli(1567763690000L)));
        em.persist(editVersion);

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
