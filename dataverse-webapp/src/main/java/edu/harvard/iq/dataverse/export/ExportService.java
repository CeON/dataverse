package edu.harvard.iq.dataverse.export;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.PostConstruct;
import javax.ejb.Stateless;
import javax.inject.Inject;

import org.apache.commons.lang.StringUtils;

import edu.harvard.iq.dataverse.error.DataverseError;
import edu.harvard.iq.dataverse.export.spi.Exporter;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import edu.harvard.iq.dataverse.util.SystemConfig;
import io.vavr.control.Either;
import io.vavr.control.Try;


/**
 * Class responsible for managing exporters and mainly exporting.
 */
@Stateless
public class ExportService {

    private Map<ExporterType, Exporter> exporters = new HashMap<>();
    private LocalDate currentDate = null;

    private SettingsServiceBean settingsService;
    private SystemConfig systemConfig;

    // -------------------- CONSTRUCTORS --------------------

    @Deprecated
    ExportService() {
        //JEE requirement
    }

    @Inject
    public ExportService(SettingsServiceBean settingsService,
                         SystemConfig systemConfig) {
        this.settingsService = settingsService;
        this.systemConfig = systemConfig;
    }

    public ExportService(SettingsServiceBean settingsService,
            SystemConfig systemConfig, LocalDate currentDate) {
        this(settingsService, systemConfig);
        this.currentDate = currentDate;
}

    @PostConstruct
    void loadAllExporters() {
        boolean isEmailExcludedFromExport = settingsService.isTrueForKey(SettingsServiceBean.Key.ExcludeEmailFromExport);

        exporters.put(ExporterType.DDI, new DDIExporter(isEmailExcludedFromExport, systemConfig.getDataverseSiteUrl()));

        exporters.put(ExporterType.DATACITE, new DataCiteExporter());

        exporters.put(ExporterType.DCTERMS, new DCTermsExporter(isEmailExcludedFromExport));

        exporters.put(ExporterType.DUBLINCORE, new DublinCoreExporter(isEmailExcludedFromExport));

        exporters.put(ExporterType.OAIDDI, new OAI_DDIExporter(isEmailExcludedFromExport, systemConfig.getDataverseSiteUrl()));

        exporters.put(ExporterType.OAIORE, new OAI_OREExporter(isEmailExcludedFromExport, systemConfig.getDataverseSiteUrl(), currentDate));

        exporters.put(ExporterType.SCHEMADOTORG, new SchemaDotOrgExporter(systemConfig.getDataverseSiteUrl(),
                settingsService.getValueForKey(SettingsServiceBean.Key.HideSchemaDotOrgDownloadUrls)));

        exporters.put(ExporterType.OPENAIRE, new OpenAireExporter(isEmailExcludedFromExport));

        exporters.put(ExporterType.JSON, new JSONExporter(isEmailExcludedFromExport));
    }

    // -------------------- LOGIC --------------------

    /**
     * Exports datasetVersion with given exporter.
     *
     * @return {@code Error} if exporting failed or exporter was not found in the list of exporters.
     * <p>
     * {@code String} if exporting was a success.
     */
    public Either<DataverseError, String> exportDatasetVersionAsString(DatasetVersion datasetVersion, ExporterType exporter) {
        Optional<Exporter> loadedExporter = getExporter(exporter);

        if (loadedExporter.isPresent()) {

            String exportedDataset = Try.of(() -> loadedExporter.get()
                    .exportDataset(datasetVersion))
                    .onFailure(Throwable::printStackTrace)
                    .getOrElse(StringUtils.EMPTY);

            return exportedDataset.isEmpty() ?
                    Either.left(new DataverseError("Failed to export the dataset as " + exporter)) :
                    Either.right(exportedDataset);
        }

        return Either.left(new DataverseError(exporter + " was not found among exporter list"));
    }

    public Map<ExporterType, Exporter> getAllExporters() {
        return exporters;
    }

    /**
     * @return MediaType of given exporter or {@link Exporter#getMediaType()} default value.
     */
    public String getMediaType(ExporterType provider) {

        return getExporter(provider)
                .map(Exporter::getMediaType)
                .get();
    }

    public Optional<Exporter> getExporter(ExporterType exporter) {
        return Optional.ofNullable(exporters.get(exporter));
    }

}
