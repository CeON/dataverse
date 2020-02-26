package edu.harvard.iq.dataverse.persistence.dataset;

import edu.harvard.iq.dataverse.persistence.MocksFactory;
import edu.harvard.iq.dataverse.persistence.datafile.FileMetadata;
import org.junit.Test;

import javax.validation.ConstraintViolation;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.google.common.collect.Lists.newArrayList;
import static edu.harvard.iq.dataverse.persistence.MocksFactory.makeFileMetadata;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author michael
 * @author tjanek
 */
public class DatasetVersionTest {

    @Test
    public void testComparator() {
        DatasetVersion ds1_0 = new DatasetVersion();
        ds1_0.setId(0l);
        ds1_0.setVersionNumber(1l);
        ds1_0.setMinorVersionNumber(0l);
        ds1_0.setVersionState(DatasetVersion.VersionState.RELEASED);

        DatasetVersion ds1_1 = new DatasetVersion();
        ds1_1.setId(1l);
        ds1_1.setVersionNumber(1l);
        ds1_1.setMinorVersionNumber(1l);
        ds1_1.setVersionState(DatasetVersion.VersionState.RELEASED);

        DatasetVersion ds2_0 = new DatasetVersion();
        ds2_0.setId(2l);
        ds2_0.setVersionNumber(2l);
        ds2_0.setMinorVersionNumber(0l);
        ds2_0.setVersionState(DatasetVersion.VersionState.RELEASED);

        DatasetVersion ds_draft = new DatasetVersion();
        ds_draft.setId(3l);
        ds_draft.setVersionState(DatasetVersion.VersionState.DRAFT);

        List<DatasetVersion> expected = Arrays.asList(ds1_0, ds1_1, ds2_0, ds_draft);
        List<DatasetVersion> actual = Arrays.asList(ds2_0, ds1_0, ds_draft, ds1_1);
        Collections.sort(actual, DatasetVersion.compareByVersion);
        assertEquals(expected, actual);
    }

    @Test
    public void testIsInReview() {
        Dataset ds = MocksFactory.makeDataset();

        DatasetVersion draft = ds.getLatestVersion();
        draft.setVersionState(DatasetVersion.VersionState.DRAFT);
        ds.addLock(new DatasetLock(DatasetLock.Reason.InReview, MocksFactory.makeAuthenticatedUser("Lauren", "Ipsumowitch")));
        assertTrue(draft.isInReview());

        DatasetVersion nonDraft = new DatasetVersion();
        nonDraft.setVersionState(DatasetVersion.VersionState.RELEASED);
        assertEquals(false, nonDraft.isInReview());

        ds.addLock(null);
        assertFalse(nonDraft.isInReview());
    }

    @Test
    public void testFilteringOutEmbargoedFilesMetadata() {
        // given
        Dataset ds = MocksFactory.makeDataset();
        ds.setEmbargoDate(Date.from(Instant.now().plus(1, ChronoUnit.DAYS)));

        DatasetVersion datasetVersion = ds.getLatestVersion();
        datasetVersion.setVersionState(DatasetVersion.VersionState.RELEASED);

        // when
        List<FileMetadata> fileMetadata = datasetVersion.getOnlyFilesMetadataNotUnderEmbargoSorted();

        // then
        assertTrue(fileMetadata.isEmpty());
    }

    @Test
    public void shouldSortFileMetadataByDisplayOrder() {
        // given
        DatasetVersion version = withUnSortedFiles();

        // when
        List<FileMetadata> orderedMetadatas = version.getAllFilesMetadataSorted();

        // then
        verifySortOrder(orderedMetadatas, "file4.png", 0);
        verifySortOrder(orderedMetadatas, "file3.png", 1);
        verifySortOrder(orderedMetadatas, "file5.png", 2);
        verifySortOrder(orderedMetadatas, "file2.png", 3);
        verifySortOrder(orderedMetadatas, "file6.png", 4);
        verifySortOrder(orderedMetadatas, "file1.png", 5);
    }

    @Test
    public void shouldAddNewFileMetadataWithProperDisplayOrder() {
        // given
        DatasetVersion version = withFilesAndCustomDisplayOrder();
        FileMetadata toAdd = makeFileMetadata(40L, "file4.png", 0);

        // when
        version.addFileMetadata(toAdd);

        // then
        verifyDisplayOrder(version.getFileMetadatas(), 0, "file1.png", 1);
        verifyDisplayOrder(version.getFileMetadatas(), 1, "file2.png", 6);
        verifyDisplayOrder(version.getFileMetadatas(), 2, "file3.png", 8);
        verifyDisplayOrder(version.getFileMetadatas(), 3, "file4.png", 9);
    }

    @Test
    public void shouldAddNewFileMetadataOnEmptyMetadatasWithZeroIndex() {
        // given
        DatasetVersion version = new DatasetVersion();
        FileMetadata toAdd = makeFileMetadata(40L, "file1.png", -5); // fake -5 displayOrder

        // when
        version.addFileMetadata(toAdd);

        // then
        verifyDisplayOrder(version.getFileMetadatas(), 0, "file1.png", 0);
    }

