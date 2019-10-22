package edu.harvard.iq.dataverse.datafile.page;

import com.google.common.collect.Lists;
import edu.harvard.iq.dataverse.DataFileServiceBean;
import edu.harvard.iq.dataverse.DataverseRequestServiceBean;
import edu.harvard.iq.dataverse.dataaccess.DataAccess;
import edu.harvard.iq.dataverse.engine.command.exception.UpdateFailedException;
import edu.harvard.iq.dataverse.engine.command.impl.PersistProvFreeFormCommand;
import edu.harvard.iq.dataverse.engine.command.impl.UpdateDatasetVersionCommand;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.datafile.FileMetadata;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import io.vavr.control.Try;

import javax.ejb.Stateless;
import javax.validation.ConstraintViolation;
import javax.validation.ValidationException;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

@Stateless
public class FileService {

    private static final Logger logger = Logger.getLogger(FileService.class.getCanonicalName());

    private DataverseRequestServiceBean dvRequestService;
    private DataFileServiceBean dataFileService;

    // -------------------- LOGIC --------------------

    public FileMetadata deleteFile(FileMetadata fileToDelete, Dataset datasetFileOwner) {

        datasetFileOwner.getEditVersion().getFileMetadatas().remove(fileToDelete);

        Set<ConstraintViolation> constraintViolations = fileToDelete.getDatasetVersion().validate();

        if (!constraintViolations.isEmpty()) {
            constraintViolations.forEach(constraintViolation -> logger.warning(constraintViolation.getMessage()));
            throw new ValidationException("There was validation error during deletion attempt with the dataFile id: " + fileToDelete.getDataFile().getId());

        }

        Try.of(() -> new UpdateDatasetVersionCommand(datasetFileOwner, dvRequestService.getDataverseRequest(), Lists.newArrayList(fileToDelete)))
                .getOrElseThrow(throwable -> new UpdateFailedException("Dataset Update failed with dataset id: " + datasetFileOwner.getId(), throwable));

        if (!fileToDelete.getDataFile().isReleased()) {
            deleteFilePhysically(fileToDelete);
        }

        return fileToDelete;
    }

    public Dataset saveProvenanceFileWithDesc(FileMetadata editedFile, DataFile uploadedProvFile, String provenanceDesciption) {
        Dataset datasetFileOwner = editedFile.getDataFile().getOwner();

        editedFile.getDataFile().setProvEntityName(uploadedProvFile.getProvEntityName()); //passing this value into the file being saved here is pretty hacky.

        findDataFileInDataset(datasetFileOwner, editedFile.getDataFile())
                .ifPresent(file -> new PersistProvFreeFormCommand(dvRequestService.getDataverseRequest(),
                                                                  file.getDataFile(),
                                                                  provenanceDesciption));

        Set<ConstraintViolation> constraintViolations = editedFile.getDatasetVersion().validate();

        if (!constraintViolations.isEmpty()) {
            constraintViolations.forEach(constraintViolation -> logger.warning(constraintViolation.getMessage()));
            throw new ValidationException("There was validation error during deletion attempt with the dataFile id: " + editedFile.getDataFile().getId());
        }

        Try.of(() -> new UpdateDatasetVersionCommand(datasetFileOwner, dvRequestService.getDataverseRequest(), new ArrayList<>()))
                .getOrElseThrow(throwable -> new UpdateFailedException("Dataset Update failed with dataset id: " + datasetFileOwner.getId(), throwable));

        return datasetFileOwner;
    }

    // -------------------- PRIVATE --------------------

    private void deleteFilePhysically(FileMetadata fileToDelete) {
        String fileStorageLocation = dataFileService.getPhysicalFileToDelete(fileToDelete.getDataFile());

        if (fileStorageLocation != null) {
            Try.run(() -> dataFileService.finalizeFileDelete(fileToDelete.getDataFile().getId(), fileStorageLocation, new DataAccess()))
                    .onFailure(throwable -> logger.warning("Failed to delete the physical file associated with the deleted datafile id="
                                                                   + fileToDelete.getDataFile().getId() + ", storage location: " + fileStorageLocation));
        } else {
            throw new IllegalStateException("DataFile with id: " + fileToDelete.getDataFile().getId() + " doesn't have storage location");
        }
    }

    private Optional<FileMetadata> findDataFileInDataset(Dataset dataset, DataFile fileToFind) {
        return dataset.getEditVersion().getFileMetadatas().stream()
                .filter(fmd -> fmd.getDataFile().equals(fileToFind))
                .findAny();
    }

}
