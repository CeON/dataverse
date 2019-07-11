package edu.harvard.iq.dataverse.export;

import edu.harvard.iq.dataverse.export.spi.Exporter;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.MediaType;
import java.util.Map;

@SuppressWarnings("WeakerAccess")
public class ExportServiceTest {

    private ExportService exportService = new ExportService();

    @BeforeEach
    private void prepareData() {
        exportService.loadAllExporters();
    }

    @Test
    public void exportDatasetVersion() {

    }

    @Test
    public void exportDatasetVersionAsString() {
        //when
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
        String mediaType = exportService.getMediaType(ExporterConstant.DATA_CITE);

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
}