    private void verifySortOrder(List<FileMetadata> metadatas, String label, int expectedOrderIndex) {
        assertEquals(label, metadatas.get(expectedOrderIndex).getLabel());
    }

    private void verifyDisplayOrder(List<FileMetadata> metadatas, int index, String label, int displayOrder) {
        assertEquals(label, metadatas.get(index).getLabel());
        assertEquals(displayOrder, metadatas.get(index).getDisplayOrder());
    }

    private DatasetVersion withUnSortedFiles() {
        DatasetVersion datasetVersion = new DatasetVersion();

        datasetVersion.setFileMetadatas(newArrayList(
                makeFileMetadata(10L, "file2.png", 3),
                makeFileMetadata(20L, "file1.png", 5),
                makeFileMetadata(30L, "file3.png", 1),
                makeFileMetadata(40L, "file4.png", 0),
                makeFileMetadata(50L, "file5.png", 2),
                makeFileMetadata(60L, "file6.png", 4)
        ));

        return datasetVersion;
    }

    private DatasetVersion withFilesAndCustomDisplayOrder() {
        DatasetVersion datasetVersion = new DatasetVersion();

        datasetVersion.setFileMetadatas(newArrayList(
                makeFileMetadata(10L, "file1.png", 1),
                makeFileMetadata(20L, "file2.png", 6),
                makeFileMetadata(30L, "file3.png", 8)
        ));

        return datasetVersion;
    }

    @Test
    public void validate() {

        DatasetVersion datasetVersion = new DatasetVersion();
        FileMetadata fileMetadata = new FileMetadata();
        fileMetadata.setLabel("foo.png");
        fileMetadata.setDirectoryLabel("/has/leading/slash");
        datasetVersion.getFileMetadatas().add(fileMetadata);

        Set<ConstraintViolation> violations1 = datasetVersion.validate();
        assertEquals(1, violations1.size());
        ConstraintViolation violation1 = violations1.iterator().next();
        assertEquals("Directory Name cannot contain leading or trailing file separators.", violation1.getMessage());

        // reset
        datasetVersion.setFileMetadatas(new ArrayList<>());
        Set<ConstraintViolation> violations2 = datasetVersion.validate();
        assertEquals(0, violations2.size());

        fileMetadata.setDirectoryLabel("has/trailing/slash/");
        datasetVersion.getFileMetadatas().add(fileMetadata);
        Set<ConstraintViolation> violations3 = datasetVersion.validate();
        assertEquals(1, violations3.size());
        assertEquals("Directory Name cannot contain leading or trailing file separators.", violations3.iterator().next().getMessage());

        // reset
        datasetVersion.setFileMetadatas(new ArrayList<>());
        Set<ConstraintViolation> violations4 = datasetVersion.validate();
        assertEquals(0, violations4.size());

        fileMetadata.setDirectoryLabel("just/right");
        datasetVersion.getFileMetadatas().add(fileMetadata);
        Set<ConstraintViolation> violations5 = datasetVersion.validate();
        assertEquals(0, violations5.size());

        // reset
        datasetVersion.setFileMetadatas(new ArrayList<>());
        Set<ConstraintViolation> violations6 = datasetVersion.validate();
        assertEquals(0, violations6.size());

        fileMetadata.setDirectoryLabel("");
        datasetVersion.getFileMetadatas().add(fileMetadata);
        Set<ConstraintViolation> violations7 = datasetVersion.validate();
        assertEquals(0, violations7.size());

        // reset
        datasetVersion.setFileMetadatas(new ArrayList<>());
        Set<ConstraintViolation> violations8 = datasetVersion.validate();
        assertEquals(0, violations8.size());

        fileMetadata.setDirectoryLabel(null);
        datasetVersion.getFileMetadatas().add(fileMetadata);
        Set<ConstraintViolation> violations9 = datasetVersion.validate();
        assertEquals(0, violations9.size());

        // reset
        datasetVersion.setFileMetadatas(new ArrayList<>());
        Set<ConstraintViolation> violations10 = datasetVersion.validate();
        assertEquals(0, violations10.size());

        String singleCharacter = "a";
        fileMetadata.setDirectoryLabel(singleCharacter);
        datasetVersion.getFileMetadatas().add(fileMetadata);
        Set<ConstraintViolation> violations11 = datasetVersion.validate();
        assertEquals(0, violations11.size());

        // reset
        datasetVersion.setFileMetadatas(new ArrayList<>());
        Set<ConstraintViolation> violations12 = datasetVersion.validate();
        assertEquals(0, violations12.size());

        fileMetadata.setDirectoryLabel("/leadingAndTrailing/");
        datasetVersion.getFileMetadatas().add(fileMetadata);
        Set<ConstraintViolation> violations13 = datasetVersion.validate();
        assertEquals(1, violations13.size());

    }

}
