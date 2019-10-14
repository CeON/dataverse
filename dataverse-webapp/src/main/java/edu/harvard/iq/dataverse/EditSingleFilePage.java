package edu.harvard.iq.dataverse;

import edu.harvard.iq.dataverse.api.AbstractApiBean;
import edu.harvard.iq.dataverse.common.BundleUtil;
import edu.harvard.iq.dataverse.dataaccess.DataAccess;
import edu.harvard.iq.dataverse.dataaccess.ImageThumbConverter;
import edu.harvard.iq.dataverse.dataset.DatasetThumbnail;
import edu.harvard.iq.dataverse.dataset.DatasetUtil;
import edu.harvard.iq.dataverse.engine.command.Command;
import edu.harvard.iq.dataverse.engine.command.exception.CommandException;
import edu.harvard.iq.dataverse.engine.command.impl.DeleteDataFileCommand;
import edu.harvard.iq.dataverse.engine.command.impl.UpdateDatasetThumbnailCommand;
import edu.harvard.iq.dataverse.engine.command.impl.UpdateDatasetVersionCommand;
import edu.harvard.iq.dataverse.ingest.IngestServiceBean;
import edu.harvard.iq.dataverse.license.TermsOfUseFormMapper;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.datafile.DataFileCategory;
import edu.harvard.iq.dataverse.persistence.datafile.DataFileTag;
import edu.harvard.iq.dataverse.persistence.datafile.FileMetadata;
import edu.harvard.iq.dataverse.persistence.datafile.license.FileTermsOfUse;
import edu.harvard.iq.dataverse.persistence.datafile.license.TermsOfUseForm;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetLock;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.persistence.user.Permission;
import edu.harvard.iq.dataverse.provenance.ProvPopupFragmentBean;
import edu.harvard.iq.dataverse.search.IndexServiceBean;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean.Key;
import edu.harvard.iq.dataverse.util.JsfHelper;
import edu.harvard.iq.dataverse.util.SystemConfig;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.lang.StringUtils;

import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.faces.application.FacesMessage;
import javax.faces.event.AjaxBehaviorEvent;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.File;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import static edu.harvard.iq.dataverse.util.JsfHelper.JH;


/**
 * @author Leonid Andreev
 */
@ViewScoped
@Named("EditSingleFilePage")
public class EditSingleFilePage implements java.io.Serializable {

    private static final Logger logger = Logger.getLogger(EditDatafilesPage.class.getCanonicalName());

    @EJB
    DatasetServiceBean datasetService;
    @EJB
    DataFileServiceBean datafileService;
    @EJB
    PermissionServiceBean permissionService;
    @EJB
    IngestServiceBean ingestService;
    @EJB
    EjbDataverseEngine commandEngine;
    @Inject
    DataverseSession session;
    @EJB
    SettingsServiceBean settingsService;
    @EJB
    SystemConfig systemConfig;
    @EJB
    IndexServiceBean indexService;
    @Inject
    DataverseRequestServiceBean dvRequestService;
    @Inject
    PermissionsWrapper permissionsWrapper;
    @Inject
    FileDownloadHelper fileDownloadHelper;
    @Inject
    ProvPopupFragmentBean provPopupFragmentBean;

    @Inject
    private TermsOfUseFormMapper termsOfUseFormMapper;

    private Dataset dataset = new Dataset();

    private String editedFileIdString = null;
    private Long selectedFileId;
    private List<FileMetadata> fileMetadatas = new ArrayList<>();


    private Long ownerId;
    private Long versionId;
    private List<DataFile> newFiles = new ArrayList<>();
    private DatasetVersion workingVersion;
    private DatasetVersion clone;
    private String dropBoxSelection = "";
    private boolean datasetUpdateRequired = false;
    private boolean tabularDataTagsUpdated = false;

    private String persistentId;

    private String versionString = "";


    private boolean saveEnabled = false;

    private DataFile singleFile = null;

    public DataFile getSingleFile() {
        return singleFile;
    }

    public void setSingleFile(DataFile singleFile) {
        this.singleFile = singleFile;
    }

    public String getSelectedFileIds() {
        return editedFileIdString;
    }

    public void setSelectedFileIds(String selectedFileIds) {
        editedFileIdString = selectedFileIds;
    }

    public List<FileMetadata> getFileMetadatas() {

        if (fileMetadatas != null) {
            logger.fine("Returning a list of " + fileMetadatas.size() + " file metadatas.");
        } else {
            logger.fine("File metadatas list hasn't been initialized yet.");
        }
        // [experimental]
        // this would be a way to hide any already-uploaded files from the page
        // while a new upload is happening:
        // (the uploadStarted button on the page needs the update="filesTable"
        // attribute added for this to work)
        //if (uploadInProgress) {
        //    return null;
        //}

        return fileMetadatas;
    }

    public void setFileMetadatas(List<FileMetadata> fileMetadatas) {
        this.fileMetadatas = fileMetadatas;
    }


    public void reset() {
        // ?
    }

    public String getGlobalId() {
        return persistentId;
    }

    public String getPersistentId() {
        return persistentId;
    }

    public void setPersistentId(String persistentId) {
        this.persistentId = persistentId;
    }

    public String getDropBoxSelection() {
        return dropBoxSelection;
    }

    public String getDropBoxKey() {
        // Site-specific DropBox application registration key is configured
        // via a JVM option under glassfish.
        //if (true)return "some-test-key";  // for debugging

        return settingsService.getValueForKey(Key.DropboxKey);
    }

