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
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceConfigurationError;


@Stateful
public class ExportService {

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

    public List<String[]> getExportersLabels() {
        List<String[]> retList = new ArrayList<>();
        for (Exporter e : exporters.values()) {
            String[] temp = new String[2];
            temp[0] = e.getDisplayName();
            temp[1] = e.getProviderName();
            retList.add(temp);
        }
        return retList;
    }

    public Either<DataverseError, InputStream> exportDatasetVersion(DatasetVersion dataset, ExporterConstant exporter) {

        Optional<Exporter> loadedExporter = getExporter(exporter);

        if (loadedExporter.isPresent()) {

            String exportedDataset = Try.of(() -> loadedExporter.get().exportDataset(dataset))
                    .getOrElse(StringUtils.EMPTY);

            return exportedDataset.isEmpty() ?
                    Either.left(new DataverseError("Failed to export the dataset as " + exporter)) :
                    Either.right(new ByteArrayInputStream(exportedDataset.getBytes()));
        }

        return Either.left(new DataverseError(exporter + " was not found among exporter list"));
    }

    public Optional<String> getExportAsString(DatasetVersion dataset, ExporterConstant exporter) {
        Either<DataverseError, InputStream> exportedDataset = exportDatasetVersion(dataset, exporter);

        if (exportedDataset.isLeft()) {
            return Optional.empty();
        }

        try (InputStream inputStream = exportedDataset.get();
             InputStreamReader inp = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {

            BufferedReader br = new BufferedReader(inp);
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
                sb.append('\n');
            }
            br.close();
            inp.close();
            inputStream.close();

            return Optional.of(sb.toString());

        } catch (IOException ex) {

            return Optional.empty();
        }

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

    public Optional<Exporter> getExporter(ExporterConstant exporter) {
        return Optional.ofNullable(exporters.get(exporter));
    }

    public String getMediaType(ExporterConstant provider) {

        return getExporter(provider)
                .map(Exporter::getMediaType)
                .orElse(MediaType.TEXT_PLAIN);
    }
}
