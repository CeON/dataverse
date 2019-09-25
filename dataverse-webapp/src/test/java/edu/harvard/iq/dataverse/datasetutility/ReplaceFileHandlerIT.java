package edu.harvard.iq.dataverse.datasetutility;

import edu.harvard.iq.dataverse.DataverseServiceBean;
import edu.harvard.iq.dataverse.DataverseSession;
import edu.harvard.iq.dataverse.MetadataBlockDao;
import edu.harvard.iq.dataverse.arquillian.arquillianexamples.WebappArquillianDeployment;
import edu.harvard.iq.dataverse.authorization.AuthenticationServiceBean;
import edu.harvard.iq.dataverse.authorization.groups.impl.explicit.ExplicitGroupServiceBean;
import edu.harvard.iq.dataverse.common.DatasetFieldConstant;
import edu.harvard.iq.dataverse.dataaccess.DataAccess;
import edu.harvard.iq.dataverse.dataset.file.ReplaceFileHandler;
import edu.harvard.iq.dataverse.dataverse.MetadataBlockService;
import edu.harvard.iq.dataverse.ingest.IngestServiceBean;
import edu.harvard.iq.dataverse.persistence.DvObject;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetField;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetFieldType;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.dataset.FieldType;
import edu.harvard.iq.dataverse.persistence.dataset.MetadataBlock;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.dataverse.DataverseContact;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.persistence.user.User;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.transaction.api.annotation.TransactionMode;
import org.jboss.arquillian.transaction.api.annotation.Transactional;
import org.jboss.weld.context.ejb.Ejb;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.xml.crypto.Data;
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
    public void shouldSaveAndAddFilesToDataset() throws IOException {
        //given
        dataverseSession.setUser(authenticationServiceBean.getAdminUser());

        Dataverse dataverse = new Dataverse();
        fillDataverseWithRequiredData(dataverse);

        Dataset dataset = new Dataset();
        DataAccess dataAccess = new DataAccess();

        dataset.setOwner(dataverse);

        File file = new File("/home/dataverse/dataverse4/dataverse-webapp/src/test/resources/txt/util/jsondata.txt");
        String fileName = "jsondata";
        String fileContentType = "txt/allgood";

        File file2 = new File("/home/dataverse/dataverse4/dataverse-webapp/src/test/resources/images/banner.png");
        String fileName2 = "banner";
        String fileContentType2 = "png/allgood";

        File file3 = new File("/home/dataverse/dataverse4/dataverse-webapp/src/test/resources/txt/export/openaire/dataset-organizations.txt");
        String fileName3 = "dataset-organizations";
        String fileContentType3 = "txt/allgood";


        DataFile savedFile = replaceFileHandler.createDataFile(dataset, Files.readAllBytes(file.toPath()), fileName, fileContentType);
        DataFile savedFile2 = replaceFileHandler.createDataFile(dataset, Files.readAllBytes(file2.toPath()), fileName2, fileContentType2);
        DataFile savedFile3 = replaceFileHandler.createDataFile(dataset, Files.readAllBytes(file3.toPath()), fileName3, fileContentType3);

        savedFile.setRootDataFileId(1L);
        savedFile.setId(99L);
        savedFile2.setRootDataFileId(1L);
        savedFile2.setId(98L);
        savedFile3.setRootDataFileId(1L);
        savedFile3.setId(97L);

        List<DataFile> newFiles = new ArrayList<>();
        newFiles.add(savedFile);
        newFiles.add(savedFile2);
        newFiles.add(savedFile3);

        fillDatasetWithExtendedData(dataset);
        em.persist(dataset);

        //when
        ingestServiceBean.saveAndAddFilesToDataset(dataset.getEditVersion(), newFiles, dataAccess);

        //then
        Assert.assertEquals(newFiles ,dataset.getFiles());
        Assert.assertNotSame(newFiles, dataset.getFiles());
        Assert.assertNotEquals(new ArrayList<DataFile>(), dataset.getFiles());
    }

    @Test
    public void shouldReplaceFile() throws IOException {
        //given
        dataverseSession.setUser(authenticationServiceBean.getAdminUser());

        Dataverse dataverse = new Dataverse();
        fillDataverseWithRequiredData(dataverse);

        Dataset dataset = new Dataset();
        DataAccess dataAccess = new DataAccess();

        dataset.setOwner(dataverse);

        File file2 = new File("/home/dataverse/dataverse4/dataverse-webapp/src/test/resources/images/banner.png");
        String fileName2 = "banner";
        String fileContentType2 = "png/allgood";

        DataFile savedFile2 = replaceFileHandler.createDataFile(dataset, Files.readAllBytes(file2.toPath()), fileName2, fileContentType2);
        savedFile2.setRootDataFileId(1L);
        savedFile2.setId(99L);

        List<DataFile> newFiles = new ArrayList<>();
        newFiles.add(savedFile2);

        fillDatasetWithExtendedData(dataset);
        ingestServiceBean.saveAndAddFilesToDataset(dataset.getEditVersion(), newFiles, dataAccess);

        File file3 = new File("/home/dataverse/dataverse4/dataverse-webapp/src/test/resources/images/coffeeshop.png");
        String fileName3 = "coffeeshop";
        String fileContentType3 = "png/allgood";

        DataFile savedFile3 = replaceFileHandler.createDataFile(dataset, Files.readAllBytes(file3.toPath()), fileName3, fileContentType3);
        newFiles.clear();
        newFiles.add(savedFile3);
        em.persist(dataset);

        //when
        replaceFileHandler.replaceFile(dataset.getFiles().get(0), dataset, savedFile3);

        //then
        Assert.assertEquals(2 ,dataset.getFiles().size());
        Assert.assertEquals(savedFile3.getFileMetadatas(), dataset.getLatestVersion().getFileMetadatas());
        Assert.assertTrue(dataset.getFiles().get(1).getFileMetadatas().get(0).getLabel().equals(savedFile3.getFileMetadatas().get(0).getLabel()));
        Assert.assertNotEquals(new ArrayList<DataFile>(), dataset.getFiles());
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

        dataset.getEditVersion().setDatasetFields(datasetFieldList);
        dataset.setCreateDate(new Timestamp(System.currentTimeMillis()));

        DatasetVersion editVersion = dataset.getEditVersion();
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
