package edu.harvard.iq.dataverse.export;

import edu.harvard.iq.dataverse.DatasetVersion;
import edu.harvard.iq.dataverse.error.DataverseError;
import edu.harvard.iq.dataverse.export.spi.Exporter;
import edu.harvard.iq.dataverse.qualifiers.ProductionBean;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import io.vavr.control.Either;
import io.vavr.control.Try;
import org.apache.commons.lang.StringUtils;

import javax.annotation.PostConstruct;
import javax.ejb.Stateful;
import javax.inject.Inject;
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

    private SettingsServiceBean settingsService;

    // -------------------- CONSTRUCTORS --------------------

    @Deprecated
    ExportService() {
        //JEE requirement
    }

    @Inject
    public ExportService(@ProductionBean SettingsServiceBean settingsService) {
        this.settingsService = settingsService;
    }

    @PostConstruct
    void loadAllExporters() {
        boolean isEmailExcludedFromExport = settingsService.isTrueForKey(SettingsServiceBean.Key.ExcludeEmailFromExport);

        exporters.put(ExporterConstant.DDI, new DDIExporter(isEmailExcludedFromExport));

        exporters.put(ExporterConstant.DATACITE, new DataCiteExporter());

        exporters.put(ExporterConstant.DCTERMS, new DCTermsExporter(isEmailExcludedFromExport));

        exporters.put(ExporterConstant.DUBLINCORE, new DublinCoreExporter(isEmailExcludedFromExport));

        exporters.put(ExporterConstant.OAIDDI, new OAI_DDIExporter(isEmailExcludedFromExport));

        exporters.put(ExporterConstant.OAIORE, new OAI_OREExporter(isEmailExcludedFromExport));

        exporters.put(ExporterConstant.SCHEMADOTORG, new SchemaDotOrgExporter());

        exporters.put(ExporterConstant.OPENAIRE, new OpenAireExporter(isEmailExcludedFromExport));

        exporters.put(ExporterConstant.JSON, new JSONExporter(isEmailExcludedFromExport));
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
                    .onSuccess(dataset -> datasetVersion.getDataset().setLastExportTime(exportTime))
                    .onFailure(Throwable::printStackTrace)
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
     * @return MediaType of given exporter or {@link Exporter#getMediaType()} default value.
     */
    public String getMediaType(ExporterConstant provider) {

        return getExporter(provider)
                .map(Exporter::getMediaType)
                .get();
    }

    public Optional<Exporter> getExporter(ExporterConstant exporter) {
        return Optional.ofNullable(exporters.get(exporter));
    }
}
