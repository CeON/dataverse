package edu.harvard.iq.dataverse.export;

import com.google.common.collect.Lists;
import edu.harvard.iq.dataverse.DatasetFieldServiceBean;
import edu.harvard.iq.dataverse.UnitTestUtils;
import edu.harvard.iq.dataverse.api.dto.DatasetDTO;
import edu.harvard.iq.dataverse.api.dto.FieldDTO;
import edu.harvard.iq.dataverse.error.DataverseError;
import edu.harvard.iq.dataverse.export.ddi.DdiDatasetExportService;
import edu.harvard.iq.dataverse.persistence.MockMetadataFactory;
import edu.harvard.iq.dataverse.persistence.MocksFactory;
import edu.harvard.iq.dataverse.persistence.dataset.ControlledVocabularyValue;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetField;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetFieldType;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.dataset.FieldType;
import edu.harvard.iq.dataverse.persistence.dataset.MetadataBlock;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.qualifiers.TestBean;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean.Key;
import edu.harvard.iq.dataverse.util.SystemConfig;
import edu.harvard.iq.dataverse.util.json.JsonParseException;
import edu.harvard.iq.dataverse.util.json.JsonParser;
import edu.harvard.iq.dataverse.util.xml.XmlPrinter;
import io.vavr.control.Either;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.xml.stream.XMLStreamException;

