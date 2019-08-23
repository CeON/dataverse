package edu.harvard.iq.dataverse;

import com.google.common.collect.Lists;
import edu.harvard.iq.dataverse.common.BundleUtil;
import edu.harvard.iq.dataverse.datasetutility.AddReplaceFileHelper;
import edu.harvard.iq.dataverse.datasetutility.FileReplaceException;
import edu.harvard.iq.dataverse.datasetutility.FileReplacePageHelper;
import edu.harvard.iq.dataverse.engine.command.impl.UpdateDatasetVersionCommand;
import edu.harvard.iq.dataverse.ingest.IngestServiceBean;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.datafile.FileMetadata;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.user.Permission;
import edu.harvard.iq.dataverse.util.JsfHelper;
import io.vavr.control.Try;
import org.apache.commons.lang.StringUtils;

import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.Serializable;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@ViewScoped
@Named("ReplaceDatafilesPage")
public class ReplaceDatafilesPage implements Serializable {

    private static final Logger logger = Logger.getLogger(ReplaceDatafilesPage.class.getCanonicalName());

    private PermissionsWrapper permissionsWrapper;
    private PermissionServiceBean permissionService;
    private DatasetServiceBean datasetService;
    private DataFileServiceBean datafileService;
    private IngestServiceBean ingestService;
    private EjbDataverseEngine commandEngine;
    private DataverseRequestServiceBean dvRequestService;

    private long datasetId;
    private long fileId;
    private Dataset dataset;
    private DataFile fileToBeReplaced;
    private FileReplacePageHelper fileReplacePageHelper;

    // -------------------- CONSTRUCTORS --------------------

    @Deprecated /* JEE requirement*/
    public ReplaceDatafilesPage() {
    }

    @Inject
    public ReplaceDatafilesPage(PermissionsWrapper permissionsWrapper, PermissionServiceBean permissionService, DatasetServiceBean datasetService,
                                DataFileServiceBean datafileService, IngestServiceBean ingestService, EjbDataverseEngine commandEngine, DataverseRequestServiceBean dvRequestService) {
        this.permissionsWrapper = permissionsWrapper;
        this.permissionService = permissionService;
        this.datasetService = datasetService;
        this.datafileService = datafileService;
        this.ingestService = ingestService;
        this.commandEngine = commandEngine;
        this.dvRequestService = dvRequestService;
    }

// -------------------- GETTERS --------------------

    public long getDatasetId() {
        return datasetId;
    }

    public long getFileId() {
        return fileId;
    }

    public DataFile getFileToBeReplaced() {
        return fileToBeReplaced;
    }

    public Dataset getDataset() {
        return dataset;
    }

    public FileReplacePageHelper getFileReplacePageHelper() {
        return fileReplacePageHelper;
    }

    // -------------------- LOGIC --------------------

    public String init() {
        dataset = datasetService.find(datasetId);
        fileToBeReplaced = datafileService.find(fileId);

        String permissionError = checkPermissions(dataset, fileToBeReplaced);

        if (!permissionError.isEmpty()) {
            return permissionError;
        }


        AddReplaceFileHelper addReplaceFileHelper = new AddReplaceFileHelper(dvRequestService.getDataverseRequest(),
                                                                             ingestService,
                                                                             datafileService,
                                                                             permissionService,
                                                                             commandEngine);

        fileReplacePageHelper = new FileReplacePageHelper(addReplaceFileHelper,
                                                          dataset,
                                                          fileToBeReplaced);

        return StringUtils.EMPTY;
    }

    public String saveReplacement() {
        try {
            return saveReplacementFile();
        } catch (FileReplaceException ex) {
            String errMsg = ex.getMessage();
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, BundleUtil.getStringFromBundle("dataset.save.fail"), errMsg));
            logger.log(Level.SEVERE, "Dataset save failed for replace operation: {0}", errMsg);
            return StringUtils.EMPTY;
        }
    }

    /**
     * Save for File Replace operations
     *
     * @return
     * @throws FileReplaceException
     */
    private String saveReplacementFile() throws FileReplaceException {

        if (!fileReplacePageHelper.wasPhase1Successful()) {
            throw new FileReplaceException("Save should only be called when a replacement file has been chosen.  (Phase 1 has to have completed)");

        }

        if (fileReplacePageHelper.runSaveReplacementFile_Phase2()) {
            JsfHelper.addFlashSuccessMessage(BundleUtil.getStringFromBundle("file.message.replaceSuccess"));

            return returnToFileLandingPageAfterReplace(fileReplacePageHelper.getFirstNewlyAddedFile());
        } else {

            String errMsg = fileReplacePageHelper.getErrorMessages();

            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, BundleUtil.getStringFromBundle("dataset.save.fail"), errMsg));
            logger.severe("Dataset save failed for replace operation: " + errMsg);
            return StringUtils.EMPTY;
        }

    }

    public List<FileMetadata> getFileMetadatas() {

        if (fileReplacePageHelper.wasPhase1Successful()) {
            logger.fine("Replace: File metadatas 'list' of 1 from the fileReplacePageHelper.");
            return fileReplacePageHelper.getNewFileMetadatasBeforeSave();
        }

        return Lists.newArrayList();
    }

    public boolean isLockedFromEdits() {

        return Try.of(() -> permissionService.checkEditDatasetLock(dataset, dvRequestService.getDataverseRequest(),
                                                                   new UpdateDatasetVersionCommand(dataset, dvRequestService.getDataverseRequest())))
                .getOrElse(true);
    }

    public String returnToFileLandingPage() {
        Long fileId = fileReplacePageHelper.getFileToReplace().getId();

        if (dataset.getLatestVersion().isDraft()) {
            return "/file.xhtml?fileId=" + fileId + "&version=DRAFT&faces-redirect=true";
        }
        return "/file.xhtml?fileId=" + fileId + "&faces-redirect=true";

    }

    // -------------------- PRIVATE --------------------

    private String checkPermissions(Dataset dataset, DataFile fileToBeReplaced) {

        if (dataset == null || dataset.isHarvested()) {
            return permissionsWrapper.notFound();
        }

        DatasetVersion workingVersion = dataset.getEditVersion();

        if (!workingVersion.isDraft()) {
            return permissionsWrapper.notFound();
        }

        if (!permissionService.on(dataset).has(Permission.EditDataset)) {
            return permissionsWrapper.notAuthorized();
        }

        if (fileToBeReplaced == null) {
            return permissionsWrapper.notFound();
        }

        return StringUtils.EMPTY;
    }

    private String returnToFileLandingPageAfterReplace(DataFile newFile) {

        if (newFile == null) {
            throw new NullPointerException("newFile cannot be null!");
        }
        //Long datasetVersionId = newFile.getOwner().getLatestVersion().getId();
        return "/file.xhtml?fileId=" + newFile.getId() + "&version=DRAFT&faces-redirect=true";
    }

    // -------------------- SETTERS --------------------

    public void setDataset(Dataset dataset) {
        this.dataset = dataset;
    }

    public void setDatasetId(long datasetId) {
        this.datasetId = datasetId;
    }

    public void setFileId(long fileId) {
        this.fileId = fileId;
    }
}
