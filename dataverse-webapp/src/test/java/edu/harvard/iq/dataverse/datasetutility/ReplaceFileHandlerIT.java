package edu.harvard.iq.dataverse.datasetutility;

import edu.harvard.iq.dataverse.arquillian.arquillianexamples.WebappArquillianDeployment;
import edu.harvard.iq.dataverse.dataaccess.DataAccess;
import edu.harvard.iq.dataverse.dataset.file.ReplaceFileHandler;
import edu.harvard.iq.dataverse.ingest.IngestServiceBean;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.transaction.api.annotation.TransactionMode;
import org.jboss.arquillian.transaction.api.annotation.Transactional;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
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

    @Inject
    private IngestServiceBean ingestServiceBean;

    @Inject
    private ReplaceFileHandler replaceFileHandler;

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
        Dataset dataset = new Dataset();
        DataAccess dataAccess = new DataAccess();

        File file = new File("/home/dataverse/dataverse4/dataverse-webapp/src/test/resources/txt/util/jsondata.txt");
        String fileName = "jsondata";
        String fileContentType = "txt";

        File file2 = new File("/home/dataverse/dataverse4/dataverse-webapp/src/test/resources/images/banner.png");
        String fileName2 = "banner";
        String fileContentType2 = "png";

        File file3 = new File("/home/dataverse/dataverse4/dataverse-webapp/src/test/resources/images/banner.png");
        String fileName3 = "banner";
        String fileContentType3 = "png";


        DataFile savedFile = replaceFileHandler.createDataFile(dataset, Files.readAllBytes(file.toPath()), fileName, fileContentType);
        DataFile savedFile2 = replaceFileHandler.createDataFile(dataset, Files.readAllBytes(file2.toPath()), fileName2, fileContentType2);
        DataFile savedFile3 = replaceFileHandler.createDataFile(dataset, Files.readAllBytes(file3.toPath()), fileName3, fileContentType3);

        List<DataFile> newFiles = new ArrayList<>();
        newFiles.add(savedFile);
        newFiles.add(savedFile2);
        newFiles.add(savedFile3);

        fillDatasetWithRequiredData(dataset);
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
        Dataset dataset = new Dataset();
        DataAccess dataAccess = new DataAccess();

        File file2 = new File("/home/dataverse/dataverse4/dataverse-webapp/src/test/resources/images/banner.png");
        String fileName2 = "banner";
        String fileContentType2 = "png/allgood";

        DataFile savedFile2 = replaceFileHandler.createDataFile(dataset, Files.readAllBytes(file2.toPath()), fileName2, fileContentType2);

        File file3 = new File("/home/dataverse/dataverse4/dataverse-webapp/src/test/resources/images/coffeeshop.png");
        String fileName3 = "coffeeshop";
        String fileContentType3 = "png/allgood";

        DataFile savedFile3 = replaceFileHandler.createDataFile(dataset, Files.readAllBytes(file3.toPath()), fileName3, fileContentType3);

        List<DataFile> newFiles = new ArrayList<>();
        newFiles.add(savedFile2);

        fillDatasetWithRequiredData(dataset);
        ingestServiceBean.saveAndAddFilesToDataset(dataset.getEditVersion(), newFiles, dataAccess);
        em.persist(dataset);

        //when
        newFiles.clear();
        newFiles.add(savedFile3);

        replaceFileHandler.replaceFile(dataset.getFiles().get(0), dataset, savedFile3);
        //then
        Assert.assertEquals(newFiles ,dataset.getFiles());
        Assert.assertNotSame(newFiles, dataset.getFiles());
        Assert.assertNotEquals(new ArrayList<DataFile>(), dataset.getFiles());
    }

    private Dataset fillDatasetWithRequiredData(Dataset dataset) {
        dataset.setCreateDate(Timestamp.valueOf(LocalDateTime.of
                (LocalDate.of(2019, 12, 12), LocalTime.of(13, 15))));

        dataset.setModificationTime(Timestamp.valueOf(LocalDateTime.of
                (LocalDate.of(2019,12,12), LocalTime.of(13,15))));

        dataset.setAuthority("10.5072");
        dataset.setIdentifier("FK2/AAAAAA");

        DatasetVersion editVersion = dataset.getEditVersion();
        editVersion.setCreateTime(Date.from(Instant.ofEpochMilli(1567763690000L)));
        editVersion.setLastUpdateTime(Date.from(Instant.ofEpochMilli(1567763690000L)));

        return dataset;
    }


}
