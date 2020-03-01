package edu.harvard.iq.dataverse.ingest;

import edu.harvard.iq.dataverse.persistence.MocksFactory;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.datafile.DataTable;
import edu.harvard.iq.dataverse.persistence.datafile.FileMetadata;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static edu.harvard.iq.dataverse.persistence.MocksFactory.makeDataset;
import static org.junit.jupiter.api.Assertions.*;

public class IngestUtilTest {

    @Test
    public void checkForDuplicateFileNamesFinal_noDirectories() throws Exception {

         Dataset dataset = makeDataset();
       DatasetVersion datasetVersion = getDatasetVersion(dataset);

        List<DataFile> dataFileList = new ArrayList<>();
        DataFile datafile1 = createDataFile(dataset, "application/octet-stream", "datafile1.txt");

        FileMetadata fmd1 = createFileMetadata(datasetVersion, datafile1, 1L, "datafile1.txt");
        datafile1.getFileMetadatas().add(fmd1);
        datasetVersion.getFileMetadatas().add(fmd1);

        dataFileList.add(datafile1);

        DataFile datafile2 = createDataFile(dataset, "application/octet-stream", "datafile2.txt");
        FileMetadata fmd2 = createFileMetadata(datasetVersion, datafile2, 2L, "datafile2.txt");
        datafile2.getFileMetadatas().add(fmd2);
        datasetVersion.getFileMetadatas().add(fmd2);

        dataFileList.add(datafile2);

        IngestUtil.checkForDuplicateFileNamesFinal(datasetVersion, dataFileList);

        boolean file1NameAltered = false;
        boolean file2NameAltered = false;
        for (DataFile df : dataFileList) {
            if (df.getFileMetadata().getLabel().equals("datafile1-1.txt")) {
                file1NameAltered = true;
            }
            if (df.getFileMetadata().getLabel().equals("datafile2-1.txt")) {
                file2NameAltered = true;
            }
        }

        assertTrue(file1NameAltered);
        assertTrue(file2NameAltered);

        // try to add data files with "-1" duplicates and see if it gets incremented to "-2"
        IngestUtil.checkForDuplicateFileNamesFinal(datasetVersion, dataFileList);

        for (DataFile df : dataFileList) {
            if (df.getFileMetadata().getLabel().equals("datafile1-2.txt")) {
                file1NameAltered = true;
            }
            if (df.getFileMetadata().getLabel().equals("datafile2-2.txt")) {
                file2NameAltered = true;
            }
        }

        assertTrue(file1NameAltered);
        assertTrue(file2NameAltered);
    }

    @NotNull
    private FileMetadata createFileMetadata(DatasetVersion datasetVersion, DataFile datafile1, long l, String s) {
        FileMetadata fmd1 = new FileMetadata();
        fmd1.setId(l);
        fmd1.setLabel(s);
        fmd1.setDataFile(datafile1);
        fmd1.setDatasetVersion(datasetVersion);
        return fmd1;
    }

    @NotNull
    private DataFile createDataFile(Dataset dataset, String s, String s2) {
        DataFile datafile1 = new DataFile(s);
        datafile1.setStorageIdentifier(s2);
        datafile1.setFilesize(200);
        datafile1.setModificationTime(new Timestamp(new Date().getTime()));
        datafile1.setCreateDate(new Timestamp(new Date().getTime()));
        datafile1.setPermissionModificationTime(new Timestamp(new Date().getTime()));
        datafile1.setOwner(dataset);
        datafile1.setIngestDone();
        datafile1.setChecksumType(DataFile.ChecksumType.SHA1);
        datafile1.setChecksumValue("Unknown");
        return datafile1;
    }

    private DatasetVersion getDatasetVersion(Dataset dataset) throws ParseException {
        DatasetVersion datasetVersion = dataset.getEditVersion();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
        datasetVersion.setCreateTime(dateFormat.parse("20001012"));
        datasetVersion.setLastUpdateTime(datasetVersion.getLastUpdateTime());
        datasetVersion.setId(MocksFactory.nextId());
        datasetVersion.setReleaseTime(dateFormat.parse("20010101"));
        datasetVersion.setVersionState(DatasetVersion.VersionState.RELEASED);
        datasetVersion.setMinorVersionNumber(0L);
        datasetVersion.setVersionNumber(1L);
        datasetVersion.setFileMetadatas(new ArrayList<>());
        return datasetVersion;
    }

