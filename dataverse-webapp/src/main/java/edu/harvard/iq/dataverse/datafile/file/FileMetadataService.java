package edu.harvard.iq.dataverse.datafile.file;

import edu.harvard.iq.dataverse.DataverseRequestServiceBean;
import edu.harvard.iq.dataverse.EjbDataverseEngine;
import edu.harvard.iq.dataverse.engine.command.impl.DeleteProvJsonCommand;
import edu.harvard.iq.dataverse.engine.command.impl.PersistProvJsonCommand;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.datafile.FileMetadata;
import edu.harvard.iq.dataverse.persistence.datafile.FileMetadataRepository;
import edu.harvard.iq.dataverse.provenance.UpdatesEntry;
import io.vavr.control.Option;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.List;

@Stateless
public class FileMetadataService {

    private EjbDataverseEngine commandEngine;
    private DataverseRequestServiceBean dvRequestService;
    private FileMetadataRepository fileMetadataRepository;

    // -------------------- CONSTRUCTORS --------------------

    @Deprecated
    public FileMetadataService() {
    }

    @Inject
    public FileMetadataService(EjbDataverseEngine commandEngine,
                               DataverseRequestServiceBean dvRequestService,
                               FileMetadataRepository fileMetadataRepository) {
        this.commandEngine = commandEngine;
        this.dvRequestService = dvRequestService;
        this.fileMetadataRepository = fileMetadataRepository;
    }

    // -------------------- LOGIC --------------------

    /**
     * If file is among provenanceUpdates file will be updated with ProvFreeForm.
     */
    public FileMetadata updateFileMetadataWithProvFreeForm(FileMetadata fileMetadataToUpdate, String provenanceFreeForm) {

        fileMetadataToUpdate.setProvFreeForm(provenanceFreeForm);

        return fileMetadataToUpdate;
    }

    /**
     * Aggregate function that either persists provenance or deletes it.
     */
    public Option<DataFile> manageProvJson(boolean saveContext, UpdatesEntry provenanceUpdate) {

        Option<DataFile> updatedEntry = Option.none();

        if (provenanceUpdate.getDeleteJson()) {
            DataFile updatedDataFile = commandEngine.submit((new DeleteProvJsonCommand(dvRequestService.getDataverseRequest(),
                                                                                       provenanceUpdate.getDataFile(),
                                                                                       saveContext)));
            return Option.of(updatedDataFile);
        } else if (provenanceUpdate.getProvJson().isDefined()) {
            DataFile updatedDataFile = commandEngine.submit(new PersistProvJsonCommand(dvRequestService.getDataverseRequest(),
                                                                                       provenanceUpdate.getDataFile(),
                                                                                       provenanceUpdate
                                                                                               .getProvJson()
                                                                                               .get(),
                                                                                       provenanceUpdate
                                                                                               .getDataFile()
                                                                                               .getProvEntityName(),
                                                                                       saveContext));
            return Option.of(updatedDataFile);
        }

        return updatedEntry;
    }

    public List<FileMetadata> findAccessibleFileMetadataSorted(long dsvId, int pageNumber, int amountToFetch) {
        return fileMetadataRepository.findFileMetadataByDatasetVersionIdWithPagination(dsvId, pageNumber, amountToFetch);
    }

    public List<FileMetadata> findSearchedAccessibleFileMetadataSorted(long dsvId, int pageNumber, int amountToFetch, String searchTerms) {
        return fileMetadataRepository.findSearchedFileMetadataByDatasetVersionIdWithPagination(dsvId, pageNumber, amountToFetch, searchTerms);
    }

    public List<Long> findFileMetadataIds(long dsvId) {
        return fileMetadataRepository.findFileMetadataIdsByDatasetVersionId(dsvId);
    }

}
