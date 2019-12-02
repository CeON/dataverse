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
import java.util.List;
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

    public FileMetadata updateFileMetadataWithProvFreeform(FileMetadata fileMetadataToUpdate, Map<String, UpdatesEntry> provenanceUpdates) {

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
    public Set<UpdatesEntry> saveStagedProvJson(boolean saveContext, List<FileMetadata> checksumSource, Map<String, UpdatesEntry> provenanceUpdates) {
        Set<String> finalChecksums = checksumSource.stream()
                .map(fileMetadata -> fileMetadata.getDataFile().getChecksumValue())
                .collect(Collectors.toSet());

        Set<Map.Entry<String, UpdatesEntry>> provenanceUpdatesForChange = provenanceUpdates.entrySet().stream()
                .filter(provMap -> finalChecksums.contains(provMap.getKey()))
                .collect(Collectors.toSet());

        Set<UpdatesEntry> updatedEntries = new HashSet<>();
        for (Map.Entry<String, UpdatesEntry> m : provenanceUpdatesForChange) {
            UpdatesEntry mapEntry = m.getValue();
            DataFile df = mapEntry.getDataFile();
            Option<String> provString = mapEntry.getProvJson();

            if (mapEntry.getDeleteJson()) {
                DataFile updatedDataFile = commandEngine.submit((new DeleteProvJsonCommand(dvRequestService.getDataverseRequest(), df, saveContext)));
                updatedEntries.add(new UpdatesEntry(updatedDataFile, mapEntry.getProvJson(), mapEntry.getDeleteJson(), mapEntry.getProvFreeform()));
            } else if (provString.isDefined()) {
                DataFile updatedDataFile = commandEngine.submit(new PersistProvJsonCommand(dvRequestService.getDataverseRequest(), df, provString.get(),
                                                                                           df.getProvEntityName(), saveContext));
                updatedEntries.add(new UpdatesEntry(updatedDataFile, mapEntry.getProvJson(), mapEntry.getDeleteJson(), mapEntry.getProvFreeform()));
            }

        }

        return updatedEntries;
    }

    // -------------------- PRIVATE --------------------

    private boolean isProvFreeFormAvailable(Tuple2<FileMetadata, UpdatesEntry> fileMetadataWithProvenance) {
        return fileMetadataWithProvenance._2() != null && fileMetadataWithProvenance._2().getProvFreeform() != null;
    }
}
