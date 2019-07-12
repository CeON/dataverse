package edu.harvard.iq.dataverse;

import static org.junit.Assert.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import javax.json.Json;
import javax.json.JsonObject;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import edu.harvard.iq.dataverse.DatasetVersionDifference.DatasetFileDifferenceItem;
import edu.harvard.iq.dataverse.DatasetVersionDifference.DatasetReplaceFileItem;
import edu.harvard.iq.dataverse.mocks.MocksFactory;
import edu.harvard.iq.dataverse.util.json.JsonParseException;
import edu.harvard.iq.dataverse.util.json.JsonParser;
import io.vavr.Tuple2;
import io.vavr.Tuple4;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class DatasetVersionDifferenceTest {

    @Mock
    private DatasetFieldServiceBean datasetFieldService;
    
    
    @BeforeEach
    public void before() {
        MetadataBlock citationMetadataBlock = new MetadataBlock();
        citationMetadataBlock.setId(1L);
        citationMetadataBlock.setName("citation");
        citationMetadataBlock.setDisplayName("Citation Metadata");
        
        
        DatasetFieldType titleFieldType = MocksFactory.makeDatasetFieldType("title", FieldType.TEXT, false, citationMetadataBlock);
        
        DatasetFieldType authorNameFieldType = MocksFactory.makeDatasetFieldType("authorName", FieldType.TEXT, false, citationMetadataBlock);
        DatasetFieldType authorAffiliationFieldType = MocksFactory.makeDatasetFieldType("authorAffiliation", FieldType.TEXT, false, citationMetadataBlock);
        DatasetFieldType authorFieldType = MocksFactory.makeComplexDatasetFieldType("author", true, citationMetadataBlock,
                authorNameFieldType, authorAffiliationFieldType);
        authorFieldType.setDisplayOnCreate(true);
        
        DatasetFieldType datasetContactNameFieldType = MocksFactory.makeDatasetFieldType("datasetContactName", FieldType.TEXT, false, citationMetadataBlock);
        DatasetFieldType datasetContactAffiliationFieldType = MocksFactory.makeDatasetFieldType("datasetContactAffiliation", FieldType.TEXT, false, citationMetadataBlock);
        DatasetFieldType datasetContactEmailFieldType = MocksFactory.makeDatasetFieldType("datasetContactEmail", FieldType.TEXT, false, citationMetadataBlock);
        DatasetFieldType datasetContactFieldType = MocksFactory.makeComplexDatasetFieldType("datasetContact", true, citationMetadataBlock,
                datasetContactNameFieldType, datasetContactAffiliationFieldType, datasetContactEmailFieldType);
        
        
        DatasetFieldType dsDescriptionValueFieldType = MocksFactory.makeDatasetFieldType("dsDescriptionValue", FieldType.TEXT, false, citationMetadataBlock);
        DatasetFieldType dsDescriptionDateFieldType = MocksFactory.makeDatasetFieldType("dsDescriptionDate", FieldType.TEXT, false, citationMetadataBlock);
        DatasetFieldType dsDescriptionFieldType = MocksFactory.makeComplexDatasetFieldType("dsDescription", true, citationMetadataBlock,
                dsDescriptionValueFieldType, dsDescriptionDateFieldType);
        
        DatasetFieldType subjectFieldType = MocksFactory.makeControlledVocabDatasetFieldType("subject", true, citationMetadataBlock,
                "agricultural_sciences", "arts_and_humanities", "chemistry");
        DatasetFieldType depositorFieldType = MocksFactory.makeDatasetFieldType("depositor", FieldType.TEXT, false, citationMetadataBlock);
        DatasetFieldType dateOfDepositFieldType = MocksFactory.makeDatasetFieldType("dateOfDeposit", FieldType.TEXT, false, citationMetadataBlock);
        
        when(datasetFieldService.findByNameOpt(eq("title"))).thenReturn(titleFieldType);
        when(datasetFieldService.findByNameOpt(eq("author"))).thenReturn(authorFieldType);
        when(datasetFieldService.findByNameOpt(eq("authorName"))).thenReturn(authorNameFieldType);
        when(datasetFieldService.findByNameOpt(eq("authorAffiliation"))).thenReturn(authorAffiliationFieldType);
        when(datasetFieldService.findByNameOpt(eq("datasetContact"))).thenReturn(datasetContactFieldType);
        when(datasetFieldService.findByNameOpt(eq("datasetContactName"))).thenReturn(datasetContactNameFieldType);
        when(datasetFieldService.findByNameOpt(eq("datasetContactAffiliation"))).thenReturn(datasetContactAffiliationFieldType);
        when(datasetFieldService.findByNameOpt(eq("datasetContactEmail"))).thenReturn(datasetContactEmailFieldType);
        when(datasetFieldService.findByNameOpt(eq("dsDescription"))).thenReturn(dsDescriptionFieldType);
        when(datasetFieldService.findByNameOpt(eq("dsDescriptionValue"))).thenReturn(dsDescriptionValueFieldType);
        when(datasetFieldService.findByNameOpt(eq("dsDescriptionDate"))).thenReturn(dsDescriptionDateFieldType);
        when(datasetFieldService.findByNameOpt(eq("subject"))).thenReturn(subjectFieldType);
        when(datasetFieldService.findControlledVocabularyValueByDatasetFieldTypeAndStrValue(subjectFieldType, "Chemistry", false))
            .thenReturn(subjectFieldType.getControlledVocabularyValue("chemistry"));
        when(datasetFieldService.findControlledVocabularyValueByDatasetFieldTypeAndStrValue(subjectFieldType, "Agricultural Sciences", false))
            .thenReturn(subjectFieldType.getControlledVocabularyValue("agricultural_sciences"));
        when(datasetFieldService.findControlledVocabularyValueByDatasetFieldTypeAndStrValue(subjectFieldType, "Arts and Humanities", false))
        .thenReturn(subjectFieldType.getControlledVocabularyValue("arts_and_humanities"));
        
        when(datasetFieldService.findByNameOpt(eq("depositor"))).thenReturn(depositorFieldType);
        when(datasetFieldService.findByNameOpt(eq("dateOfDeposit"))).thenReturn(dateOfDepositFieldType);
        
        
    }
    
    
    // -------------------- TESTS --------------------
    
    @Test
    public void createTest() throws IOException, JsonParseException {

        // given
        
        Dataset dataset = MocksFactory.makeDataset();
        
        DataFile dataFile1 = MocksFactory.makeDataFile();
        dataFile1.getFileMetadatas().clear();
        DataFile dataFile2 = MocksFactory.makeDataFile();
        dataFile2.getFileMetadatas().clear();
        DataFile dataFile3 = MocksFactory.makeDataFile();
        dataFile3.getFileMetadatas().clear();

        DataFile dataFileToReplace = MocksFactory.makeDataFile();
        dataFile3.getFileMetadatas().clear();
        DataFile dataFileReplacement = MocksFactory.makeDataFile();
        dataFileReplacement.setPreviousDataFileId(dataFileToReplace.getId());
        dataFile3.getFileMetadatas().clear();
        
        
        DatasetVersion v1 = parseDatasetVersionFromClasspath("/json/complete-dataset-version.json");
        v1.setDataset(dataset);
        
        FileMetadata v1FileMetadata1 = MocksFactory.makeFileMetadata(10L, "firstFile.txt", 0);
        v1FileMetadata1.setDataFile(dataFile1);
        dataFile1.getFileMetadatas().add(v1FileMetadata1);
        
        FileMetadata v1FileMetadata2 = MocksFactory.makeFileMetadata(11L, "secondFile.txt", 1);
        v1FileMetadata2.setDataFile(dataFile2);
        dataFile2.getFileMetadatas().add(v1FileMetadata2);
        
        FileMetadata v1FileMetadata3 = MocksFactory.makeFileMetadata(12L, "toreplace.txt", 2);
        v1FileMetadata3.setDataFile(dataFileToReplace);
        dataFileToReplace.getFileMetadatas().add(v1FileMetadata3);
        
        v1.addFileMetadata(v1FileMetadata1);
        v1.addFileMetadata(v1FileMetadata2);
        v1.addFileMetadata(v1FileMetadata3);
        
        
        DatasetVersion v2 = parseDatasetVersionFromClasspath("/json/complete-dataset-version-with-changes.json");
        v2.setDataset(dataset);
        
        FileMetadata v2FileMetadata1 = MocksFactory.makeFileMetadata(21L, "secondFile (changed).txt", 1);
        v2FileMetadata1.setDataFile(dataFile2);
        dataFile2.getFileMetadatas().add(v2FileMetadata1);
        
        FileMetadata v2FileMetadata2 = MocksFactory.makeFileMetadata(22L, "newFile.txt", 2);
        v2FileMetadata2.setDataFile(dataFile3);
        dataFile3.getFileMetadatas().add(v2FileMetadata2);
        
        FileMetadata v2FileMetadata3 = MocksFactory.makeFileMetadata(23L, "replacementFile.txt", 3);
        v2FileMetadata3.setDataFile(dataFileReplacement);
        dataFileReplacement.getFileMetadatas().add(v2FileMetadata3);
        
        v2.addFileMetadata(v2FileMetadata1);
        v2.addFileMetadata(v2FileMetadata2);
        v2.addFileMetadata(v2FileMetadata3);
        
        
        // when
        
        DatasetVersionDifference diff = new DatasetVersionDifference(v2, v1);
        
        
        // then
        
        System.out.println(diff.getEditSummaryForLog());
        
        List<Tuple4<MetadataBlock, Integer, Integer, Integer>> blockDataForNote = diff.getBlockDataForNote();
        assertEquals(1, blockDataForNote.size());
        
        Tuple4<MetadataBlock, Integer, Integer, Integer> blockChange = blockDataForNote.get(0);
        assertEquals("citation", blockChange._1().getName());
        assertEquals(Integer.valueOf(1), blockChange._2()); // added
        assertEquals(Integer.valueOf(1), blockChange._3()); // removed
        assertEquals(Integer.valueOf(2), blockChange._4()); // changed
        
        List<Tuple4<DatasetFieldType, Integer, Integer, Integer>> summaryDataForNote = diff.getSummaryDataForNote();
        
        assertEquals(1, summaryDataForNote.size());
        Tuple4<DatasetFieldType, Integer, Integer, Integer> summaryForNote = summaryDataForNote.get(0);
        assertEquals("author", summaryForNote._1().getName());
        assertEquals(Integer.valueOf(1), summaryForNote._2());
        assertEquals(Integer.valueOf(0), summaryForNote._3());
        assertEquals(Integer.valueOf(1), summaryForNote._4());
        
        
        List<List<Tuple2<DatasetField, DatasetField>>> detailDataByBlock = diff.getDetailDataByBlock();
        assertEquals(1, detailDataByBlock.size());
        
        List<Tuple2<DatasetField, DatasetField>> detailData = detailDataByBlock.get(0);
        
        assertEquals(4, detailData.size());
        
        assertDatasetPrimitiveFieldChange(detailData.get(0), "title",
                "Sample-published-dataset (updated)",
                "Sample-published-dataset (updated2)");
        
        assertDatasetCompoundFieldChange(detailData.get(1), "author",
                "Kew, Susie; Creedence Clearwater Revival",
                "Kew, Susie (changed); Creedence Clearwater Revival; Doe, Joe");
        
        assertDatasetPrimitiveFieldChange(detailData.get(2), "subject",
                "chemistry", "agricultural_sciences; arts_and_humanities");
        
        assertDatasetCompoundFieldChange(detailData.get(3), "datasetContact",
                "Dataverse, Admin; Dataverse; admin@malinator.com", "");
        
        
        assertEquals(1, diff.getAddedFiles().size());
        assertSame(v2FileMetadata2, diff.getAddedFiles().get(0));
        
        assertEquals(1, diff.getChangedFileMetadata().size());
        assertSame(v1FileMetadata2, diff.getChangedFileMetadata().get(0)._1());
        assertSame(v2FileMetadata1, diff.getChangedFileMetadata().get(0)._2());
        
        assertEquals(3, diff.getDatasetFilesDiffList().size());
        
        DatasetFileDifferenceItem fileDifference1 = diff.getDatasetFilesDiffList().get(1);
        assertEquals("firstFile.txt", fileDifference1.getFileName1());
        assertNull(fileDifference1.getFileName2());
        
        DatasetFileDifferenceItem fileDifference2 = diff.getDatasetFilesDiffList().get(2);
        assertEquals("secondFile.txt", fileDifference2.getFileName1());
        assertEquals("secondFile (changed).txt", fileDifference2.getFileName2());
        
        DatasetFileDifferenceItem fileDifference3 = diff.getDatasetFilesDiffList().get(0);
        assertNull(fileDifference3.getFileName1());
        assertEquals("newFile.txt", fileDifference3.getFileName2());
        
        
        assertEquals(1, diff.getDatasetFilesReplacementList().size());
        DatasetReplaceFileItem dataFileDiffReplacement = diff.getDatasetFilesReplacementList().get(0);
        assertEquals(String.valueOf(dataFileToReplace.getId()), dataFileDiffReplacement.getFile1Id());
        assertEquals(String.valueOf(dataFileReplacement.getId()), dataFileDiffReplacement.getFile2Id());
        assertEquals("toreplace.txt", dataFileDiffReplacement.getFdi().getFileName1());
        assertEquals("replacementFile.txt", dataFileDiffReplacement.getFdi().getFileName2());
        
        assertEquals(1, diff.getRemovedFiles().size());
        assertSame(v1FileMetadata1, diff.getRemovedFiles().get(0));
        
        assertSame(v1, diff.getOriginalVersion());
        assertSame(v2, diff.getNewVersion());
    }
    
    
    // -------------------- PRIVATE --------------------
    
    private void assertDatasetPrimitiveFieldChange(Tuple2<DatasetField, DatasetField> actualFieldChange,
            String expectedFieldName, String expectedOldValue, String expectedNewValue) {
        
        assertEquals(expectedFieldName, actualFieldChange._1().getDatasetFieldType().getName());
        assertEquals(expectedFieldName, actualFieldChange._2().getDatasetFieldType().getName());
        
        assertEquals(expectedOldValue, actualFieldChange._1().getRawValue());
        assertEquals(expectedNewValue, actualFieldChange._2().getRawValue());
        
    }
    
    private void assertDatasetCompoundFieldChange(Tuple2<DatasetField, DatasetField> actualFieldChange,
            String expectedFieldName, String expectedOldValue, String expectedNewValue) {
        
        assertEquals(expectedFieldName, actualFieldChange._1().getDatasetFieldType().getName());
        assertEquals(expectedFieldName, actualFieldChange._2().getDatasetFieldType().getName());
        
        assertEquals(expectedOldValue, actualFieldChange._1().getCompoundRawValue());
        assertEquals(expectedNewValue, actualFieldChange._2().getCompoundRawValue());
        
    }
    
    private DatasetVersion parseDatasetVersionFromClasspath(String classpath) throws IOException, JsonParseException {
        
        try (ByteArrayInputStream is = new ByteArrayInputStream(IOUtils.resourceToByteArray(classpath))) {
            JsonObject jsonObject = Json.createReader(is).readObject();
            JsonParser jsonParser = new JsonParser(datasetFieldService, null, null);
            
            return jsonParser.parseDatasetVersion(jsonObject);
        }
    }
}
