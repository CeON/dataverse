package edu.harvard.iq.dataverse.export;

import edu.harvard.iq.dataverse.DatasetVersion;
import edu.harvard.iq.dataverse.error.DataverseError;
import edu.harvard.iq.dataverse.export.spi.Exporter;
import io.vavr.control.Either;
import io.vavr.control.Try;
import org.apache.commons.lang.StringUtils;

import javax.annotation.PostConstruct;
import javax.ejb.Stateful;
import javax.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;


/**
 * Class responsible for managing exporters and mainly exporting.
 */
@Stateful
public class ExportService {

    private static final Logger logger = Logger.getLogger(ExportService.class.getCanonicalName());

    private Map<ExporterConstant, Exporter> exporters = new HashMap<>();

    // -------------------- CONSTRUCTORS --------------------

    @PostConstruct
    private void loadAllExporters() {
        exporters.put(ExporterConstant.DDI, new DDIExporter());

        exporters.put(ExporterConstant.DATA_CITE, new DataCiteExporter());

        exporters.put(ExporterConstant.DC_TERMS, new DCTermsExporter());

        exporters.put(ExporterConstant.DUBLIN_CORE, new DublinCoreExporter());

        exporters.put(ExporterConstant.OAI_DDI, new OAI_DDIExporter());

        exporters.put(ExporterConstant.OAI_OREE, new OAI_OREExporter());

        exporters.put(ExporterConstant.SCHEMA_DOT_ORG, new SchemaDotOrgExporter());

        exporters.put(ExporterConstant.JSON, new JSONExporter());
    }

    // -------------------- LOGIC --------------------

    /**
     * Exports datasetVersion with given exporter.
     *
     * @return {@code Error} if exporting failed or exporter was not found in the list of exporters.
     * <p>
     * {@code InputStream} if exporting was an success.
     */
    public Either<DataverseError, InputStream> exportDatasetVersion(DatasetVersion datasetVersion, ExporterConstant exporter, Date exportTime) {
        Either<DataverseError, String> exportedDataset = exportDatasetVersionAsString(datasetVersion, exporter, exportTime);

        return exportedDataset.isLeft() ?
                Either.left(exportedDataset.getLeft()) :
                Either.right(new ByteArrayInputStream(exportedDataset.get().getBytes()));
    }

    /**
     * Exports datasetVersion with given exporter.
     *
     * @return {@code Error} if exporting failed or exporter was not found in the list of exporters.
     * <p>
     * {@code String} if exporting was a success.
     */
    public Either<DataverseError, String> exportDatasetVersionAsString(DatasetVersion datasetVersion, ExporterConstant exporter, Date exportTime) {
        Optional<Exporter> loadedExporter = getExporter(exporter);

        if (loadedExporter.isPresent()) {

            String exportedDataset = Try.of(() -> loadedExporter.get().exportDataset(datasetVersion))
                    .onSuccess(string -> datasetVersion.getDataset().setLastExportTime(exportTime))
                    .onFailure(throwable -> logger.fine(throwable.getMessage()))
                    .getOrElse(StringUtils.EMPTY);

            return exportedDataset.isEmpty() ?
                    Either.left(new DataverseError("Failed to export the dataset as " + exporter)) :
                    Either.right(exportedDataset);
        }

        return Either.left(new DataverseError(exporter + " was not found among exporter list"));
    }

    public Map<ExporterConstant, Exporter> getAllExporters() {
        return exporters;
    }

    /**
     * @return MediaType of given exporter or {@code MediaType.TEXT_PLAIN} if provider is not found.
     */
    public String getMediaType(ExporterConstant provider) {

        return getExporter(provider)
                .map(Exporter::getMediaType)
                .orElse(MediaType.TEXT_PLAIN);
    }

    // -------------------- PRIVATE --------------------

    private Optional<Exporter> getExporter(ExporterConstant exporter) {
        return Optional.ofNullable(exporters.get(exporter));
    }
}