import java.io.IOException;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.Year;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DDIExporterTest {

    @InjectMocks
    private DDIExporter ddiExporter;
    
    @Mock
    private DdiDatasetExportService ddiDatasetExportService;

    @Mock
    private SettingsServiceBean settingsService;
    
    @Mock
    private VocabularyValuesIndexer vocabularyValuesIndexer;
    
    @Captor
    private ArgumentCaptor<DatasetDTO> datasetDtoCaptor;
//    MockDatasetFieldSvc datasetFieldTypeSvc = null;

    @BeforeEach
    public void setUp() {
//        datasetFieldTypeSvc = new MockDatasetFieldSvc();

//        DatasetFieldType titleType = datasetFieldTypeSvc.add(new DatasetFieldType("title", FieldType.TEXTBOX, false));
//        DatasetFieldType authorType = datasetFieldTypeSvc.add(new DatasetFieldType("author", FieldType.TEXT, true));
//        Set<DatasetFieldType> authorChildTypes = new HashSet<>();
//        authorChildTypes.add(datasetFieldTypeSvc.add(new DatasetFieldType("authorName", FieldType.TEXT, false)));
//        authorChildTypes.add(datasetFieldTypeSvc.add(new DatasetFieldType("authorAffiliation", FieldType.TEXT, false)));
//        authorChildTypes.add(datasetFieldTypeSvc.add(new DatasetFieldType("authorIdentifier", FieldType.TEXT, false)));
//        DatasetFieldType authorIdentifierSchemeType = datasetFieldTypeSvc.add(new DatasetFieldType("authorIdentifierScheme", FieldType.TEXT, false));
//        authorIdentifierSchemeType.setAllowControlledVocabulary(true);
//        authorIdentifierSchemeType.setControlledVocabularyValues(Arrays.asList(
//                // Why aren't these enforced? Should be ORCID, etc.
//                new ControlledVocabularyValue(1l, "ark", authorIdentifierSchemeType),
//                new ControlledVocabularyValue(2l, "doi", authorIdentifierSchemeType),
//                new ControlledVocabularyValue(3l, "url", authorIdentifierSchemeType)
//        ));
//        authorChildTypes.add(datasetFieldTypeSvc.add(authorIdentifierSchemeType));
//        for (DatasetFieldType t : authorChildTypes) {
//            t.setParentDatasetFieldType(authorType);
//        }
//        authorType.setChildDatasetFieldTypes(authorChildTypes);
//
//        DatasetFieldType datasetContactType = datasetFieldTypeSvc.add(new DatasetFieldType("datasetContact", FieldType.TEXT, true));
//        Set<DatasetFieldType> datasetContactTypes = new HashSet<>();
//        datasetContactTypes.add(datasetFieldTypeSvc.add(new DatasetFieldType("datasetContactEmail", FieldType.TEXT, false)));
//        datasetContactTypes.add(datasetFieldTypeSvc.add(new DatasetFieldType("datasetContactName", FieldType.TEXT, false)));
//        datasetContactTypes.add(datasetFieldTypeSvc.add(new DatasetFieldType("datasetContactAffiliation", FieldType.TEXT, false)));
//        for (DatasetFieldType t : datasetContactTypes) {
//            t.setParentDatasetFieldType(datasetContactType);
//        }
//        datasetContactType.setChildDatasetFieldTypes(datasetContactTypes);
//
//        DatasetFieldType dsDescriptionType = datasetFieldTypeSvc.add(new DatasetFieldType("dsDescription", FieldType.TEXT, true));
//        Set<DatasetFieldType> dsDescriptionTypes = new HashSet<>();
//        dsDescriptionTypes.add(datasetFieldTypeSvc.add(new DatasetFieldType("dsDescriptionValue", FieldType.TEXT, false)));
//        for (DatasetFieldType t : dsDescriptionTypes) {
//            t.setParentDatasetFieldType(dsDescriptionType);
//        }
//        dsDescriptionType.setChildDatasetFieldTypes(dsDescriptionTypes);

//        DatasetFieldType keywordType = datasetFieldTypeSvc.add(new DatasetFieldType("keyword", FieldType.TEXT, true));
//        DatasetFieldType descriptionType = datasetFieldTypeSvc.add(new DatasetFieldType("description", FieldType.TEXTBOX, false));

//        DatasetFieldType subjectType = datasetFieldTypeSvc.add(new DatasetFieldType("subject", FieldType.TEXT, true));
//        subjectType.setAllowControlledVocabulary(true);
//        subjectType.setControlledVocabularyValues(Arrays.asList(
//                new ControlledVocabularyValue(1l, "mgmt", subjectType),
//                new ControlledVocabularyValue(2l, "law", subjectType),
//                new ControlledVocabularyValue(3l, "cs", subjectType)
//        ));
//
//        DatasetFieldType pubIdType = datasetFieldTypeSvc.add(new DatasetFieldType("publicationIdType", FieldType.TEXT, false));
//        pubIdType.setAllowControlledVocabulary(true);
//        pubIdType.setControlledVocabularyValues(Arrays.asList(
//                new ControlledVocabularyValue(1l, "ark", pubIdType),
//                new ControlledVocabularyValue(2l, "doi", pubIdType),
//                new ControlledVocabularyValue(3l, "url", pubIdType)
//        ));
//
//        DatasetFieldType compoundSingleType = datasetFieldTypeSvc.add(new DatasetFieldType("coordinate", FieldType.TEXT, true));
//        Set<DatasetFieldType> childTypes = new HashSet<>();
//        childTypes.add(datasetFieldTypeSvc.add(new DatasetFieldType("lat", FieldType.TEXT, false)));
//        childTypes.add(datasetFieldTypeSvc.add(new DatasetFieldType("lon", FieldType.TEXT, false)));
//
//        for (DatasetFieldType t : childTypes) {
//            t.setParentDatasetFieldType(compoundSingleType);
//        }
//        compoundSingleType.setChildDatasetFieldTypes(childTypes);
    }

    /*
{
  "id": 1,
  "storageIdentifier": "file://10.5072/FK2/05NAR1",
  "versionNumber": 1,
  "versionMinorNumber": 0,
  "versionState": "RELEASED",
  "productionDate": "Production Date",
  "lastUpdateTime": "2019-07-10T13:51:01Z",
  "releaseTime": "2019-07-10T13:51:01Z",
  "createTime": "2019-07-10T13:24:13Z",
  "license": "CC0",
  "termsOfUse": "CC0 Waiver",
  "fileAccessRequest": false,
}

     */

    @Test
    @DisplayName("Should pass email type field to service responsible for export")
    public void exportDataset_without_excluding_emails() throws IOException, ExportException, XMLStreamException {
        //given
        Dataset dataset = createDataset();
        DatasetVersion datasetVersion = dataset.getLatestVersion();
        
        MetadataBlock citationBlock = MockMetadataFactory.makeCitationMetadataBlock();
        
        DatasetFieldType titleType = MockMetadataFactory.makeTitleFieldType(citationBlock);
        DatasetField titleField = DatasetField.createNewEmptyDatasetField(titleType, datasetVersion);
        MockMetadataFactory.fillTitle(titleField, "Export test");
        
        DatasetFieldType emailType = MocksFactory.makeDatasetFieldType("email", FieldType.EMAIL, false, citationBlock);
        DatasetField emailField = DatasetField.createNewEmptyDatasetField(emailType, datasetVersion);
        emailField.setValue("example@domain.com");
        
        datasetVersion.setDatasetFields(Lists.newArrayList(titleField, emailField));
        
        when(settingsService.isTrueForKey(Key.ExcludeEmailFromExport)).thenReturn(false);
        
        
        //when
        ddiExporter.exportDataset(datasetVersion);

        //then
        verify(ddiDatasetExportService).datasetJson2ddi(datasetDtoCaptor.capture(), same(datasetVersion), any(), any());
        DatasetDTO datasetDTO = datasetDtoCaptor.getValue();
        
        assertThat(extractDatasetField(datasetDTO, "email")).isPresent();
        FieldDTO capturedEmailField = extractDatasetField(datasetDTO, "email").get();
        assertThat(capturedEmailField.getSinglePrimitive()).isEqualTo("example@domain.com");
        
    }
    
    @Test
    @DisplayName("Should not pass email type field to service responsible for export")
    public void exportDataset_with_excluding_emails() throws IOException, ExportException, XMLStreamException {
        //given
        Dataset dataset = createDataset();
        DatasetVersion datasetVersion = dataset.getLatestVersion();

        MetadataBlock citationBlock = MockMetadataFactory.makeCitationMetadataBlock();
        
        DatasetFieldType titleType = MockMetadataFactory.makeTitleFieldType(citationBlock);
        DatasetField titleField = DatasetField.createNewEmptyDatasetField(titleType, datasetVersion);
        MockMetadataFactory.fillTitle(titleField, "Export test");

        DatasetFieldType emailType = MocksFactory.makeDatasetFieldType("email", FieldType.EMAIL, false, citationBlock);
        DatasetField emailField = DatasetField.createNewEmptyDatasetField(emailType, datasetVersion);
        emailField.setValue("example@domain.com");

        datasetVersion.setDatasetFields(Lists.newArrayList(titleField, emailField));
        
        when(settingsService.isTrueForKey(Key.ExcludeEmailFromExport)).thenReturn(true);
        
        //when
        ddiExporter.exportDataset(datasetVersion);

        //then
        verify(ddiDatasetExportService).datasetJson2ddi(datasetDtoCaptor.capture(), same(datasetVersion), any(), any());
        DatasetDTO datasetDTO = datasetDtoCaptor.getValue();
        
        assertThat(extractDatasetField(datasetDTO, "email")).isNotPresent();
        
    }

    private Optional<FieldDTO> extractDatasetField(DatasetDTO dataset, String fieldTypeName) {
        return dataset.getDatasetVersion().getMetadataBlocks().values().stream()
                .flatMap(block -> block.getFields().stream())
                .filter(field -> field.getTypeName().equals(fieldTypeName))
                .findFirst();
    }
    
    private Dataset createDataset() {
        Dataset dataset = MocksFactory.makeDataset();
        dataset.getFiles().clear();
        dataset.setProtocol("doi");
        dataset.setAuthority("10.1012");
        dataset.setIdentifier("abc");
        dataset.setPublicationDate(Timestamp.from(Instant.now()));
        DatasetVersion datasetVersion = dataset.getLatestVersion();
        datasetVersion.getFileMetadatas().clear();
        return dataset;
    }
//    @Test
//    @DisplayName("export DatasetVersion as string for ddi without email")
//    public void exportDatasetVersionAsString_forDdiWithoutEmail() throws IOException, JsonParseException {
//        //given
//        enableExcludingEmails();
//
//        DatasetVersion datasetVersion = parseDatasetVersionFromClasspath("json/testDataset.json");
//        prepareDataForExport(datasetVersion);
//
//        //when
//        Either<DataverseError, String> exportedDataset =
//                exportService.exportDatasetVersionAsString(datasetVersion, ExporterType.DDI);
//
//        //then
//        Assert.assertEquals(UnitTestUtils.readFileToString("exportdata/ddiWithoutEmail.xml"), exportedDataset.get());
//    }

}
