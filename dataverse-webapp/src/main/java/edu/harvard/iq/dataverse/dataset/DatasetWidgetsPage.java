package edu.harvard.iq.dataverse.dataset;

import edu.harvard.iq.dataverse.DatasetDao;
import edu.harvard.iq.dataverse.PermissionsWrapper;
import edu.harvard.iq.dataverse.common.BundleUtil;
import edu.harvard.iq.dataverse.dataaccess.ImageThumbConverter;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.util.FileUtil;
import edu.harvard.iq.dataverse.util.JsfHelper;
import io.vavr.Lazy;
import io.vavr.control.Option;
import io.vavr.control.Try;
import org.omnifaces.cdi.ViewScoped;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.file.UploadedFile;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@ViewScoped
@Named("DatasetWidgetsPage")
public class DatasetWidgetsPage implements java.io.Serializable {

    private static final Logger logger = Logger.getLogger(DatasetWidgetsPage.class.getCanonicalName());

    @EJB
    private DatasetDao datasetDao;

    @Inject
    private PermissionsWrapper permissionsWrapper;

    @Inject
    private DatasetService datasetService;

    @Inject
    private DatasetThumbnailService datasetThumbnailService;

    @Inject
    private ImageThumbConverter imageThumbConverter;

    private Long datasetId;
    private Dataset dataset;
    private List<DatasetThumbnail> datasetThumbnails;
    /**
     * A preview image of either the current or (potentially unsaved) future
     * thumbnail.
     */
    private DatasetThumbnail datasetThumbnail;
    private DataFile datasetFileThumbnailToSwitchTo;
    private Option<Lazy<DatasetThumbnail>> thumbnailOperation = Option.none();

    private final boolean considerDatasetLogoAsCandidate = false;

    public String init() {
        if (datasetId == null || datasetId.intValue() <= 0) {
            return permissionsWrapper.notFound();
        }
        dataset = datasetDao.find(datasetId);
        if (dataset == null) {
            return permissionsWrapper.notFound();
        }
        /**
         * @todo Consider changing this to "can issue"
         * UpdateDatasetThumbnailCommand since it's really the only update you
         * can do from this page.
         */
        if (!permissionsWrapper.canCurrentUserUpdateDataset(dataset)) {
            return permissionsWrapper.notAuthorized();
        }
        if (datasetDao.isInReview(dataset) && !permissionsWrapper.canUpdateAndPublishDataset(dataset)) {
            return permissionsWrapper.notAuthorized();
        }


        datasetThumbnails = datasetThumbnailService.getThumbnailCandidates(dataset, considerDatasetLogoAsCandidate);
        datasetThumbnail = datasetThumbnailService.getThumbnail(dataset);
        if (datasetThumbnail != null) {
            DataFile dataFile = datasetThumbnail.getDataFile();
            if (dataFile != null) {
                datasetFileThumbnailToSwitchTo = dataFile;
            }
        }
        return null;
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

        thumbnailOperation = Option.of(Lazy.of(() -> datasetService.changeDatasetThumbnail(dataset, datasetFileThumbnailToSwitchTo)));

        String base64image = imageThumbConverter.getImageThumbnailAsBase64(datasetFileThumbnailToSwitchTo, ImageThumbConverter.DEFAULT_CARDIMAGE_SIZE);
        datasetThumbnail = new DatasetThumbnail(base64image, datasetFileThumbnailToSwitchTo);
    }

    public void flagDatasetThumbnailForRemoval() {
        logger.fine("flagDatasetThumbnailForRemoval");
        thumbnailOperation = Option.of(Lazy.of(() -> datasetService.removeDatasetThumbnail(dataset)));

        datasetFileThumbnailToSwitchTo = null;
        datasetThumbnail = null;
    }

    public void handleImageFileUpload(FileUploadEvent event) {
        logger.fine("handleImageFileUpload clicked");
        UploadedFile uploadedFile = event.getFile();
        try {
            InputStream uploadedFileInputstream = uploadedFile.getInputStream();

            thumbnailOperation = Option.of(Lazy.of(() -> datasetService.changeDatasetThumbnail(dataset, uploadedFileInputstream)));
        } catch (IOException ex) {
            String error = "Unexpected error while uploading file.";
            logger.warning("Problem uploading dataset thumbnail to dataset id " + dataset.getId() + ". " + error + " . Exception: " + ex);
            return;
        }
        File file = null;
        try {
            file = FileUtil.inputStreamToFile(uploadedFile.getInputStream());

            String base64image = imageThumbConverter.generateImageThumbnailFromFileAsBase64(file, ImageThumbConverter.DEFAULT_CARDIMAGE_SIZE);
            if (base64image != null) {
                datasetThumbnail = new DatasetThumbnail(base64image, datasetFileThumbnailToSwitchTo);
            } else {
                Logger.getLogger(DatasetWidgetsPage.class.getName()).log(Level.SEVERE, "Failed to produce a thumbnail from the uploaded dataset logo.");
            }
        } catch (IOException ex) {
            Logger.getLogger(DatasetWidgetsPage.class.getName()).log(Level.SEVERE, null, ex);
            return;
        } finally {
            file.delete();
        }
    }

    public String save() {
        if (thumbnailOperation.isEmpty()) {
            logger.fine("The user clicked saved without making any changes.");
            return redirectToDatasetPage();
        }

        Lazy<DatasetThumbnail> lazyOperation = thumbnailOperation.get();

        Try<DatasetThumbnail> executedOperation = Try.of(lazyOperation::get);

        if (executedOperation.isSuccess()) {
            JsfHelper.addFlashSuccessMessage(BundleUtil.getStringFromBundle("dataset.thumbnailsAndWidget.success"));
            return redirectToDatasetPage();
        }

        executedOperation.onFailure(this::handleThumbnailExceptions);
        return "";
    }

    public String redirectToDatasetPage() {
        return "/dataset.xhtml?persistentId=" + dataset.getGlobalIdString() + "&faces-redirect=true";
    }

    private void handleThumbnailExceptions(Throwable exception) {

        logger.log(Level.SEVERE, "There was a problem with executing thumbnail command", exception);
        JsfHelper.addFlashErrorMessage(BundleUtil.getStringFromBundle("dataset.thumbnailsAndWidget.fail"));
    }

}
