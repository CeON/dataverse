package edu.harvard.iq.dataverse.export;

import edu.harvard.iq.dataverse.DatasetVersion;
import edu.harvard.iq.dataverse.export.spi.Exporter;
import edu.harvard.iq.dataverse.util.BundleUtil;
import edu.harvard.iq.dataverse.util.json.JsonPrinter;

import javax.json.JsonObjectBuilder;
import javax.ws.rs.core.MediaType;


/**
 * @author skraffmi
 */

public class JSONExporter implements Exporter {

    @Override
    public String getProviderName() {
        return "json";
    }

    @Override
    public String getDisplayName() {
        return BundleUtil.getStringFromBundle("dataset.exportBtn.itemLabel.json") != null ? BundleUtil.getStringFromBundle("dataset.exportBtn.itemLabel.json") : "JSON";
    }

    @Override
    public String exportDataset(DatasetVersion version, boolean excludeEmailFromExport) throws ExportException {
        try {
            JsonObjectBuilder jsonObjectBuilder = JsonPrinter.jsonAsDatasetDto(version, excludeEmailFromExport);

            return jsonObjectBuilder
                    .build()
                    .toString();

        } catch (Exception e) {
            throw new ExportException("Unknown exception caught during JSON export.");
        }
    }

    @Override
    public Boolean isXMLFormat() {
        return false;
    }

    @Override
    public Boolean isHarvestable() {
        return true;
    }

    @Override
    public Boolean isAvailableToUsers() {
        return true;
    }

    @Override
    public String getXMLNameSpace() throws ExportException {
        throw new ExportException("JSONExporter: not an XML format.");
    }

    @Override
    public String getXMLSchemaLocation() throws ExportException {
        throw new ExportException("JSONExporter: not an XML format.");
    }

    @Override
    public String getXMLSchemaVersion() throws ExportException {
        throw new ExportException("JSONExporter: not an XML format.");
    }

    @Override
    public void setParam(String name, Object value) {
        // this exporter doesn't need/doesn't currently take any parameters
    }

    @Override
    public String getMediaType() {
        return MediaType.APPLICATION_JSON;
    }

}
