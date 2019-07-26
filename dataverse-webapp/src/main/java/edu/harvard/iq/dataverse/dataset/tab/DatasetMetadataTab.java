package edu.harvard.iq.dataverse.dataset.tab;

import edu.harvard.iq.dataverse.Dataset;
import edu.harvard.iq.dataverse.DatasetPage;
import edu.harvard.iq.dataverse.DatasetVersion;
import edu.harvard.iq.dataverse.DataverseRequestServiceBean;
import edu.harvard.iq.dataverse.PermissionsWrapper;
import edu.harvard.iq.dataverse.export.ExportService;
import edu.harvard.iq.dataverse.export.spi.Exporter;
import edu.harvard.iq.dataverse.util.SystemConfig;
import io.vavr.Tuple;
import io.vavr.Tuple2;

import javax.faces.bean.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@ViewScoped
@Named("DatasetMetadataTab")
public class DatasetMetadataTab implements Serializable {

    private PermissionsWrapper permissionsWrapper;
    private DataverseRequestServiceBean dvRequestService;
    private ExportService exportService;
    private SystemConfig systemConfig;

    private DatasetVersion currentDatasetVersion;
    private Dataset dataset;
    private DatasetPage.EditMode currentEditMode;
    private boolean isLocked;

    // -------------------- CONSTRUCTORS --------------------

    @Inject
    public DatasetMetadataTab(PermissionsWrapper permissionsWrapper,
                              DataverseRequestServiceBean dvRequestService,
                              ExportService exportService,
                              SystemConfig systemConfig) {
        this.permissionsWrapper = permissionsWrapper;
        this.dvRequestService = dvRequestService;
        this.exportService = exportService;
        this.systemConfig = systemConfig;
    }

    // -------------------- GETTERS --------------------

    public DatasetPage.EditMode getCurrentEditMode() {
        return currentEditMode;
    }

    public DatasetVersion getCurrentDatasetVersion() {
        return currentDatasetVersion;
    }

    public Dataset getDataset() {
        return dataset;
    }

    public boolean isLocked() {
        return isLocked;
    }

    // -------------------- LOGIC --------------------

    public void init(DatasetVersion currentDatasetVersion,
                     Dataset dataset,
                     DatasetPage.EditMode currentEditMode,
                     boolean isLocked) {
        this.currentDatasetVersion = currentDatasetVersion;
        this.dataset = dataset;
        this.currentEditMode = currentEditMode;
        this.isLocked = isLocked;
    }

    public boolean canUpdateDataset() {
        return permissionsWrapper.canUpdateDataset(dvRequestService.getDataverseRequest(), this.dataset);
    }


    public List<Tuple2<String, String>> getExportersDisplayNameAndURL() {
        List<Tuple2<String, String>> exportersInfo = new ArrayList<>();

        exportService.getAllExporters().values().stream()
                .filter(Exporter::isAvailableToUsers)
                .forEach(exporter -> exportersInfo.add(Tuple.of(exporter.getDisplayName(), createExporterURL(exporter, systemConfig.getDataverseSiteUrl()))));

        return exportersInfo;
    }

    // -------------------- PRIVATE --------------------

    private String createExporterURL(Exporter exporter, String myHostURL) {
        return myHostURL + "/api/datasets/export?exporter=" + exporter.getProviderName() + "&persistentId=" + dataset.getGlobalIdString();
    }
}
