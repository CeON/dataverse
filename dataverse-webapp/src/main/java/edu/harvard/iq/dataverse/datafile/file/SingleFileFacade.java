package edu.harvard.iq.dataverse.datafile.file;

import com.google.common.collect.Lists;
import edu.harvard.iq.dataverse.DataverseRequestServiceBean;
import edu.harvard.iq.dataverse.EjbDataverseEngine;
import edu.harvard.iq.dataverse.datafile.page.EditDatafilesPage;
import edu.harvard.iq.dataverse.engine.command.impl.UpdateDatasetVersionCommand;
import edu.harvard.iq.dataverse.persistence.datafile.FileMetadata;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.provenance.UpdatesEntry;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import edu.harvard.iq.dataverse.util.JsfHelper;
import io.vavr.control.Try;

import javax.ejb.Stateless;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.omnifaces.util.Faces.getBundleString;

@Stateless
public class SingleFileFacade {

    private FileMetadataService fileMetadataService;
    private SettingsServiceBean settingsService;
    private EjbDataverseEngine commandEngine;
    private DataverseRequestServiceBean dvRequestService;

    // -------------------- LOGIC --------------------

    public Dataset saveFileChanges(FileMetadata fileMetadata, Map<String, UpdatesEntry> provUpdates) {

        if (settingsService.isTrueForKey(SettingsServiceBean.Key.ProvCollectionEnabled)) {

            fileMetadataService.updateFileMetadataWithProvFreeform(fileMetadata, provUpdates);

            Try<Set<UpdatesEntry>> sets = Try.of(() -> fileMetadataService.saveStagedProvJson(false, Lists.newArrayList(fileMetadata), provUpdates))
                    .onFailure(ex -> {
                        JsfHelper.addFlashErrorMessage(getBundleString("file.metadataTab.provenance.error"));
                        Logger.getLogger(EditDatafilesPage.class.getName()).log(Level.SEVERE, "There was a problem with saving prov json", ex);
                    });

        }
        Dataset datasetToUpdate = fileMetadata.getDatasetVersion().getDataset();
        DatasetVersion cloneDatasetVersion = datasetToUpdate.getEditVersion().cloneDatasetVersion();

        UpdateDatasetVersionCommand updateCommand = new UpdateDatasetVersionCommand(datasetToUpdate, dvRequestService.getDataverseRequest(),
                                                                                    Collections.emptyList(), cloneDatasetVersion);
        updateCommand.setValidateLenient(true);

        return commandEngine.submit(updateCommand);
    }
}
