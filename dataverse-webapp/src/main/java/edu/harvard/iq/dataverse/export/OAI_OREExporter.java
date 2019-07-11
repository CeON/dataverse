package edu.harvard.iq.dataverse.export;

import edu.harvard.iq.dataverse.DatasetVersion;
import edu.harvard.iq.dataverse.export.spi.Exporter;
import edu.harvard.iq.dataverse.util.bagit.OREMap;
import org.apache.commons.lang.StringUtils;

import javax.ws.rs.core.MediaType;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ResourceBundle;
import java.util.logging.Logger;


public class OAI_OREExporter implements Exporter {

    private static final Logger logger = Logger.getLogger(OAI_OREExporter.class.getCanonicalName());

    public static final String NAME = "OAI_ORE";

    @Override
    public String exportDataset(DatasetVersion version, boolean excludeEmailFromExport) {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            new OREMap(version, excludeEmailFromExport)
                    .writeOREMap(byteArrayOutputStream);

            return byteArrayOutputStream.toString(StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            logger.severe(e.getMessage());
            e.printStackTrace();
        }
        return StringUtils.EMPTY;
    }

    @Override
    public String getProviderName() {
        return NAME;
    }

    @Override
    public String getDisplayName() {
        return ResourceBundle.getBundle("Bundle").getString("dataset.exportBtn.itemLabel.oai_ore");
    }

    @Override
    public Boolean isXMLFormat() {
        return false;
    }

    @Override
    public Boolean isHarvestable() {
        return false;
    }

    @Override
    public Boolean isAvailableToUsers() {
        return true;
    }

    @Override
    public String getXMLNameSpace() throws ExportException {
        throw new ExportException(OAI_OREExporter.class.getSimpleName() + ": not an XML format.");
    }

    @Override
    public String getXMLSchemaLocation() throws ExportException {
        throw new ExportException(OAI_OREExporter.class.getSimpleName() + ": not an XML format.");
    }

    @Override
    public String getXMLSchemaVersion() throws ExportException {
        throw new ExportException(SchemaDotOrgExporter.class.getSimpleName() + ": not an XML format.");
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
