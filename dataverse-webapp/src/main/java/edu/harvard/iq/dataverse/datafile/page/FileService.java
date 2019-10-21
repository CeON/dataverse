package edu.harvard.iq.dataverse.datafile.page;

import com.google.common.collect.Lists;
import edu.harvard.iq.dataverse.DataFileServiceBean;
import edu.harvard.iq.dataverse.DataverseRequestServiceBean;
import edu.harvard.iq.dataverse.dataaccess.DataAccess;
import edu.harvard.iq.dataverse.engine.command.impl.UpdateDatasetVersionCommand;
import edu.harvard.iq.dataverse.persistence.datafile.FileMetadata;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import io.vavr.control.Try;

import javax.ejb.Stateless;
import javax.validation.ConstraintViolation;
import javax.validation.ValidationException;
import java.util.Set;
import java.util.logging.Logger;

@Stateless
public class FileService {

    private static final Logger logger = Logger.getLogger(FileService.class.getCanonicalName());

    private DataverseRequestServiceBean dvRequestService;
    private DataFileServiceBean dataFileService;

    // -------------------- LOGIC --------------------

    public String deleteFile(FileMetadata fileToDelete, Dataset datasetFileOwner) {

        datasetFileOwner.getEditVersion().getFileMetadatas().remove(fileToDelete);

        Set<ConstraintViolation> constraintViolations = fileToDelete.getDatasetVersion().validate();

        if (!constraintViolations.isEmpty()) {
            constraintViolations.forEach(constraintViolation -> logger.warning(constraintViolation.getMessage()));
            throw new ValidationException("There was validation error during deletion attempt with the dataFile id: "+ fileToDelete.getDataFile().getId());

            //JH.addMessage(FacesMessage.SEVERITY_ERROR, BundleUtil.getStringFromBundle("dataset.message.validationError"));
        }

        Try.of(() -> new UpdateDatasetVersionCommand(datasetFileOwner, dvRequestService.getDataverseRequest(), Lists.newArrayList(fileToDelete)))
                .getOrElseThrow(throwable -> new IllegalStateException("", throwable));

                /*.onFailure(CommandException.class, ex -> JH.addMessage(FacesMessage.SEVERITY_ERROR,
                                                                       BundleUtil.getStringFromBundle("dataset.save.fail"),
                                                                       " - " + ex.toString()));*/

        if (!fileToDelete.getDataFile().isReleased()) {
            deleteFilePhysically(fileToDelete);
        }

        //JsfHelper.addFlashSuccessMessage(BundleUtil.getStringFromBundle("file.message.deleteSuccess"));

        //setVersion("DRAFT");
        return "";
    }

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
}
