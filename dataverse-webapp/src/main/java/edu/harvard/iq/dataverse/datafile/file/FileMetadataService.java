package edu.harvard.iq.dataverse.datafile.file;

import edu.harvard.iq.dataverse.DataverseRequestServiceBean;
import edu.harvard.iq.dataverse.EjbDataverseEngine;
import edu.harvard.iq.dataverse.engine.command.impl.DeleteProvJsonCommand;
import edu.harvard.iq.dataverse.engine.command.impl.PersistProvJsonCommand;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.datafile.FileMetadata;
import edu.harvard.iq.dataverse.provenance.UpdatesEntry;
import io.vavr.control.Option;

import javax.ejb.Stateless;
import javax.inject.Inject;
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

    /**
     * If file is among provenanceUpdates file will be updated with ProvFreeForm.
     */
    public FileMetadata updateFileMetadataWithProvFreeForm(FileMetadata fileMetadataToUpdate, Map<String, UpdatesEntry> provenanceUpdates) {

        UpdatesEntry provEntry = provenanceUpdates.get(fileMetadataToUpdate.getDataFile().getChecksumValue());

        if (provEntry != null && provEntry.getProvFreeform() != null){
            fileMetadataToUpdate.setProvFreeForm(provEntry.getProvFreeform());
        }

        return fileMetadataToUpdate;
    }

    /**
     * Aggregate function that either persists provenance or deletes it.
     * @param checksumSource - used for filtering provenance, since Datafile checksum is used as key in provenanceUpdates.
     */
    public Option<DataFile> manageProvJson(boolean saveContext, FileMetadata checksumSource, Map<String, UpdatesEntry> provenanceUpdates) {

        Set<Map.Entry<String, UpdatesEntry>> provenanceUpdatesForChange = provenanceUpdates.entrySet().stream()
                .filter(provMap -> {
                    String checksumValue = checksumSource.getDataFile().getChecksumValue();
                    return checksumValue.equals(provMap.getKey());
                })
                .collect(Collectors.toSet());

        Option<DataFile> updatedEntry = Option.none();

        for (Map.Entry<String, UpdatesEntry> entry : provenanceUpdatesForChange) {
            UpdatesEntry updatesEntry = entry.getValue();
            DataFile updatedProvOwner = updatesEntry.getDataFile();
            Option<String> provString = updatesEntry.getProvJson();

            if (updatesEntry.getDeleteJson()) {
                DataFile updatedDataFile = commandEngine.submit((new DeleteProvJsonCommand(dvRequestService.getDataverseRequest(), updatedProvOwner, saveContext)));
                return Option.of(updatedDataFile);
            } else if (provString.isDefined()) {
                DataFile updatedDataFile = commandEngine.submit(new PersistProvJsonCommand(dvRequestService.getDataverseRequest(), updatedProvOwner, provString.get(),
                                                                                           updatedProvOwner.getProvEntityName(), saveContext));
                return Option.of(updatedDataFile);
            }

        }

        return updatedEntry;
    }
}
