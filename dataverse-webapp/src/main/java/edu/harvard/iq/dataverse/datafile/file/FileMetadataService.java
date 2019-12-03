package edu.harvard.iq.dataverse.datafile.file;

import edu.harvard.iq.dataverse.DataverseRequestServiceBean;
import edu.harvard.iq.dataverse.EjbDataverseEngine;
import edu.harvard.iq.dataverse.engine.command.impl.DeleteProvJsonCommand;
import edu.harvard.iq.dataverse.engine.command.impl.PersistProvJsonCommand;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.datafile.FileMetadata;
import edu.harvard.iq.dataverse.provenance.UpdatesEntry;
import io.vavr.Tuple2;
import io.vavr.control.Option;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Stateless
public class FileMetadataService {

    private EjbDataverseEngine commandEngine;
    private DataverseRequestServiceBean dvRequestService;

    // -------------------- CONSTRUCTORS --------------------

    @Deprecated
    public FileMetadataService() {
    }

    @Inject
    public FileMetadataService(EjbDataverseEngine commandEngine, DataverseRequestServiceBean dvRequestService) {
        this.commandEngine = commandEngine;
        this.dvRequestService = dvRequestService;
    }

    // -------------------- LOGIC --------------------

    public FileMetadata updateFileMetadataWithProvFreeForm(FileMetadata fileMetadataToUpdate, Map<String, UpdatesEntry> provenanceUpdates) {

        UpdatesEntry provEntry = provenanceUpdates.get(fileMetadataToUpdate.getDataFile().getChecksumValue());

        if (provEntry != null && provEntry.getProvFreeform() != null){
            fileMetadataToUpdate.setProvFreeForm(provEntry.getProvFreeform());
        }

        return fileMetadataToUpdate;
    }

    /**
     * Note that the user may have uploaded provenance metadata file(s)
     * for some of the new files that have since failed to be permanently saved
     * in storage (in the ingestService.saveAndAddFilesToDataset() step, above);
     * these files have been dropped from the fileMetadatas list, and we
     * are not adding them to the dataset; but the
     * provenance update set still has entries for these failed files,
     * so we are passing the fileMetadatas list to the saveStagedProvJson()
     * method below - so that it doesn't attempt to save the entries
     * that are no longer valid.
     * @param checksumSource - used for filtering provenance, since Datafile checksum is used as key in provenanceUpdates.
     */
    public Set<DataFile> manageProvJson(boolean saveContext, FileMetadata checksumSource, Map<String, UpdatesEntry> provenanceUpdates) {

        Set<Map.Entry<String, UpdatesEntry>> provenanceUpdatesForChange = provenanceUpdates.entrySet().stream()
                .filter(provMap -> {
                    String checksumValue = checksumSource.getDataFile().getChecksumValue();
                    return checksumValue.equals(provMap.getKey());
                })
                .collect(Collectors.toSet());

        Set<DataFile> updatedEntries = new HashSet<>();

        for (Map.Entry<String, UpdatesEntry> entry : provenanceUpdatesForChange) {
            UpdatesEntry updatesEntry = entry.getValue();
            DataFile updatedProvOwner = updatesEntry.getDataFile();
            Option<String> provString = updatesEntry.getProvJson();

            if (updatesEntry.getDeleteJson()) {
                DataFile updatedDataFile = commandEngine.submit((new DeleteProvJsonCommand(dvRequestService.getDataverseRequest(), updatedProvOwner, saveContext)));
                updatedEntries.add(updatedDataFile);
            } else if (provString.isDefined()) {
                DataFile updatedDataFile = commandEngine.submit(new PersistProvJsonCommand(dvRequestService.getDataverseRequest(), updatedProvOwner, provString.get(),
                                                                                           updatedProvOwner.getProvEntityName(), saveContext));
                updatedEntries.add(updatedDataFile);
            }

        }

        return updatedEntries;
    }

    // -------------------- PRIVATE --------------------

    private boolean isProvFreeFormAvailable(Tuple2<FileMetadata, UpdatesEntry> fileMetadataWithProvenance) {
        return fileMetadataWithProvenance._2() != null && fileMetadataWithProvenance._2().getProvFreeform() != null;
    }
}
