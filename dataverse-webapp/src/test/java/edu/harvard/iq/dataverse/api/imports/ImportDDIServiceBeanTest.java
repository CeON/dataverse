package edu.harvard.iq.dataverse.api.imports;

import com.google.gson.JsonElement;
import edu.harvard.iq.dataverse.api.dto.DatasetDTO;
import edu.harvard.iq.dataverse.common.DatasetFieldConstant;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class ImportDDIServiceBeanTest {

    private final ImportDDIServiceBean importDDIServiceBean = new ImportDDIServiceBean();

    @Test
    void isRelatedMaterial_And_Dataset_ParsedCorrectly() throws IOException, XMLStreamException, ImportException {
        //given
        final String ddiXml = IOUtils.toString(Objects.requireNonNull(HarvestedJsonParserIT.class
                                                                              .getClassLoader()
                                                                              .getResource("xml/imports/modernDdi.xml")), StandardCharsets.UTF_8);

        //when
        DatasetDTO datasetDTO = importDDIServiceBean.doImport(ImportUtil.ImportType.HARVEST, ddiXml);

        //then
        assertThat(extractJsonValues(datasetDTO, DatasetFieldConstant.relatedMaterial, DatasetFieldConstant.relatedMaterialCitation))
                .isNotNull();
        assertThat(extractJsonValues(datasetDTO, DatasetFieldConstant.relatedMaterial, DatasetFieldConstant.relatedMaterialIDType))
                .isNotNull();
        assertThat(extractJsonValues(datasetDTO, DatasetFieldConstant.relatedMaterial, DatasetFieldConstant.relatedMaterialIDNumber))
                .isNotNull();
        assertThat(extractJsonValues(datasetDTO, DatasetFieldConstant.relatedMaterial, DatasetFieldConstant.relatedMaterialURL))
                .isNotNull();

        assertThat(extractJsonValues(datasetDTO, DatasetFieldConstant.relatedDataset, DatasetFieldConstant.relatedDatasetCitation))
                .isNotNull();
        assertThat(extractJsonValues(datasetDTO, DatasetFieldConstant.relatedDataset, DatasetFieldConstant.relatedDatasetIDType))
                .isNotNull();
        assertThat(extractJsonValues(datasetDTO, DatasetFieldConstant.relatedDataset, DatasetFieldConstant.relatedDatasetIDNumber))
                .isNotNull();
        assertThat(extractJsonValues(datasetDTO, DatasetFieldConstant.relatedDataset, DatasetFieldConstant.relatedDatasetURL))
                .isNotNull();

    }

    private JsonElement extractJsonValues(DatasetDTO datasetDTO, String citationParentField, String childField) {
        return datasetDTO.getDatasetVersion().getMetadataBlocks().get("citation").getField(citationParentField)
                         .getValue().getAsJsonArray().get(0).getAsJsonObject().get(childField);
    }
}