package edu.harvard.iq.dataverse.export;

import edu.harvard.iq.dataverse.export.spi.Exporter;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class ExportServiceTest {

    private ExportService exportService = new ExportService();

    @Test
    public void exportDatasetVersion() {
    }

    @Test
    public void exportDatasetVersionAsString() {

    }

    @Test
    public void getAllExporters() {
        //when
        Map<ExporterConstant, Exporter> allExporters = exportService.getAllExporters();

        //then
        Assert.assertTrue(allExporters.size() > 0);
    }

    @Test
    public void getMediaType() {
    }
}