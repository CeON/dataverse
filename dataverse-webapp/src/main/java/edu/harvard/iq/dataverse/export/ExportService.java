package edu.harvard.iq.dataverse.export;

import edu.harvard.iq.dataverse.Dataset;
import edu.harvard.iq.dataverse.DatasetVersion;
import edu.harvard.iq.dataverse.error.DataverseError;
import edu.harvard.iq.dataverse.export.spi.Exporter;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import edu.harvard.iq.dataverse.util.json.JsonPrinter;
import io.vavr.control.Either;
import io.vavr.control.Try;
import org.apache.commons.lang.StringUtils;

import javax.annotation.PostConstruct;
import javax.ejb.Stateful;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.sql.Timestamp;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.logging.Logger;


/**
 * Class responsible for managing exporters and mainly exporting.
 */
@Stateful
public class ExportService {

    private static final Logger logger = Logger.getLogger(ExportService.class.getCanonicalName());

    private static ExportService service;
    static SettingsServiceBean settingsService;

    private Map<ExporterConstant, Exporter> exporters = new HashMap<>();

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
     * @deprecated Use `getInstance(SettingsServiceBean settingsService)`
     * instead. For privacy reasons, we need to pass in settingsService so that
     * we can make a decision whether not not to exclude email addresses. No new
     * code should call this method and it would be nice to remove calls from
     * existing code.
     */
    @Deprecated
    public static synchronized ExportService getInstance() {
        return getInstance(null);
    }

    public static synchronized ExportService getInstance(SettingsServiceBean settingsService) {
        ExportService.settingsService = settingsService;
        // We pass settingsService into the JsonPrinter so it can check the :ExcludeEmailFromExport setting in calls to JsonPrinter.jsonAsDatasetDto().
        JsonPrinter.setSettingsService(settingsService);
        if (service == null) {
            service = new ExportService();
        }
        return service;
    }

    /**
     * Exports datasetVersion with given exporter.
     *
     * @return {@code Error} if exporting failed or exporter was not found in the list of exporters.
     * <p>
     * {@code InputStream} if exporting was an success.
     */
    public Either<DataverseError, InputStream> exportDatasetVersion(DatasetVersion dataset, ExporterConstant exporter) {
        Either<DataverseError, String> exportedDataset = exportDatasetVersionAsString(dataset, exporter);

        return exportedDataset.isLeft() ?
                Either.left(exportedDataset.getLeft()) :
                Either.right(new ByteArrayInputStream(exportedDataset.get().getBytes()));
    }

    /**
     * Exports datasetVersion with given exporter.
     *
     * @return {@code Error} if exporting failed or exporter was not found in the list of exporters.
     * <p>
     * {@code String} if exporting was an success.
     */
    public Either<DataverseError, String> exportDatasetVersionAsString(DatasetVersion dataset, ExporterConstant exporter) {
        Optional<Exporter> loadedExporter = getExporter(exporter);

        if (loadedExporter.isPresent()) {

            String exportedDataset = Try.of(() -> loadedExporter.get().exportDataset(dataset))
                    .onFailure(throwable -> logger.fine(throwable.getMessage()))
                    .getOrElse(StringUtils.EMPTY);

            return exportedDataset.isEmpty() ?
                    Either.left(new DataverseError("Failed to export the dataset as " + exporter)) :
                    Either.right(exportedDataset);
        }

        return Either.left(new DataverseError(exporter + " was not found among exporter list"));
    }

    // This method goes through all the Exporters and calls 
    // the "chacheExport()" method that will save the produced output  
    // in a file in the dataset directory, on each Exporter available. 
    public void exportAllFormats(Dataset dataset) throws ExportException {

        try {
            DatasetVersion releasedVersion = dataset.getReleasedVersion();
            if (releasedVersion == null) {
                throw new ExportException("No released version for dataset " + dataset.getGlobalId().toString());
            }

            final JsonObjectBuilder datasetAsJsonBuilder = JsonPrinter.jsonAsDatasetDto(releasedVersion);
            JsonObject datasetAsJson = datasetAsJsonBuilder.build();

            for (Exporter e : exporters.values()) {
                String formatName = e.getProviderName();

            }
        } catch (ServiceConfigurationError serviceError) {
            throw new ExportException("Service configuration error during export. " + serviceError.getMessage());
        }
        // Finally, if we have been able to successfully export in all available 
        // formats, we'll increment the "last exported" time stamp: 

        dataset.setLastExportTime(new Timestamp(new Date().getTime()));

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
