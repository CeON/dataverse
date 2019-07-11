package edu.harvard.iq.dataverse.export;

import edu.harvard.iq.dataverse.DatasetVersion;
import edu.harvard.iq.dataverse.export.ddi.DdiExportUtil;
import edu.harvard.iq.dataverse.export.spi.Exporter;
import edu.harvard.iq.dataverse.util.BundleUtil;
import edu.harvard.iq.dataverse.util.json.JsonPrinter;

import javax.json.JsonObject;
import javax.xml.stream.XMLStreamException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * @author skraffmi
 */

/**
 * This exporter is for the OAI ("short") flavor of the DDI -
 * that is, without the variable/data information. The ddi export
 * utility does not need the version entity to produce that.
 */

public class OAI_DDIExporter implements Exporter {

    private static String DEFAULT_XML_NAMESPACE = "ddi:codebook:2_5";
    private static String DEFAULT_XML_SCHEMALOCATION = "http://www.ddialliance.org/Specification/DDI-Codebook/2.5/XMLSchema/codebook.xsd";
    private static String DEFAULT_XML_VERSION = "2.5";

    @Override
    public String getProviderName() {
        return "oai_ddi";
    }

    @Override
    public String getDisplayName() {
        return BundleUtil.getStringFromBundle("dataset.exportBtn.itemLabel.ddi") != null ? BundleUtil.getStringFromBundle("dataset.exportBtn.itemLabel.ddi") : "DDI";
    }

    @Override
    public String exportDataset(DatasetVersion version, boolean excludeEmailFromExport) throws ExportException {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            JsonObject datasetAsJson = JsonPrinter.jsonAsDatasetDto(version, excludeEmailFromExport)
                    .build();

            DdiExportUtil.datasetJson2ddi(datasetAsJson, byteArrayOutputStream);
            return byteArrayOutputStream.toString(StandardCharsets.UTF_8.name());
        } catch (XMLStreamException | IOException xse) {
            throw new ExportException("Caught XMLStreamException performing DDI export");
        }
    }

    @Override
    public Boolean isXMLFormat() {
        return true;
    }

    @Override
    public Boolean isHarvestable() {
        return true;
    }

    @Override
    public Boolean isAvailableToUsers() {
        return false;
    }

    @Override
    public String getXMLNameSpace() throws ExportException {
        return OAI_DDIExporter.DEFAULT_XML_NAMESPACE;
    }

    @Override
    public String getXMLSchemaLocation() throws ExportException {
        return OAI_DDIExporter.DEFAULT_XML_SCHEMALOCATION;
    }

    @Override
    public String getXMLSchemaVersion() throws ExportException {
        return OAI_DDIExporter.DEFAULT_XML_VERSION;
    }

    @Override
    public void setParam(String name, Object value) {
        // this exporter does not uses or supports any parameters as of now.
    }
}