    @Test
    public void checkForDuplicateFileNamesFinal_emptyDirectoryLabels() throws Exception {

        Dataset dataset = makeDataset();
        DatasetVersion datasetVersion = getDatasetVersion(dataset);

        List<DataFile> dataFileList = new ArrayList<>();
        DataFile datafile1 = createDataFile(dataset, "application/octet-stream", "datafile1.txt");
        FileMetadata fmd1 = createFileMetadata(datasetVersion, datafile1, 1L, "datafile1.txt");
        fmd1.setDirectoryLabel("");
        datafile1.getFileMetadatas().add(fmd1);
        datasetVersion.getFileMetadatas().add(fmd1);

        dataFileList.add(datafile1);

        DataFile datafile2 = createDataFile(dataset, "application/octet-stream", "datafile2.txt");
        FileMetadata fmd2 = createFileMetadata(datasetVersion, datafile2, 2L, "datafile2.txt");
        fmd2.setDirectoryLabel("");
        datafile2.getFileMetadatas().add(fmd2);
        datasetVersion.getFileMetadatas().add(fmd2);

        dataFileList.add(datafile2);

        IngestUtil.checkForDuplicateFileNamesFinal(datasetVersion, dataFileList);

        boolean file1NameAltered = false;
        boolean file2NameAltered = false;
        for (DataFile df : dataFileList) {
            if (df.getFileMetadata().getLabel().equals("datafile1-1.txt")) {
                file1NameAltered = true;
            }
            if (df.getFileMetadata().getLabel().equals("datafile2-1.txt")) {
                file2NameAltered = true;
            }
        }

        assertTrue(file1NameAltered);
        assertTrue(file2NameAltered);

        // try to add data files with "-1" duplicates and see if it gets incremented to "-2"
        IngestUtil.checkForDuplicateFileNamesFinal(datasetVersion, dataFileList);

        for (DataFile df : dataFileList) {
            if (df.getFileMetadata().getLabel().equals("datafile1-2.txt")) {
                file1NameAltered = true;
            }
            if (df.getFileMetadata().getLabel().equals("datafile2-2.txt")) {
                file2NameAltered = true;
            }
        }

        assertTrue(file1NameAltered);
        assertTrue(file2NameAltered);
    }

    @Test
    public void checkForDuplicateFileNamesFinal_withDirectories() throws Exception {

        Dataset dataset = makeDataset();
        DatasetVersion datasetVersion = getDatasetVersion(dataset);

        List<DataFile> dataFileList = new ArrayList<>();
        DataFile datafile1 = createDataFile(dataset, "application/octet-stream", "subdir/datafile1.txt");
        FileMetadata fmd1 = createFileMetadata(datasetVersion, datafile1, 1L, "datafile1.txt");
        fmd1.setDirectoryLabel("subdir");
        datafile1.getFileMetadatas().add(fmd1);
        datasetVersion.getFileMetadatas().add(fmd1);

        dataFileList.add(datafile1);

        DataFile datafile2 = createDataFile(dataset, "application/octet-stream", "subdir/datafile2.txt");
        FileMetadata fmd2 = createFileMetadata(datasetVersion, datafile2, 2L, "datafile2.txt");
        fmd2.setDirectoryLabel("subdir");
        datafile2.getFileMetadatas().add(fmd2);
        datasetVersion.getFileMetadatas().add(fmd2);

        dataFileList.add(datafile2);

        DataFile datafile3 = createDataFile(dataset, "application/octet-stream", "datafile2.txt");
        FileMetadata fmd3 = new FileMetadata();
        fmd3.setId(3L);
        fmd3.setLabel("datafile2.txt");
        fmd3.setDataFile(datafile3);
        datafile3.getFileMetadatas().add(fmd3);

        dataFileList.add(datafile3);

        IngestUtil.checkForDuplicateFileNamesFinal(datasetVersion, dataFileList);

        boolean file1NameAltered = false;
        boolean file2NameAltered = false;
        boolean file3NameAltered = true;
        for (DataFile df : dataFileList) {
            if (df.getFileMetadata().getLabel().equals("datafile1-1.txt")) {
                file1NameAltered = true;
            }
            if (df.getFileMetadata().getLabel().equals("datafile2-1.txt")) {
                file2NameAltered = true;
            }
            if (df.getFileMetadata().getLabel().equals("datafile2.txt")) {
                file3NameAltered = false;
            }
        }

        // check filenames are unique
        assertTrue(file1NameAltered);
        assertTrue(file2NameAltered);
        assertFalse(file3NameAltered);

        // add duplicate file in root
        datasetVersion.getFileMetadatas().add(fmd3);
        fmd3.setDatasetVersion(datasetVersion);

        // try to add data files with "-1" duplicates and see if it gets incremented to "-2"
        IngestUtil.checkForDuplicateFileNamesFinal(datasetVersion, dataFileList);

        for (DataFile df : dataFileList) {
            if (df.getFileMetadata().getLabel().equals("datafile1-2.txt")) {
                file1NameAltered = true;
            }
            if (df.getFileMetadata().getLabel().equals("datafile2-2.txt")) {
                file2NameAltered = true;
            }
            if (df.getFileMetadata().getLabel().equals("datafile2-1.txt")) {
                file3NameAltered = true;
            }
        }

        // check filenames are unique
        assertTrue(file1NameAltered);
        assertTrue(file2NameAltered);
        assertTrue(file3NameAltered);
    }