    public void setDropBoxSelection(String dropBoxSelection) {
        this.dropBoxSelection = dropBoxSelection;
    }

    public Dataset getDataset() {
        return dataset;
    }

    public void setDataset(Dataset dataset) {
        this.dataset = dataset;
    }

    public DatasetVersion getWorkingVersion() {
        return workingVersion;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public Long getVersionId() {
        return versionId;
    }

    public void setVersionId(Long versionId) {
        this.versionId = versionId;
    }

    public String init() {
        fileMetadatas = new ArrayList<>();

        if (dataset.getId() != null) {
            // Set Working Version and Dataset by Dataset Id and Version
            dataset = datasetService.find(dataset.getId());
            // Is the Dataset harvested? (because we don't allow editing of harvested
            // files!)
            if (dataset == null || dataset.isHarvested()) {
                return permissionsWrapper.notFound();
            }
        } else {
            return permissionsWrapper.notFound();
        }


        workingVersion = dataset.getEditVersion();
        clone = workingVersion.cloneDatasetVersion();
        if (workingVersion == null || !workingVersion.isDraft()) {
            // Sorry, we couldn't find/obtain a draft version for this dataset!
            return permissionsWrapper.notFound();
        }

        // Check if they have permission to modify this dataset:

        if (!permissionService.on(dataset).has(Permission.EditDataset)) {
            return permissionsWrapper.notAuthorized();
        }
        if (datasetService.isInReview(dataset) && !permissionsWrapper.canUpdateAndPublishDataset(dvRequestService.getDataverseRequest(), dataset)) {
            return permissionsWrapper.notAuthorized();
        }

        if (StringUtils.isNotEmpty(editedFileIdString)) {
                try {
                    Long fileId = Long.parseLong(editedFileIdString);
                    singleFile = datafileService.find(fileId);
                    selectedFileId = fileId;
                } catch (NumberFormatException nfe) {
                    // do nothing...
                    logger.warning("Couldn't parse editedFileIdString =" + editedFileIdString + " to Long");
                    JH.addMessage(FacesMessage.SEVERITY_ERROR, "File id is not a number!");
                    return "";
                }
        }

        if (singleFile == null) {
            logger.fine("No numeric file ids supplied to the page, in the edit mode. Redirecting to the 404 page.");
            // If no valid file IDs specified, send them to the 404 page...
            return permissionsWrapper.notFound();
        }

        logger.fine("The page is called with " + selectedFileId + " file id.");

        populateFileMetadatas();

        // and if no filemetadatas can be found for the specified file ids
        // and version id - same deal, send them to the "not found" page.
        // (at least for now; ideally, we probably want to show them a page
        // with a more informative error message; something alonog the lines
        // of - could not find the files for the ids specified; or, these
        // datafiles are not present in the version specified, etc.
        if (fileMetadatas.size() < 1) {
            return permissionsWrapper.notFound();
        }

        if (fileMetadatas.get(0).getDatasetVersion().getId() != null) {
            versionString = "DRAFT";
        }

        saveEnabled = true;

        if (settingsService.isTrueForKey(SettingsServiceBean.Key.PublicInstall)) {
            JH.addMessage(FacesMessage.SEVERITY_WARN, getBundleString("dataset.message.publicInstall"));
        }

        return null;
    }


    private List<FileMetadata> selectedFiles;

    public List<FileMetadata> getSelectedFiles() {
        return selectedFiles;
    }

    public void setSelectedFiles(List<FileMetadata> selectedFiles) {
        this.selectedFiles = selectedFiles;
    }

    public String getVersionString() {
        return versionString;
    }

    public void setVersionString(String versionString) {
        this.versionString = versionString;
    }

    private List<FileMetadata> filesToBeDeleted = new ArrayList<>();


    /**
     * @param msgName - from the bundle e.g. "file.deleted.success"
     * @return
     */
    private String getBundleString(String msgName) {

        return BundleUtil.getStringFromBundle(msgName);
    }


    public void deleteFiles() {
        logger.fine("entering bulk file delete (EditDataFilesPage)");

        String fileNames = null;
        for (FileMetadata fmd : this.getSelectedFiles()) {
            // collect the names of the files,
            // to show in the success message:
            if (fileNames == null) {
                fileNames = fmd.getLabel();
            } else {
                fileNames = fileNames.concat(", " + fmd.getLabel());
            }
        }

        for (FileMetadata markedForDelete : this.getSelectedFiles()) {
            logger.fine("delete requested on file " + markedForDelete.getLabel());
            logger.fine("file metadata id: " + markedForDelete.getId());
            logger.fine("datafile id: " + markedForDelete.getDataFile().getId());
            logger.fine("page is in single file edit mode ");

            // has this filemetadata been saved already? (or is it a brand new
            // filemetadata, created as part of a brand new version, created when
            // the user clicked 'delete', that hasn't been saved in the db yet?)
            if (markedForDelete.getId() != null) {
                logger.fine("this is a filemetadata from an existing draft version");
                // so all we remove is the file from the fileMetadatas (from the
                // file metadatas attached to the editVersion, and from the
                // display list of file metadatas that are being edited)
                // and let the delete be handled in the command (by adding it to the
                // filesToBeDeleted list):

                dataset.getEditVersion().getFileMetadatas().remove(markedForDelete);
                fileMetadatas.remove(markedForDelete);
                filesToBeDeleted.add(markedForDelete);
            } else {
                logger.fine("this is a brand-new (unsaved) filemetadata");
                // ok, this is a brand-new DRAFT version.

                // if (mode != FileEditMode.CREATE) {
                // If the bean is in the 'CREATE' mode, the page is using
                // dataset.getEditVersion().getFileMetadatas() directly,
                // so there's no need to delete this meta from the local
                // fileMetadatas list. (but doing both just adds a no-op and won't cause an
                // error)

                // 1. delete the filemetadata from the local display list:
                removeFileMetadataFromList(fileMetadatas, markedForDelete);
                // 2. delete the filemetadata from the version:
                removeFileMetadataFromList(dataset.getEditVersion().getFileMetadatas(), markedForDelete);
            }


            if (markedForDelete.getDataFile().getId() == null) {
                logger.fine("this is a brand new file.");
                // the file was just added during this step, so in addition to
                // removing it from the fileMetadatas lists (above), we also remove it from
                // the newFiles list and the dataset's files, so it never gets saved.

                removeDataFileFromList(dataset.getFiles(), markedForDelete.getDataFile());
            }
        }
        if (fileNames != null) {
            String successMessage = getBundleString("file.deleted.success");
            logger.fine(successMessage);
            successMessage = successMessage.replace("{0}", fileNames);
            JsfHelper.addFlashMessage(successMessage);
        }
    }

    private void removeFileMetadataFromList(List<FileMetadata> fmds, FileMetadata fmToDelete) {
        Iterator<FileMetadata> fmit = fmds.iterator();
        while (fmit.hasNext()) {
            FileMetadata fmd = fmit.next();
            if (fmToDelete.getDataFile().getStorageIdentifier().equals(fmd.getDataFile().getStorageIdentifier())) {
                fmit.remove();
                break;
            }
        }
    }

    private void removeDataFileFromList(List<DataFile> dfs, DataFile dfToDelete) {
        Iterator<DataFile> dfit = dfs.iterator();
        while (dfit.hasNext()) {
            DataFile df = dfit.next();
            if (dfToDelete.getStorageIdentifier().equals(df.getStorageIdentifier())) {
                dfit.remove();
                break;
            }
        }
    }

    public String save() {
        // Once all the filemetadatas pass the validation, we'll only allow the user
        // to try to save once; (this it to prevent them from creating multiple
        // DRAFT versions, if the page gets stuck in that state where it
        // successfully creates a new version, but can't complete the remaining
        // tasks. -- L.A. 4.2

        if (!saveEnabled) {
            return "";
        }


        int nOldFiles = workingVersion.getFileMetadatas().size();
        int nNewFiles = newFiles.size();
        int nExpectedFilesTotal = nOldFiles + nNewFiles;

        if (nNewFiles > 0) {
            //SEK 10/15/2018 only apply the following tests if dataset has already been saved.
            if (dataset.getId() != null) {
                Dataset lockTest = datasetService.find(dataset.getId());
                //SEK 09/19/18 Get Dataset again to test for lock just in case the user downloads the rsync script via the api while the
                // edit files page is open and has already loaded a file in http upload for Dual Mode
                if (dataset.isLockedFor(DatasetLock.Reason.DcmUpload) || lockTest.isLockedFor(DatasetLock.Reason.DcmUpload)) {
                    logger.log(Level.INFO, "Couldn''t save dataset: {0}", "DCM script has been downloaded for this dataset. Additonal files are not permitted."
                            + "");
                    populateDatasetUpdateFailureMessage();
                    return null;
                }
                for (DatasetVersion dv : lockTest.getVersions()) {
                    if (dv.isHasPackageFile()) {
                        logger.log(Level.INFO, ResourceBundle.getBundle("Bundle").getString("file.api.alreadyHasPackageFile")
                                + "");
                        populateDatasetUpdateFailureMessage();
                        return null;
                    }
                }
            }

            for (DataFile newFile : newFiles) {
                TermsOfUseForm termsOfUseForm = newFile.getFileMetadata().getTermsOfUseForm();
                FileTermsOfUse termsOfUse = termsOfUseFormMapper.mapToFileTermsOfUse(termsOfUseForm);

                newFile.getFileMetadata().setTermsOfUse(termsOfUse);
            }

            // Try to save the NEW files permanently:
            List<DataFile> filesAdded = ingestService.saveAndAddFilesToDataset(workingVersion, newFiles, new DataAccess());

            // reset the working list of fileMetadatas, as to only include the ones
            // that have been added to the version successfully:
            fileMetadatas.clear();
            for (DataFile addedFile : filesAdded) {
                fileMetadatas.add(addedFile.getFileMetadata());
            }
        }

        Boolean provJsonChanges = false;

        if (settingsService.isTrueForKey(SettingsServiceBean.Key.ProvCollectionEnabled)) {
            Boolean provFreeChanges = provPopupFragmentBean.updatePageMetadatasWithProvFreeform(fileMetadatas);

            try {
                // Note that the user may have uploaded provenance metadata file(s)
                // for some of the new files that have since failed to be permanently saved
                // in storage (in the ingestService.saveAndAddFilesToDataset() step, above);
                // these files have been dropped from the fileMetadatas list, and we
                // are not adding them to the dataset; but the
                // provenance update set still has entries for these failed files,
                // so we are passing the fileMetadatas list to the saveStagedProvJson()
                // method below - so that it doesn't attempt to save the entries
                // that are no longer valid.
                provJsonChanges = provPopupFragmentBean.saveStagedProvJson(false, fileMetadatas);
            } catch (AbstractApiBean.WrappedResponse ex) {
                JsfHelper.addFlashErrorMessage(getBundleString("file.metadataTab.provenance.error"));
                Logger.getLogger(EditDatafilesPage.class.getName()).log(Level.SEVERE, null, ex);
            }
            //Always update the whole dataset if updating prov
            //The flow that happens when datasetUpdateRequired is false has problems with doing saving actions after its merge
            //This was the simplest way to work around this issue for prov. --MAD 4.8.6.
            datasetUpdateRequired = datasetUpdateRequired || provFreeChanges || provJsonChanges;
        }

        if (workingVersion.getId() == null || datasetUpdateRequired) {
            logger.fine("issuing the dataset update command");
            // We are creating a new draft version;
            // (OR, a full update of the dataset has been explicitly requested,
            // because of the nature of the updates the user has made).
            // We'll use an Update command for this:

            //newDraftVersion = true;

            if (datasetUpdateRequired) {
                for (int i = 0; i < workingVersion.getFileMetadatas().size(); i++) {
                    for (FileMetadata fileMetadata : fileMetadatas) {
                        if (fileMetadata.getDataFile().getStorageIdentifier() != null) {
                            if (fileMetadata.getDataFile().getStorageIdentifier().equals(workingVersion.getFileMetadatas().get(i).getDataFile().getStorageIdentifier())) {
                                workingVersion.getFileMetadatas().set(i, fileMetadata);
                            }
                        }
                    }
                }


                //Moves DataFile updates from popupFragment to page for saving
                //This does not seem to collide with the tags updating below
                if (settingsService.isTrueForKey(SettingsServiceBean.Key.ProvCollectionEnabled) && provJsonChanges) {
                    HashMap<String, ProvPopupFragmentBean.UpdatesEntry> provenanceUpdates = provPopupFragmentBean.getProvenanceUpdates();
                    for (int i = 0; i < dataset.getFiles().size(); i++) {
                        for (ProvPopupFragmentBean.UpdatesEntry ue : provenanceUpdates.values()) {
                            if (ue.dataFile.getStorageIdentifier() != null) {
                                if (ue.dataFile.getStorageIdentifier().equals(dataset.getFiles().get(i).getStorageIdentifier())) {
                                    dataset.getFiles().set(i, ue.dataFile);
                                }
                            }
                        }
                    }
                }

                // Tabular data tags are assigned to datafiles, not to
                // version-specfic filemetadatas!
                // So if tabular tags have been modified, we also need to
                // refresh the list of datafiles, as found in dataset.getFiles(),
                // similarly to what we've just done, above, for the filemetadatas.
                // Otherwise, when we call UpdateDatasetCommand, it's not going
                // to update the tags in the database (issue #2798).
                // TODO: Is the above still true/is this still necessary?
                // (and why?...)

                if (tabularDataTagsUpdated) {
                    for (int i = 0; i < dataset.getFiles().size(); i++) {
                        for (FileMetadata fileMetadata : fileMetadatas) {
                            if (fileMetadata.getDataFile().getStorageIdentifier() != null) {
                                if (fileMetadata.getDataFile().getStorageIdentifier().equals(dataset.getFiles().get(i).getStorageIdentifier())) {
                                    dataset.getFiles().set(i, fileMetadata.getDataFile());
                                }
                            }
                        }
                    }
                    tabularDataTagsUpdated = false;
                }
            }

            Map<Long, String> deleteStorageLocations = null;

            if (!filesToBeDeleted.isEmpty()) {
                deleteStorageLocations = datafileService.getPhysicalFilesToDelete(filesToBeDeleted);
            }

            Command<Dataset> cmd;
            try {
                cmd = new UpdateDatasetVersionCommand(dataset, dvRequestService.getDataverseRequest(), filesToBeDeleted, clone);
                ((UpdateDatasetVersionCommand) cmd).setValidateLenient(true);
                dataset = commandEngine.submit(cmd);

            } catch (EJBException ex) {
                StringBuilder error = new StringBuilder();
                error.append(ex).append(" ");
                error.append(ex.getMessage()).append(" ");
                Throwable cause = ex;
                while (cause.getCause() != null) {
                    cause = cause.getCause();
                    error.append(cause).append(" ");
                    error.append(cause.getMessage()).append(" ");
                }
                logger.log(Level.INFO, "Couldn''t save dataset: {0}", error.toString());
                populateDatasetUpdateFailureMessage();
                return null;
            } catch (CommandException ex) {
                //FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Dataset Save Failed", " - " + ex.toString()));
                logger.log(Level.INFO, "Couldn''t save dataset: {0}", ex.getMessage());
                populateDatasetUpdateFailureMessage();
                return null;
            }

            // Have we just deleted some draft datafiles (successfully)?
            // finalize the physical file deletes:
            // (DataFileService will double-check that the datafiles no
            // longer exist in the database, before attempting to delete
            // the physical files)
            if (deleteStorageLocations != null) {
                datafileService.finalizeFileDeletes(deleteStorageLocations);
            }

            datasetUpdateRequired = false;
            saveEnabled = false;
        } else {
            // This is an existing Draft version (and nobody has explicitly
            // requested that the entire dataset is updated). So we'll try to update
            // only the filemetadatas and/or files affected, and not the
            // entire version.
            Timestamp updateTime = new Timestamp(new Date().getTime());

            workingVersion.setLastUpdateTime(updateTime);
            dataset.setModificationTime(updateTime);

            StringBuilder saveError = new StringBuilder();

            for (FileMetadata fileMetadata : fileMetadatas) {

                if (fileMetadata.getDataFile().getCreateDate() == null) {
                    fileMetadata.getDataFile().setCreateDate(updateTime);
                    fileMetadata.getDataFile().setCreator((AuthenticatedUser) session.getUser());
                }
                fileMetadata.getDataFile().setModificationTime(updateTime);
                try {
                    //DataFile savedDatafile = datafileService.save(fileMetadata.getDataFile());
                    fileMetadata = datafileService.mergeFileMetadata(fileMetadata);
                    logger.fine("Successfully saved DataFile " + fileMetadata.getLabel() + " in the database.");
                } catch (EJBException ex) {
                    saveError.append(ex).append(" ");
                    saveError.append(ex.getMessage()).append(" ");
                    Throwable cause = ex;
                    while (cause.getCause() != null) {
                        cause = cause.getCause();
                        saveError.append(cause).append(" ");
                        saveError.append(cause.getMessage()).append(" ");
                    }
                }
            }

            // Remove / delete any files that were removed
            for (FileMetadata fmd : filesToBeDeleted) {
                //  check if this file is being used as the default thumbnail
                if (fmd.getDataFile().equals(dataset.getThumbnailFile())) {
                    logger.fine("deleting the dataset thumbnail designation");
                    dataset.setThumbnailFile(null);
                }

                if (!fmd.getDataFile().isReleased()) {
                    // if file is draft (ie. new to this version, delete; otherwise just remove filemetadata object)
                    boolean deleteCommandSuccess = false;
                    Long dataFileId = fmd.getDataFile().getId();
                    String deleteStorageLocation = null;

                    if (dataFileId != null) { // is this check necessary?

                        deleteStorageLocation = datafileService.getPhysicalFileToDelete(fmd.getDataFile());

                        try {
                            commandEngine.submit(new DeleteDataFileCommand(fmd.getDataFile(), dvRequestService.getDataverseRequest()));
                            dataset.getFiles().remove(fmd.getDataFile());
                            workingVersion.getFileMetadatas().remove(fmd);
                            // added this check to handle an issue where you could not delete a file that shared a category with a new file
                            // the relationship does not seem to cascade, yet somehow it was trying to merge the filemetadata
                            // todo: clean this up some when we clean the create / update dataset methods
                            for (DataFileCategory cat : dataset.getCategories()) {
                                cat.getFileMetadatas().remove(fmd);
                            }
                            deleteCommandSuccess = true;
                        } catch (CommandException cmde) {
                            // TODO:
                            // add diagnostics reporting for individual data files that
                            // we failed to delete.
                            logger.warning("Failed to delete DataFile id=" + dataFileId + " from the database; " + cmde.getMessage());
                        }
                        if (deleteCommandSuccess) {
                            if (deleteStorageLocation != null) {
                                // Finalize the delete of the physical file
                                // (File service will double-check that the datafile no
                                // longer exists in the database, before proceeding to
                                // delete the physical file)
                                try {
                                    datafileService.finalizeFileDelete(dataFileId, deleteStorageLocation, new DataAccess());
                                } catch (IOException ioex) {
                                    logger.warning("Failed to delete the physical file associated with the deleted datafile id="
                                            + dataFileId + ", storage location: " + deleteStorageLocation);
                                }
                            }
                        }
                    }
                } else {
                    datafileService.removeFileMetadata(fmd);
                    fmd.getDataFile().getFileMetadatas().remove(fmd);
                    workingVersion.getFileMetadatas().remove(fmd);
                }
            }

            String saveErrorString = saveError.toString();
            if (saveErrorString != null && !saveErrorString.isEmpty()) {
                logger.log(Level.INFO, "Couldn''t save dataset: {0}", saveErrorString);
                populateDatasetUpdateFailureMessage();
                return null;
            }
        }

        workingVersion = dataset.getEditVersion();
        logger.fine("working version id: " + workingVersion.getId());

        JsfHelper.addFlashSuccessMessage(getBundleString("file.message.editSuccess"));

        if (CollectionUtils.isNotEmpty(fileMetadatas)) {
            // we want to redirect back to
            // the landing page. BUT ONLY if the file still exists - i.e., if
            // the user hasn't just deleted it!
            versionString = "DRAFT";
            return returnToFileLandingPage();
        }

        indexService.indexDataset(dataset, true);
        logger.fine("Redirecting to the dataset page, from the edit/upload page.");
        return returnToDraftVersion();
    }

    private void populateDatasetUpdateFailureMessage() {

        JH.addMessage(FacesMessage.SEVERITY_FATAL, getBundleString("dataset.message.filesFailure"));
    }


    private String returnToDraftVersion() {
        return "/dataset.xhtml?persistentId=" + dataset.getGlobalId().asString() + "&version=DRAFT&faces-redirect=true";
    }

    public String returnToDatasetOnly() {
        dataset = datasetService.find(dataset.getId());
        return "/dataset.xhtml?persistentId=" + dataset.getGlobalId().asString() + "&faces-redirect=true";
    }

    private String returnToFileLandingPage() {
        Long fileId = fileMetadatas.get(0).getDataFile().getId();
        if (versionString != null && versionString.equals("DRAFT")) {
            return "/file.xhtml?fileId=" + fileId + "&version=DRAFT&faces-redirect=true";
        }
        return "/file.xhtml?fileId=" + fileId + "&faces-redirect=true";

    }


    public String cancel() {
        return returnToFileLandingPage();
    }

    private HttpClient getClient() {
        return new HttpClient();
    }


    private Map<String, String> temporaryThumbnailsMap = new HashMap<>();

    public boolean isTemporaryPreviewAvailable(String fileSystemId, String mimeType) {
        if (temporaryThumbnailsMap.get(fileSystemId) != null && !temporaryThumbnailsMap.get(fileSystemId).isEmpty()) {
            return true;
        }

        if ("".equals(temporaryThumbnailsMap.get(fileSystemId))) {
            // we've already looked once - and there's no thumbnail.
            return false;
        }

        String filesRootDirectory = systemConfig.getFilesDirectory();
        String fileSystemName = filesRootDirectory + "/temp/" + fileSystemId;

        String imageThumbFileName = null;

        // ATTENTION! TODO: the current version of the method below may not be checking if files are already cached!
        if ("application/pdf".equals(mimeType)) {
            imageThumbFileName = ImageThumbConverter.generatePDFThumbnailFromFile(fileSystemName, ImageThumbConverter.DEFAULT_THUMBNAIL_SIZE);
        } else if (mimeType != null && mimeType.startsWith("image/")) {
            imageThumbFileName = ImageThumbConverter.generateImageThumbnailFromFile(fileSystemName, ImageThumbConverter.DEFAULT_THUMBNAIL_SIZE);
        }

        if (imageThumbFileName != null) {
            File imageThumbFile = new File(imageThumbFileName);
            if (imageThumbFile.exists()) {
                String previewAsBase64 = ImageThumbConverter.getImageAsBase64FromFile(imageThumbFile);
                if (previewAsBase64 != null) {
                    temporaryThumbnailsMap.put(fileSystemId, previewAsBase64);
                    return true;
                } else {
                    temporaryThumbnailsMap.put(fileSystemId, "");
                }
            }
        }

        return false;
    }

    public String getTemporaryPreviewAsBase64(String fileSystemId) {
        return temporaryThumbnailsMap.get(fileSystemId);
    }

    public boolean isLocked() {
        if (dataset != null) {
            logger.log(Level.FINE, "checking lock status of dataset {0}", dataset.getId());
            if (dataset.isLocked()) {
                // refresh the dataset and version, if the current working
                // version of the dataset is locked:
            }
            Dataset lookedupDataset = datasetService.find(dataset.getId());

            if ((lookedupDataset != null) && lookedupDataset.isLocked()) {
                logger.fine("locked!");
                return true;
            }
        }
        return false;
    }

    public boolean isThumbnailAvailable(FileMetadata fileMetadata) {
        // new and optimized logic:
        // - check download permission here (should be cached - so it's free!)
        // - only then ask the file service if the thumbnail is available/exists.
        // the service itself no longer checks download permissions.
        if (!fileDownloadHelper.canDownloadFile(fileMetadata)) {
            return false;
        }

        return datafileService.isThumbnailAvailable(fileMetadata.getDataFile());
    }

    // Methods for edit functions that are performed on one file at a time,
    // in popups that block the rest of the page:

    public boolean isDesignatedDatasetThumbnail(FileMetadata fileMetadata) {
        if (fileMetadata != null) {
            if (fileMetadata.getDataFile() != null) {
                if (fileMetadata.getDataFile().getId() != null) {
                    //if (fileMetadata.getDataFile().getOwner() != null) {
                    return fileMetadata.getDataFile().equals(dataset.getThumbnailFile());
                    //}
                }
            }
        }
        return false;
    }

    /*
     * Items for the "Designated this image as the Dataset thumbnail:
     */

    private FileMetadata fileMetadataSelectedForThumbnailPopup = null;

    /**
     * @param fm
     * @todo For consistency, we should disallow users from setting the
     * thumbnail to a restricted file. We enforce this rule in the newer
     * workflow in dataset-widgets.xhtml. The logic to show the "Set Thumbnail"
     * button is in editFilesFragment.xhtml and it would be nice to move it to
     * Java since it's getting long and a bit complicated.
     */
    public void setFileMetadataSelectedForThumbnailPopup(FileMetadata fm) {
        fileMetadataSelectedForThumbnailPopup = fm;
        alreadyDesignatedAsDatasetThumbnail = getUseAsDatasetThumbnail();

    }

    public void clearFileMetadataSelectedForThumbnailPopup() {
        fileMetadataSelectedForThumbnailPopup = null;
    }

    private boolean alreadyDesignatedAsDatasetThumbnail = false;

    public boolean getUseAsDatasetThumbnail() {

        return isDesignatedDatasetThumbnail(fileMetadataSelectedForThumbnailPopup);
    }

    public void setUseAsDatasetThumbnail(boolean useAsThumbnail) {
        if (fileMetadataSelectedForThumbnailPopup != null) {
            if (fileMetadataSelectedForThumbnailPopup.getDataFile() != null) {
                if (useAsThumbnail) {
                    dataset.setThumbnailFile(fileMetadataSelectedForThumbnailPopup.getDataFile());
                } else if (getUseAsDatasetThumbnail()) {
                    dataset.setThumbnailFile(null);
                }
            }
        }
    }

    public void saveAsDesignatedThumbnail() {
        logger.fine("saving as the designated thumbnail");
        // We don't need to do anything specific to save this setting, because
        // the setUseAsDatasetThumbnail() method, above, has already updated the
        // file object appropriately.
        // However, once the "save" button is pressed, we want to show a success message, if this is
        // a new image has been designated as such:
        if (getUseAsDatasetThumbnail() && !alreadyDesignatedAsDatasetThumbnail) {
            String successMessage = getBundleString("file.assignedDataverseImage.success");
            logger.fine(successMessage);
            successMessage = successMessage.replace("{0}", fileMetadataSelectedForThumbnailPopup.getLabel());
            JsfHelper.addFlashMessage(successMessage);

            datasetUpdateRequired = true;
        }

        // And reset the selected fileMetadata:
        fileMetadataSelectedForThumbnailPopup = null;
    }

    public void deleteDatasetLogoAndUseThisDataFileAsThumbnailInstead() {
        logger.log(Level.FINE, "For dataset id {0} the current thumbnail is from a dataset logo rather than a dataset file, blowing away the logo and using this FileMetadata id instead: {1}", new Object[]{dataset.getId(), fileMetadataSelectedForThumbnailPopup});
        /**
         * @todo Rather than deleting and merging right away, try to respect how
         * this page seems to stage actions and giving the user a chance to
         * review before clicking "Save Changes".
         */
        try {
            DatasetThumbnail datasetThumbnail = commandEngine.submit(new UpdateDatasetThumbnailCommand(dvRequestService.getDataverseRequest(), dataset, UpdateDatasetThumbnailCommand.UserIntent.setDatasetFileAsThumbnail, fileMetadataSelectedForThumbnailPopup.getDataFile().getId(), null));
            // look up the dataset again because the UpdateDatasetThumbnailCommand mutates (merges) the dataset
            dataset = datasetService.find(dataset.getId());
        } catch (CommandException ex) {
            String error = "Problem setting thumbnail for dataset id " + dataset.getId() + ".: " + ex;
            // show this error to the user?
            logger.info(error);
        }
    }

    public boolean isThumbnailIsFromDatasetLogoRatherThanDatafile() {
        DatasetThumbnail datasetThumbnail = DatasetUtil.getThumbnail(dataset);
        return datasetThumbnail != null && !datasetThumbnail.isFromDataFile();
    }

    /*
     * Items for the "Tags (Categories)" popup.
     *
     */
    private FileMetadata fileMetadataSelectedForTagsPopup = null;

    public void setFileMetadataSelectedForTagsPopup(FileMetadata fm) {
        fileMetadataSelectedForTagsPopup = fm;
    }

    public FileMetadata getFileMetadataSelectedForTagsPopup() {
        return fileMetadataSelectedForTagsPopup;
    }

    public void clearFileMetadataSelectedForTagsPopup() {
        fileMetadataSelectedForTagsPopup = null;
    }

    /*
     * 1. Tabular File Tags:
     */

    private List<String> tabFileTags = null;

    public List<String> getTabFileTags() {
        if (tabFileTags == null) {
            tabFileTags = DataFileTag.listTags();
        }
        return tabFileTags;
    }

    public void setTabFileTags(List<String> tabFileTags) {
        this.tabFileTags = tabFileTags;
    }

    private String[] selectedTabFileTags = {};

    public String[] getSelectedTabFileTags() {
        return selectedTabFileTags;
    }

    public void setSelectedTabFileTags(String[] selectedTabFileTags) {
        this.selectedTabFileTags = selectedTabFileTags;
    }

    private String[] selectedTags = {};

    public void refreshTagsPopUp(FileMetadata fm) {
        setFileMetadataSelectedForTagsPopup(fm);
        refreshCategoriesByName();
        refreshTabFileTagsByName();
    }

    private List<String> tabFileTagsByName;

    private void refreshTabFileTagsByName() {
        tabFileTagsByName = new ArrayList<>();
        if (fileMetadataSelectedForTagsPopup.getDataFile().getTags() != null) {
            for (int i = 0; i < fileMetadataSelectedForTagsPopup.getDataFile().getTags().size(); i++) {
                tabFileTagsByName.add(fileMetadataSelectedForTagsPopup.getDataFile().getTags().get(i).getTypeLabel());
            }
        }
        refreshSelectedTabFileTags();
    }

    private void refreshSelectedTabFileTags() {
        selectedTabFileTags = null;
        selectedTabFileTags = new String[0];
        if (tabFileTagsByName.size() > 0) {
            selectedTabFileTags = new String[tabFileTagsByName.size()];
            for (int i = 0; i < tabFileTagsByName.size(); i++) {
                selectedTabFileTags[i] = tabFileTagsByName.get(i);
            }
        }
        Arrays.sort(selectedTabFileTags);
    }

    private void refreshCategoriesByName() {
        categoriesByName = new ArrayList<>();
        for (String category : dataset.getCategoriesByName()) {
            categoriesByName.add(category);
        }
        refreshSelectedTags();
    }


    private List<String> categoriesByName;

    public List<String> getCategoriesByName() {
        return categoriesByName;
    }

    public void setCategoriesByName(List<String> categoriesByName) {
        this.categoriesByName = categoriesByName;
    }

    private void refreshSelectedTags() {
        selectedTags = null;
        selectedTags = new String[0];
        List<String> selectedCategoriesByName = new ArrayList<>();

        if (fileMetadataSelectedForTagsPopup.getCategories() != null) {
            for (int i = 0; i < fileMetadataSelectedForTagsPopup.getCategories().size(); i++) {
                if (!selectedCategoriesByName.contains(fileMetadataSelectedForTagsPopup.getCategories().get(i).getName())) {
                    selectedCategoriesByName.add(fileMetadataSelectedForTagsPopup.getCategories().get(i).getName());
                }
            }
        }

        if (selectedCategoriesByName.size() > 0) {
            selectedTags = new String[selectedCategoriesByName.size()];
            for (int i = 0; i < selectedCategoriesByName.size(); i++) {
                selectedTags[i] = selectedCategoriesByName.get(i);
            }
        }
        Arrays.sort(selectedTags);
    }

    public String[] getSelectedTags() {
        return selectedTags;
    }

    public void setSelectedTags(String[] selectedTags) {
        this.selectedTags = selectedTags;
    }



    /*
     * "File Tags" (aka "File Categories"):
     */

    private String newCategoryName = null;

    public String getNewCategoryName() {
        return newCategoryName;
    }

    public void setNewCategoryName(String newCategoryName) {
        this.newCategoryName = newCategoryName;
    }

    public String saveNewCategory() {

        if (newCategoryName != null && !newCategoryName.isEmpty()) {
            categoriesByName.add(newCategoryName);
        }
        //Now increase size of selectedTags and add new category
        String[] temp = new String[selectedTags.length + 1];
        System.arraycopy(selectedTags, 0, temp, 0, selectedTags.length);
        selectedTags = temp;
        selectedTags[selectedTags.length - 1] = newCategoryName;
        //Blank out added category
        newCategoryName = "";
        return "";
    }

    /* This method handles saving both "tabular file tags" and
     * "file categories" (which are also considered "tags" in 4.0)
     */
    public void saveFileTagsAndCategories() {
        if (fileMetadataSelectedForTagsPopup == null) {
            logger.fine("No FileMetadata selected for the categories popup");
            return;
        }
        // 1. File categories:
        /*
        In order to get the cancel button to work we had to separate the selected tags
        from the file metadata and re-add them on save

        */

        fileMetadataSelectedForTagsPopup.setCategories(new ArrayList<>());

        // New, custom file category (if specified):
        if (newCategoryName != null) {
            logger.fine("Adding new category, " + newCategoryName + " for file " + fileMetadataSelectedForTagsPopup.getLabel());
            fileMetadataSelectedForTagsPopup.addCategoryByName(newCategoryName);
        } else {
            logger.fine("no category specified");
        }
        newCategoryName = null;

        // File Categories selected from the list of existing categories:
        if (selectedTags != null) {
            for (String selectedTag : selectedTags) {

                fileMetadataSelectedForTagsPopup.addCategoryByName(selectedTag);
            }
        }

        // 2. Tabular DataFile Tags:

        if (fileMetadataSelectedForTagsPopup.getDataFile() != null && tabularDataTagsUpdated && selectedTabFileTags != null) {
            fileMetadataSelectedForTagsPopup.getDataFile().setTags(null);
            for (String selectedTabFileTag : selectedTabFileTags) {
                DataFileTag tag = new DataFileTag();
                try {
                    tag.setTypeByLabel(selectedTabFileTag);
                    tag.setDataFile(fileMetadataSelectedForTagsPopup.getDataFile());
                    fileMetadataSelectedForTagsPopup.getDataFile().addTag(tag);

                } catch (IllegalArgumentException iax) {
                    // ignore
                }
            }

            datasetUpdateRequired = true;
        }

        fileMetadataSelectedForTagsPopup = null;

    }

    public void handleFileCategoriesSelection(final AjaxBehaviorEvent event) {
        if (selectedTags != null) {
            selectedTags = selectedTags.clone();
        }
    }

    public void handleTabularTagsSelection(final AjaxBehaviorEvent event) {
        tabularDataTagsUpdated = true;
    }

    public void handleDescriptionChange(final AjaxBehaviorEvent event) {
        datasetUpdateRequired = true;
    }

    public void handleNameChange(final AjaxBehaviorEvent event) {
        datasetUpdateRequired = true;
    }

    private void populateFileMetadatas() {

        Long datasetVersionId = workingVersion.getId();

            if (datasetVersionId != null) {
                // The version has a database id - this is an existing version,
                // that had been saved previously. So we can look up the file metadatas
                // by the file and version ids:
                logger.fine("attempting to retrieve file metadata for version id " + datasetVersionId + " and file id " + selectedFileId);
                FileMetadata fileMetadata = datafileService.findFileMetadataByDatasetVersionIdAndDataFileId(datasetVersionId, selectedFileId);
                if (fileMetadata != null) {
                    logger.fine("Success!");
                    fileMetadatas.add(fileMetadata);
                } else {
                    logger.fine("Failed to find file metadata.");
                }
            } else {
                logger.fine("Brand new edit version - no database id.");
                for (FileMetadata fileMetadata : workingVersion.getFileMetadatas()) {

                    if (selectedFileId.equals(fileMetadata.getDataFile().getId())) {
                        logger.fine("Success! - found the file id " + selectedFileId + " in the brand new edit version.");
                        fileMetadatas.add(fileMetadata);
                        break;
                    }
                }
            }
    }
}
