package edu.harvard.iq.dataverse.export;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import edu.harvard.iq.dataverse.citation.CitationFactory;
import edu.harvard.iq.dataverse.common.BundleUtil;
import edu.harvard.iq.dataverse.export.datacite.ResourceDTOCreator;
import edu.harvard.iq.dataverse.export.spi.Exporter;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Map;

/**
 * @author qqmyers
 */
@ApplicationScoped
public class DataCiteExporter implements Exporter {

    private static String DEFAULT_XML_NAMESPACE = "http://datacite.org/schema/kernel-3";
    private static String DEFAULT_XML_SCHEMALOCATION = "http://datacite.org/schema/kernel-3 http://schema.datacite.org/meta/kernel-3/metadata.xsd";
    private static String DEFAULT_XML_VERSION = "3.0";

    private final CitationFactory citationFactory;

    // -------------------- CONSTRUCTORS --------------------

    @Inject
    public DataCiteExporter(CitationFactory citationFactory) {
        this.citationFactory = citationFactory;
    }

    // -------------------- LOGIC --------------------

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
        Map<String, String> metadata = citationFactory.create(version)
                .getCitationData()
                .getDataCiteMetadata();
        try {
            XmlMapper mapper = new XmlMapper();
            mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
            return mapper.writeValueAsString(new ResourceDTOCreator()
                    .create(version.getDataset().getGlobalId().asString(), metadata, version.getDataset()));
        } catch (JsonProcessingException jpe) {
            throw new RuntimeException(jpe);
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