    @Test
    public void checkForDuplicateFileNamesFinal_tabularFiles() throws Exception {

        Dataset dataset = makeDataset();
        DatasetVersion datasetVersion = getDatasetVersion(dataset);

        List<DataFile> dataFileList = new ArrayList<>();
        DataFile datafile1 = createTabularDataFile(dataset);

        FileMetadata fmd1 = createFileMetadata(datasetVersion, datafile1, 1L, "foobar.tab");
        datafile1.getFileMetadatas().add(fmd1);
        datasetVersion.getFileMetadatas().add(fmd1);

        DataFile datafile2 = createTabularDataFile(dataset);

        FileMetadata fmd2 = new FileMetadata();
        fmd2.setId(2L);
        fmd2.setLabel("foobar.dta");
        fmd2.setDataFile(datafile2);
        datafile2.getFileMetadatas().add(fmd2);

        dataFileList.add(datafile2);

        IngestUtil.checkForDuplicateFileNamesFinal(datasetVersion, dataFileList);

        boolean file2NameAltered = false;
        for (DataFile df : dataFileList) {
            if (df.getFileMetadata().getLabel().equals("foobar-1.dta")) {
                file2NameAltered = true;
            }
        }

        // check filename is altered since tabular and will change to .tab after ingest
        assertTrue(file2NameAltered);
    }

    @NotNull
    private DataFile createTabularDataFile(Dataset dataset) {
        DataFile datafile1 = createDataFile(dataset, "application/x-strata", "foobar.dta");
        DataTable dt1 = new DataTable();
        dt1.setOriginalFileFormat("application/x-stata");
        datafile1.setDataTable(dt1);
        return datafile1;
    }

    @Test
    public void recalculateDatasetVersionUNF_nullVersion_expectedNoException() {
        IngestUtil.recalculateDatasetVersionUNF(null);
    }

    @Test
    public void recalculateDatasetVersionUNF_noFileVersion_expectedNullUnf() {
        DatasetVersion dsvNoFile = new DatasetVersion();
        IngestUtil.recalculateDatasetVersionUNF(dsvNoFile);
        assertNull(dsvNoFile.getUNF());
    }

    @Test
    public void recalculateDatasetVersionUNF() {
        //GIVEN
        Dataset dataset = new Dataset();
        dataset.setProtocol("doi");
        dataset.setAuthority("fakeAuthority");
        dataset.setIdentifier("12345");

        DatasetVersion datasetVersion = new DatasetVersion();
        datasetVersion.setDataset(dataset);
        datasetVersion.setId(42L);
        datasetVersion.setVersionState(DatasetVersion.VersionState.DRAFT);

        List<DatasetVersion> datasetVersions = new ArrayList<>();
        datasetVersions.add(datasetVersion);
        dataset.setVersions(datasetVersions);

        DataTable dataTable = new DataTable();
        dataTable.setUnf("unfOnDataTable");

        DataFile dataFile = new DataFile("application/octet-stream");
        dataFile.setDataTable(dataTable);

        FileMetadata fileMetadata = createFileMetadata(datasetVersion, dataFile, 1L, "datafile1.txt");

        dataFile.getFileMetadatas().add(fileMetadata);
        datasetVersion.getFileMetadatas().add(fileMetadata);

        //WHEN
        IngestUtil.recalculateDatasetVersionUNF(datasetVersion);

        //THEN
        assertAll(
                () -> assertEquals("UNF:6:rDlgOhoEkEQQdwtLRHjmtw==", datasetVersion.getUNF()),
                () -> assertTrue(dataFile.isTabularData())
        );
    }

    @Test
    public void getUnfValuesOfFiles_null_expectedEmptyList() {
        assertEquals(Collections.emptyList(), IngestUtil.getUnfValuesOfFiles(null));
    }

    @Test
    public void shouldHaveUnf_null_expectedFalse() {
        assertFalse(IngestUtil.shouldHaveUnf(null));
    }
}
