package edu.harvard.iq.dataverse;

import edu.harvard.iq.dataverse.dataset.DatasetThumbnail;
import edu.harvard.iq.dataverse.dataset.DatasetThumbnailService;
import edu.harvard.iq.dataverse.engine.command.exception.CommandException;
import edu.harvard.iq.dataverse.engine.command.impl.UpdateDatasetThumbnailCommand;
import edu.harvard.iq.dataverse.engine.command.impl.UpdateDatasetThumbnailCommand.UserIntent;
import edu.harvard.iq.dataverse.thumbnail.InputStreamWrapper;
import edu.harvard.iq.dataverse.thumbnail.Thumbnail;
import edu.harvard.iq.dataverse.thumbnail.ThumbnailGeneratorManager;
import edu.harvard.iq.dataverse.thumbnail.ThumbnailUtil;
import edu.harvard.iq.dataverse.thumbnail.Thumbnail.ThumbnailSize;
import edu.harvard.iq.dataverse.engine.command.impl.UpdateDatasetVersionCommand;
import edu.harvard.iq.dataverse.util.BundleUtil;
import edu.harvard.iq.dataverse.util.JsfHelper;
import org.apache.commons.lang3.StringUtils;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.UploadedFile;

import javax.ejb.EJB;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

@ViewScoped
@Named("DatasetWidgetsPage")
public class DatasetWidgetsPage implements java.io.Serializable {

    private static final Logger logger = Logger.getLogger(DatasetWidgetsPage.class.getCanonicalName());

    @EJB
    DatasetServiceBean datasetService;

    @EJB
    EjbDataverseEngine commandEngine;

    @Inject
    DataverseRequestServiceBean dvRequestService;
    
    @Inject
    private DatasetThumbnailService datasetThumbnailService;
    @Inject
    private ThumbnailGeneratorManager thumbnailGenerator;

    private Long datasetId;
    private Dataset dataset;
    private List<DatasetThumbnail> datasetThumbnails;
    /**
     * A preview image of either the current or (potentially unsaved) future
     * thumbnail.
     */
    private DatasetThumbnail datasetThumbnail;
    private DataFile datasetFileThumbnailToSwitchTo;
    
    private UserIntent userIntent;
    private UploadedFile uploadedFile;

    @Inject
    PermissionsWrapper permissionsWrapper;

    public String init() {
        if (datasetId == null || datasetId.intValue() <= 0) {
            return permissionsWrapper.notFound();
        }
        dataset = datasetService.find(datasetId);
        if (dataset == null) {
            return permissionsWrapper.notFound();
        }
        if (!permissionsWrapper.canIssueCommand(dataset, UpdateDatasetVersionCommand.class)) {
            return permissionsWrapper.notAuthorized();
        }
        datasetThumbnails = datasetThumbnailService.getThumbnailCandidates(dataset, false);
        datasetThumbnail = datasetThumbnailService.getThumbnailBase64(dataset).orElse(null);
        if (datasetThumbnail != null) {
            DataFile dataFile = datasetThumbnail.getDataFile();
            if (dataFile != null) {
                datasetFileThumbnailToSwitchTo = dataFile;
            }
        }
        
        return StringUtils.EMPTY;
    }

    public Long getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(Long datasetId) {
        this.datasetId = datasetId;
    }

    public Dataset getDataset() {
        return dataset;
    }

    public void setDataset(Dataset dataset) {
        this.dataset = dataset;
    }

    public List<DatasetThumbnail> getDatasetThumbnails() {
        return datasetThumbnails;
    }

    public DatasetThumbnail getDatasetThumbnail() {
        return datasetThumbnail;
    }

    public void setDatasetThumbnail(DatasetThumbnail datasetThumbnail) {
        this.datasetThumbnail = datasetThumbnail;
    }

    public DataFile getDatasetFileThumbnailToSwitchTo() {
        return datasetFileThumbnailToSwitchTo;
    }

    public void setDatasetFileThumbnailToSwitchTo(DataFile datasetFileThumbnailToSwitchTo) {
        this.datasetFileThumbnailToSwitchTo = datasetFileThumbnailToSwitchTo;
    }

    public void setDataFileAsThumbnail() {
        logger.fine("setDataFileAsThumbnail clicked");
        userIntent = UserIntent.setDatasetFileAsThumbnail;
        datasetThumbnail = retrieveThumbnailOfFile(datasetFileThumbnailToSwitchTo);
    }

    public void flagDatasetThumbnailForRemoval() {
        logger.fine("flagDatasetThumbnailForRemoval");
        userIntent = UserIntent.removeThumbnail;
        datasetFileThumbnailToSwitchTo = null;
        datasetThumbnail = null;
    }

    public void handleImageFileUpload(FileUploadEvent event) throws IOException {
        logger.fine("handleImageFileUpload clicked");
        uploadedFile = event.getFile();
        
        InputStreamWrapper is = new InputStreamWrapper(uploadedFile.getInputstream(), uploadedFile.getSize(), uploadedFile.getContentType());
        Thumbnail thumbnail = thumbnailGenerator.generateThumbnail(is, ThumbnailSize.CARD);
        String base64Thumbnail = ThumbnailUtil.thumbnailAsBase64(thumbnail);

        userIntent = UserIntent.setNonDatasetFileAsThumbnail;
        datasetFileThumbnailToSwitchTo = null;
        datasetThumbnail = new DatasetThumbnail(base64Thumbnail);
        
    }

    public String save() {
        logger.fine("save clicked");
        if (userIntent == null) {
            logger.fine("The user clicked saved without making any changes.");
            return null;
        }
        
        try {
            UpdateDatasetThumbnailCommand updateDatasetThumbnailCommand = new UpdateDatasetThumbnailCommand(
                    dvRequestService.getDataverseRequest(), dataset, userIntent,
                    datasetFileThumbnailToSwitchTo == null ? null : datasetFileThumbnailToSwitchTo.getId(),
                    uploadedFile == null ? null : uploadedFile.getInputstream());
            
            commandEngine.submit(updateDatasetThumbnailCommand);
            JsfHelper.addFlashSuccessMessage(BundleUtil.getStringFromBundle("dataset.thumbnailsAndWidget.success"));
            return "/dataset.xhtml?persistentId=" + dataset.getGlobalIdString() + "&faces-redirect=true";
        } catch (CommandException ex) {
            String error = ex.getLocalizedMessage();
            JsfHelper.addFlashErrorMessage(error);
            return null;
        } catch (IOException ex) {
            String error = "Unexpected error while uploading file.";
            logger.warning("Problem uploading dataset thumbnail to dataset id " + dataset.getId() + ". " + error + " . Exception: " + ex);
            return null;
        }
    }

    public String cancel() {
        logger.fine("cancel clicked");
        return "/dataset.xhtml?persistentId=" + dataset.getGlobalIdString() + "&faces-redirect=true";
    }

    private DatasetThumbnail retrieveThumbnailOfFile(DataFile dataFile) {
        for (DatasetThumbnail thumb: datasetThumbnails) {
            if (thumb.getDataFile().equals(dataFile)) {
                return thumb;
            }
        }
        return null;
    }
}
