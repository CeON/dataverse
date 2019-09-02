package edu.harvard.iq.dataverse.datasetutility;

import com.google.common.collect.Lists;
import edu.harvard.iq.dataverse.DataFileServiceBean;
import edu.harvard.iq.dataverse.DataverseRequestServiceBean;
import edu.harvard.iq.dataverse.EjbDataverseEngine;
import edu.harvard.iq.dataverse.common.BundleUtil;
import edu.harvard.iq.dataverse.dataaccess.DataAccess;
import edu.harvard.iq.dataverse.engine.command.DataverseRequest;
import edu.harvard.iq.dataverse.engine.command.impl.UpdateDatasetVersionCommand;
import edu.harvard.iq.dataverse.ingest.IngestServiceBean;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import io.vavr.control.Try;
import org.apache.commons.lang.StringUtils;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Stateless
public class ReplaceFileHandler implements Serializable {

    private static final Logger logger = Logger.getLogger(ReplaceFileHandler.class.getCanonicalName());

    private IngestServiceBean ingestService;
    private DataFileServiceBean datafileService;
    private EjbDataverseEngine commandEngine;
    private DataverseRequestServiceBean dvRequestService;

    public ReplaceFileHandler() {
    }

    @Inject
    public ReplaceFileHandler(IngestServiceBean ingestService, DataFileServiceBean datafileService,
                              EjbDataverseEngine commandEngine, DataverseRequestServiceBean dvRequestService) {
        this.ingestService = ingestService;
        this.datafileService = datafileService;
        this.commandEngine = commandEngine;
        this.dvRequestService = dvRequestService;
    }

    // -------------------- LOGIC --------------------

    public DataFile createDataFile(Dataset dataset,
                               byte[] newFileContent,
                               String newFileName,
                               String newFileContentType) {

        DatasetVersion datasetDraft = dataset.getEditVersion();

        return createDataFile(dataset, newFileContent, newFileName, newFileContentType, datasetDraft);
    }

    public void replaceFile(DataFile fileToBeReplaced,
                            Dataset dataset,
                            DataFile newFile) {

        DataverseRequest dataverseRequest = dvRequestService.getDataverseRequest();
        DatasetVersion datasetDraft = dataset.getEditVersion();

        ingestService.saveAndAddFilesToDataset(datasetDraft, Lists.newArrayList(newFile), new DataAccess());

        deleteFileFromEntities(datasetDraft, fileToBeReplaced);

        if (!StringUtils.isEmpty(fileToBeReplaced.getUnf())) {
            ingestService.recalculateDatasetVersionUNF(datasetDraft);
        }

        if (fileToBeReplaced.getRootDataFileId().equals(DataFile.ROOT_DATAFILE_ID_DEFAULT)) {
            fileToBeReplaced.setRootDataFileId(fileToBeReplaced.getId());
            datafileService.save(fileToBeReplaced);
        }

        UpdateDatasetVersionCommand updateCmd = new UpdateDatasetVersionCommand(dataset,
                                                                                dataverseRequest,
                                                                                datasetDraft.cloneDatasetVersion());
        updateCmd.setValidateLenient(true);

        Try.of(() -> commandEngine.submit(updateCmd))
                .onFailure(throwable -> logger.log(Level.FINE," ",throwable))
                .getOrElseThrow(() -> new RuntimeException(BundleUtil.getStringFromBundle("file.addreplace.error.add.add_file_error")));

        ingestService.startIngestJobsForDataset(dataset, dataverseRequest.getAuthenticatedUser());

    }


    private DataFile createDataFile(Dataset dataset, byte[] newFileContent, String newFileName, String newFileContentType, DatasetVersion datasetDraft) {
        List<DataFile> dataFile = Try.of(() -> datafileService.createDataFiles(datasetDraft,
                                                                               new ByteArrayInputStream(newFileContent),
                                                                               newFileName,
                                                                               newFileContentType))
                .onFailure(throwable -> cleanupTemporaryDatasetFiles(datasetDraft, dataset))
                .getOrElseThrow(throwable -> new RuntimeException(BundleUtil.getStringFromBundle("file.addreplace.error.ingest_create_file_err")
                                                                          + " " + throwable.getMessage()));
        return dataFile.get(0);
    }

    // -------------------- PRIVATE --------------------

    private boolean cleanupTemporaryDatasetFiles(DatasetVersion datasetVersion, Dataset dataset) {
        boolean draftCleaned = datasetVersion.getFileMetadatas().removeIf(fm -> fm.getDataFile().getId() == null);
        boolean datasetCleaned = dataset.getFiles().removeIf(dataFile -> dataFile.getId() == null);

        return draftCleaned && datasetCleaned;
    }

    private boolean deleteFileFromEntities(DatasetVersion datasetVersion, DataFile fileToRemove) {

        if (datasetVersion.getId() != null) {
            datafileService.removeFileMetadata(fileToRemove.getFileMetadata());
        }

        return datasetVersion.getFileMetadatas().removeIf(fileMetadata -> fileMetadata.getDataFile().equals(fileToRemove));

    }
}
