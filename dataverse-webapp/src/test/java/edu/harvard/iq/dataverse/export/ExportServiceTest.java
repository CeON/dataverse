package edu.harvard.iq.dataverse.export;

import com.google.common.collect.Lists;
import edu.harvard.iq.dataverse.ControlledVocabularyValue;
import edu.harvard.iq.dataverse.Dataset;
import edu.harvard.iq.dataverse.DatasetField;
import edu.harvard.iq.dataverse.DatasetFieldConstant;
import edu.harvard.iq.dataverse.DatasetFieldServiceBean;
import edu.harvard.iq.dataverse.DatasetFieldType;
import edu.harvard.iq.dataverse.DatasetFieldValue;
import edu.harvard.iq.dataverse.DatasetVersion;
import edu.harvard.iq.dataverse.Dataverse;
import edu.harvard.iq.dataverse.FieldType;
import edu.harvard.iq.dataverse.MetadataBlock;
import edu.harvard.iq.dataverse.error.DataverseError;
import edu.harvard.iq.dataverse.export.spi.Exporter;
import edu.harvard.iq.dataverse.mocks.MocksFactory;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import edu.harvard.iq.dataverse.util.json.JsonParseException;
import edu.harvard.iq.dataverse.util.json.JsonParser;
import io.vavr.control.Either;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.json.Json;
import javax.json.JsonObject;
import javax.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ExportServiceTest {

    //07/10/2019
    private final long DATE = 1562709600000L;

    private ExportService exportService;

    @Mock
    private SettingsServiceBean settingsService;

    @Mock
    private DatasetFieldServiceBean datasetFieldService;

    @BeforeEach
    void prepareData() {
        when(settingsService.isTrueForKey(SettingsServiceBean.Key.ExcludeEmailFromExport)).thenReturn(true);
        mockDatasetFields();

        exportService = new ExportService(settingsService);
        exportService.loadAllExporters();
    }


    @Test
    public void exportDatasetVersion() {

    }

    @Test
    @DisplayName("export DatasetVersion as string for datacite")
    public void exportDatasetVersionAsString_forDataCite() throws IOException, JsonParseException, URISyntaxException {
        //given
        DatasetVersion datasetVersion = parseDatasetVersionFromClasspath("json/testDataset.json");
        prepareDataForExport(datasetVersion);

        //when
        Either<DataverseError, String> exportedDataset =
                exportService.exportDatasetVersionAsString(datasetVersion, ExporterConstant.DATACITE, new Date());

        //then
        Assert.assertEquals(readFileToString("exportdata/testDatacite.xml"), exportedDataset.get());
    }

    @Test
    @DisplayName("export DatasetVersion as string for DCTerms")
    public void exportDatasetVersionAsString_forDCTerms() throws IOException, JsonParseException, URISyntaxException {
        //given
        DatasetVersion datasetVersion = parseDatasetVersionFromClasspath("json/testDataset.json");
        prepareDataForExport(datasetVersion);

        //when
        Either<DataverseError, String> exportedDataset =
                exportService.exportDatasetVersionAsString(datasetVersion, ExporterConstant.DCTERMS, new Date());

        //then
        Assert.assertEquals(readFileToString("exportdata/dcterms.xml"), exportedDataset.get());
    }

    @Test
    @DisplayName("export DatasetVersion as string for ddi")
    public void exportDatasetVersionAsString_forDdi() throws IOException, JsonParseException, URISyntaxException {
        //given
        DatasetVersion datasetVersion = parseDatasetVersionFromClasspath("json/testDataset.json");
        prepareDataForExport(datasetVersion);

        //when
        Either<DataverseError, String> exportedDataset =
                exportService.exportDatasetVersionAsString(datasetVersion, ExporterConstant.DDI, new Date());

        //then
        Assert.assertEquals(readFileToString("exportdata/ddi.xml"), exportedDataset.get());
    }

    @Test
    public void getAllExporters() {
        //when
        Map<ExporterConstant, Exporter> allExporters = exportService.getAllExporters();

        //then
        Assert.assertTrue(allExporters.size() > 0);
    }

    @Test
    @DisplayName("Get mediaType for dataCite exporter")
    public void getMediaType_forDataCite() {
        //when
        String mediaType = exportService.getMediaType(ExporterConstant.DATACITE);

        //then
        Assert.assertEquals(MediaType.APPLICATION_XML, mediaType);
    }

    @Test
    @DisplayName("Get mediaType for json exporter")
    public void getMediaType_forJsonExporter() {
        //when
        String mediaType = exportService.getMediaType(ExporterConstant.JSON);

        //then
        Assert.assertEquals(MediaType.APPLICATION_JSON, mediaType);
    }

    // -------------------- PRIVATE --------------------

    private DatasetVersion parseDatasetVersionFromClasspath(String classpath) throws IOException, JsonParseException {

        try (ByteArrayInputStream is = new ByteArrayInputStream(IOUtils.resourceToByteArray(classpath, getClass().getClassLoader()))) {

            JsonObject jsonObject = Json.createReader(is).readObject();
            JsonParser jsonParser = new JsonParser(datasetFieldService, null, null);

            return jsonParser.parseDatasetVersion(jsonObject);
        }
    }

    private DatasetVersion prepareDataForExport(DatasetVersion datasetVersion) {
        Dataset dataset = new Dataset();
        dataset.setId(5L);
        dataset.setIdentifier("FK2/05NAR1");
        dataset.setProtocol("doi");
        dataset.setAuthority("10.5072");

        Dataverse owner = new Dataverse();
        owner.setName("Root");
        dataset.setOwner(owner);
        dataset.setPublicationDate(new Timestamp(DATE));
        dataset.setStorageIdentifier("file://10.5072/FK2/05NAR1");
        dataset.setVersions(Lists.newArrayList(datasetVersion));

        datasetVersion.setDataset(dataset);

        prepareDatasetFieldValues(datasetVersion);

        return datasetVersion;
    }

    private void prepareDatasetFieldValues(DatasetVersion datasetVersion) {
        List<DatasetField> datasetFields = datasetVersion.getDatasetFields();

        DatasetFieldValue titleValue = new DatasetFieldValue();
        titleValue.setValue("Export test");
        titleValue.setId(3L);

        datasetFields.stream()
                .filter(datasetField -> datasetField.getDatasetFieldType().getName().equals(DatasetFieldConstant.title))
                .peek(titleValue::setDatasetField)
                .forEach(datasetField -> datasetField.setDatasetFieldValues(Lists.newArrayList(titleValue)));

        DatasetFieldValue subjectValue = new DatasetFieldValue();
        subjectValue.setValue("Agricultural Sciences");
        subjectValue.setId(3L);

        datasetFields.stream()
                .filter(datasetField -> datasetField.getDatasetFieldType().getName().equals(DatasetFieldConstant.subject))
                .peek(subjectValue::setDatasetField)
                .forEach(datasetField -> {
                    datasetField.setDatasetFieldValues(Lists.newArrayList(subjectValue));
                    datasetField.setSingleControlledVocabularyValue(
                            new ControlledVocabularyValue(13L, subjectValue.getValue(), datasetField.getDatasetFieldType()));
                });
    }

    private void mockDatasetFields() {
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

    private String readFileToString(String resourcePath) throws IOException, URISyntaxException {
        byte[] bytes = Files.readAllBytes(Paths.get(getClass().getClassLoader().getResource(resourcePath).toURI()));
        return new String(bytes);
    }
}