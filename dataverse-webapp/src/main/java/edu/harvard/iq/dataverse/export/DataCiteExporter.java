package edu.harvard.iq.dataverse.export;

import edu.harvard.iq.dataverse.DOIDataCiteRegisterService;
import edu.harvard.iq.dataverse.common.BundleUtil;
import edu.harvard.iq.dataverse.export.spi.Exporter;
import edu.harvard.iq.dataverse.persistence.dataset.DataCitation;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;

import javax.enterprise.context.ApplicationScoped;

import java.util.Map;

/**
 * @author qqmyers
 */
@ApplicationScoped
public class DataCiteExporter implements Exporter {

    private static String DEFAULT_XML_NAMESPACE = "http://datacite.org/schema/kernel-3";
    private static String DEFAULT_XML_SCHEMALOCATION = "http://datacite.org/schema/kernel-3 http://schema.datacite.org/meta/kernel-3/metadata.xsd";
    private static String DEFAULT_XML_VERSION = "3.0";

    @Override
    public String getProviderName() {
        return ExporterType.DATACITE.getPrefix();
    }

    @Override
    public ExporterType getExporterType() {
        return ExporterType.DATACITE;
    }

    @Override
    public String getDisplayName() {
        return BundleUtil.getStringFromBundle("dataset.exportBtn.itemLabel.datacite") != null
                ? BundleUtil.getStringFromBundle("dataset.exportBtn.itemLabel.datacite")
                : "DataCite";
    }

    @Override
    public String exportDataset(DatasetVersion version) {
        Map<String, String> metadata = new DataCitation(version).getDataCiteMetadata();

        return DOIDataCiteRegisterService.getMetadataFromDvObject(
                version.getDataset().getGlobalId().asString(), metadata, version.getDataset());
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
        return true;
    }

    @Override
    public String getXMLNameSpace() {
        return DataCiteExporter.DEFAULT_XML_NAMESPACE;
    }

    @Override
    public String getXMLSchemaLocation() {
        return DataCiteExporter.DEFAULT_XML_SCHEMALOCATION;
    }

    @Override
    public String getXMLSchemaVersion() {
        return DataCiteExporter.DEFAULT_XML_VERSION;
    }

}
