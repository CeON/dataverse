package edu.harvard.iq.dataverse.datafile;

import edu.harvard.iq.dataverse.DataFileServiceBean;
import edu.harvard.iq.dataverse.DataverseRequestServiceBean;
import edu.harvard.iq.dataverse.EjbDataverseEngine;
import edu.harvard.iq.dataverse.dataaccess.DataAccess;
import edu.harvard.iq.dataverse.datacapturemodule.DataCaptureModuleUtil;
import edu.harvard.iq.dataverse.datacapturemodule.ScriptRequestResponse;
import edu.harvard.iq.dataverse.datafile.pojo.RsyncInfo;
import edu.harvard.iq.dataverse.dataset.DatasetThumbnail;
import edu.harvard.iq.dataverse.engine.command.impl.DeleteDataFileCommand;
import edu.harvard.iq.dataverse.engine.command.impl.RequestRsyncScriptCommand;
import edu.harvard.iq.dataverse.engine.command.impl.UpdateDatasetThumbnailCommand;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.datafile.FileMetadata;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import io.vavr.control.Option;
import io.vavr.control.Try;
import org.apache.commons.lang.StringUtils;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.Collection;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Stateless
public class DataFilesService {

    private final Logger logger = Logger.getLogger(DataFilesService.class.getName());

    private EjbDataverseEngine commandEngine;
    private DataverseRequestServiceBean dvRequestService;
    private DataFileServiceBean datafileDao;

    // -------------------- CONSTRUCTORS --------------------

    @Deprecated
    public DataFilesService() {
    }

    @Inject
    public DataFilesService(EjbDataverseEngine commandEngine, DataverseRequestServiceBean dvRequestService, DataFileServiceBean datafileDao) {
        this.commandEngine = commandEngine;
        this.dvRequestService = dvRequestService;
        this.datafileDao = datafileDao;
    }

    // -------------------- LOGIC --------------------

    /**
     * Replaces default thumbnail with the one provided.
     *
     * @param datasetForNewThumbnail dataset that will have new thumbnail
     * @param datafileThumbnailId    id of the thumbnail that will be set for dataset
     */
    public DatasetThumbnail changeDatasetThumbnail(Dataset datasetForNewThumbnail, long datafileThumbnailId) {
        return commandEngine.submit(new UpdateDatasetThumbnailCommand(dvRequestService.getDataverseRequest(),
                                                                      datasetForNewThumbnail,
                                                                      UpdateDatasetThumbnailCommand.UserIntent.setDatasetFileAsThumbnail,
                                                                      datafileThumbnailId,
                                                                      null));

    }

    public Set<DataFile> deleteFileVersions(Collection<FileMetadata> filesToDelete, DatasetVersion workingVersion) {
        return filesToDelete.stream()
                .map(fileMetadata -> deleteFileVersion(fileMetadata, workingVersion))
                .collect(Collectors.toSet());
    }

    /**
     * Deletes file from entities and physically if it's not published (released).
     */
    public DataFile deleteFileVersion(FileMetadata fileToDelete, DatasetVersion workingVersion) {
        Dataset datasetFileOwner = fileToDelete.getDatasetVersion().getDataset();
        DataFile fileMetadataOwner = fileToDelete.getDataFile();

        if (isFileAThumbnail(fileToDelete, datasetFileOwner)) {
            datasetFileOwner.setThumbnailFile(null);
        }

        if (!fileMetadataOwner.isReleased()) {
            String deleteStorageLocation = datafileDao.getPhysicalFileToDelete(fileMetadataOwner);

            Try<FileMetadata> deleteFileOperation = Try.of(() -> {
                deleteFileFromEntites(fileToDelete, workingVersion, datasetFileOwner);
                return fileToDelete;
            })
                    .onFailure(ex -> logger.log(Level.WARNING, "Failed to delete DataFile id=" + fileMetadataOwner.getId()
                            + "from the database; ", ex));


            if (deleteFileOperation.isSuccess()) {
                if (deleteStorageLocation != null) {

                    Try.run(() -> datafileDao.finalizeFileDelete(fileMetadataOwner.getId(), deleteStorageLocation, new DataAccess()))
                            .onFailure(ex -> logger.log(Level.WARNING, "Failed to delete the physical file associated with the deleted datafile id= "
                                    + fileMetadataOwner.getId() +
                                    ", storage location: " + deleteStorageLocation, ex));
                }
            }

        } else {
            fileMetadataOwner.getFileMetadatas().remove(fileToDelete);
            workingVersion.getFileMetadatas().remove(fileToDelete);
        }

        return fileMetadataOwner;
    }

    public Option<RsyncInfo> retrieveRsyncScript(Dataset dataset, DatasetVersion workingVersion) {
        ScriptRequestResponse scriptRequestResponse = commandEngine.submit(new RequestRsyncScriptCommand(dvRequestService.getDataverseRequest(), dataset));

        if (StringUtils.isNotEmpty(scriptRequestResponse.getScript())) {
            return Option.of(new RsyncInfo(scriptRequestResponse.getScript(), DataCaptureModuleUtil.getScriptName(workingVersion)));
        }

        return Option.none();
    }

    // -------------------- PRIVATE --------------------

    private void deleteFileFromEntites(FileMetadata fileToDelete, DatasetVersion workingVersion, Dataset datasetFileOwner) {
        commandEngine.submit(new DeleteDataFileCommand(fileToDelete.getDataFile(), dvRequestService.getDataverseRequest()));
        datasetFileOwner.getFiles().remove(fileToDelete.getDataFile());
        workingVersion.getFileMetadatas().remove(fileToDelete);
        datasetFileOwner.getCategories().forEach(dataFileCategory -> dataFileCategory.getFileMetadatas().remove(fileToDelete));
    }

    private boolean isFileAThumbnail(FileMetadata fileToDelete, Dataset datasetFileOwner) {
        return fileToDelete.getDataFile().equals(datasetFileOwner.getThumbnailFile());
    }
}
