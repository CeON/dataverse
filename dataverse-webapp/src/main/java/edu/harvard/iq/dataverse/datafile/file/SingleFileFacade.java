package edu.harvard.iq.dataverse.datafile.file;

import edu.harvard.iq.dataverse.DataverseRequestServiceBean;
import edu.harvard.iq.dataverse.EjbDataverseEngine;
import edu.harvard.iq.dataverse.datafile.file.exception.ProvenanceChangeException;
import edu.harvard.iq.dataverse.engine.command.impl.UpdateDatasetVersionCommand;
import edu.harvard.iq.dataverse.persistence.datafile.FileMetadata;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.provenance.UpdatesEntry;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import io.vavr.control.Try;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.Collections;
import java.util.Map;

@Stateless
public class SingleFileFacade {

    private FileMetadataService fileMetadataService;
    private SettingsServiceBean settingsService;
    private EjbDataverseEngine commandEngine;
    private DataverseRequestServiceBean dvRequestService;

    // -------------------- CONSTRUCTORS --------------------

    @Deprecated
    public SingleFileFacade() {
    }

    @Inject
    public SingleFileFacade(FileMetadataService fileMetadataService, SettingsServiceBean settingsService,
                            EjbDataverseEngine commandEngine, DataverseRequestServiceBean dvRequestService) {
        this.fileMetadataService = fileMetadataService;
        this.settingsService = settingsService;
        this.commandEngine = commandEngine;
        this.dvRequestService = dvRequestService;
    }

    // -------------------- LOGIC --------------------

    /**
     * Saves provenance updates if the setting is enabled, and then updates the dataset.
     */
    public Dataset saveFileChanges(FileMetadata fileToModify, Map<String, UpdatesEntry> provUpdates) {

        if (settingsService.isTrueForKey(SettingsServiceBean.Key.ProvCollectionEnabled)) {

            fileMetadataService.updateFileMetadataWithProvFreeForm(fileToModify, provUpdates);

            Try.of(() -> fileMetadataService.manageProvJson(false, fileToModify, provUpdates))
                    .getOrElseThrow(ex -> new ProvenanceChangeException("There was a problem with changing provenance file", ex));

        }


        Dataset datasetToUpdate = fileToModify.getDatasetVersion().getDataset();
        DatasetVersion cloneDatasetVersion = datasetToUpdate.getEditVersion().cloneDatasetVersion();

        UpdateDatasetVersionCommand updateCommand = new UpdateDatasetVersionCommand(datasetToUpdate, dvRequestService.getDataverseRequest(),
                                                                                    Collections.emptyList(), cloneDatasetVersion);
        updateCommand.setValidateLenient(true);

        return commandEngine.submit(updateCommand);

    }
}
