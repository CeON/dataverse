package edu.harvard.iq.dataverse.export;

import edu.harvard.iq.dataverse.DatasetVersion;
import edu.harvard.iq.dataverse.export.dublincore.DublinCoreExportUtil;
import edu.harvard.iq.dataverse.export.spi.Exporter;
import edu.harvard.iq.dataverse.util.BundleUtil;
import edu.harvard.iq.dataverse.util.json.JsonPrinter;

import javax.json.JsonObject;
import javax.xml.stream.XMLStreamException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;


public class DublinCoreExporter implements Exporter {

    private boolean excludeEmailFromExport;

    // -------------------- CONSTRUCTORS --------------------

    public DublinCoreExporter(boolean excludeEmailFromExport) {
        this.excludeEmailFromExport = excludeEmailFromExport;
    }

    // -------------------- LOGIC --------------------


    @Override
    public String getProviderName() {
        return "dublinCore";
    }

    @Override
    public String getDisplayName() {
        return BundleUtil.getStringFromBundle("dataset.exportBtn.itemLabel.dublinCore") != null ? BundleUtil.getStringFromBundle("dataset.exportBtn.itemLabel.dublinCore") : "Dublin Core";
    }

    @Override
    public String exportDataset(DatasetVersion version) throws ExportException {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            JsonObject datasetAsJson = JsonPrinter.jsonAsDatasetDto(version, excludeEmailFromExport)
                    .build();

            DublinCoreExportUtil.datasetJson2dublincore(datasetAsJson, byteArrayOutputStream, DublinCoreExportUtil.DC_FLAVOR_OAI);
            return byteArrayOutputStream.toString(StandardCharsets.UTF_8.name());
        } catch (XMLStreamException | IOException xse) {
            throw new ExportException("Caught XMLStreamException performing DC export");
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
    public String getXMLNameSpace() {
        return DublinCoreExportUtil.OAI_DC_XML_NAMESPACE;
    }

    @Override
    public String getXMLSchemaLocation() {
        return DublinCoreExportUtil.OAI_DC_XML_SCHEMALOCATION;
    }

    @Override
    public String getXMLSchemaVersion() {
        return DublinCoreExportUtil.DEFAULT_XML_VERSION;
    }

    @Override
    public void setParam(String name, Object value) {
        // this exporter doesn't need/doesn't currently take any parameters
    }
}
