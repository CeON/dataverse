package edu.harvard.iq.dataverse.dataset.tab;

import edu.harvard.iq.dataverse.DataFileServiceBean;
import edu.harvard.iq.dataverse.DatasetServiceBean;
import edu.harvard.iq.dataverse.DataverseRequestServiceBean;
import edu.harvard.iq.dataverse.DataverseSession;
import edu.harvard.iq.dataverse.EjbDataverseEngine;
import edu.harvard.iq.dataverse.FileDownloadHelper;
import edu.harvard.iq.dataverse.FileDownloadServiceBean;
import edu.harvard.iq.dataverse.GuestbookResponseServiceBean;
import edu.harvard.iq.dataverse.PermissionServiceBean;
import edu.harvard.iq.dataverse.PermissionsWrapper;
import edu.harvard.iq.dataverse.common.BundleUtil;
import edu.harvard.iq.dataverse.dataaccess.ImageThumbConverter;
import edu.harvard.iq.dataverse.datacapturemodule.DataCaptureModuleUtil;
import edu.harvard.iq.dataverse.datacapturemodule.ScriptRequestResponse;
import edu.harvard.iq.dataverse.engine.command.exception.CommandException;
import edu.harvard.iq.dataverse.engine.command.exception.IllegalCommandException;
import edu.harvard.iq.dataverse.engine.command.impl.CreateNewDatasetCommand;
import edu.harvard.iq.dataverse.engine.command.impl.RequestRsyncScriptCommand;
import edu.harvard.iq.dataverse.engine.command.impl.UpdateDatasetVersionCommand;
import edu.harvard.iq.dataverse.externaltools.ExternalToolServiceBean;
import edu.harvard.iq.dataverse.license.TermsOfUseFormMapper;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.datafile.DataFileCategory;
import edu.harvard.iq.dataverse.persistence.datafile.DataFileTag;
import edu.harvard.iq.dataverse.persistence.datafile.ExternalTool;
import edu.harvard.iq.dataverse.persistence.datafile.FileMetadata;
import edu.harvard.iq.dataverse.persistence.datafile.license.FileTermsOfUse;
import edu.harvard.iq.dataverse.persistence.datafile.license.FileTermsOfUse.TermsOfUseType;
import edu.harvard.iq.dataverse.persistence.datafile.license.TermsOfUseForm;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetLock;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.guestbook.GuestbookResponse;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import edu.harvard.iq.dataverse.util.FileUtil;
import edu.harvard.iq.dataverse.util.JsfHelper;
import edu.harvard.iq.dataverse.util.StringUtil;
import org.apache.commons.lang3.StringUtils;
import org.primefaces.context.RequestContext;
import org.primefaces.event.data.PageEvent;

import javax.ejb.EJBException;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.faces.event.AjaxBehaviorEvent;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.validation.ConstraintViolation;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static edu.harvard.iq.dataverse.util.JsfHelper.JH;

@ViewScoped
@Named("datasetFilesTab")
public class DatasetFilesTab implements Serializable {

    private static final Logger logger = Logger.getLogger(DatasetFilesTab.class.getCanonicalName());
    
    private DatasetServiceBean datasetService;
    private DataFileServiceBean datafileService;
    private GuestbookResponseServiceBean guestbookResponseService;
    private ExternalToolServiceBean externalToolService;
    private EjbDataverseEngine commandEngine;
    private FileDownloadServiceBean fileDownloadService;
    private FileDownloadHelper fileDownloadHelper;
    private TermsOfUseFormMapper termsOfUseFormMapper;
    
    private PermissionServiceBean permissionService;
    private PermissionsWrapper permissionsWrapper;
    private SettingsServiceBean settingsService;

    private DataverseSession session;
    private DataverseRequestServiceBean dvRequestService;
    
    
    private Dataset dataset;
    private DatasetVersion workingVersion;

    private Boolean hasTabular = false;
    /**
     * In the file listing, the page the user is on. This is zero-indexed so if
     * the user clicks page 2 in the UI, this will be 1.
     */
    private int filePaginatorPage;
    private int rowsPerPage;

    private List<FileMetadata> selectedFiles = new ArrayList<>();

    private List<FileMetadata> fileMetadatasSearch;

    private DatasetVersion clone;

    private boolean removeUnusedTags;

    private boolean selectAllFiles;

    private String fileLabelSearchTerm;

    /**
     * The contents of the script.
     */
    private String rsyncScript = "";

    private String rsyncScriptFilename;


    private Map<Long, String> datafileThumbnailsMap = new HashMap<>();

    private List<ExternalTool> configureTools = new ArrayList<>();
    private List<ExternalTool> exploreTools = new ArrayList<>();
    private Map<Long, List<ExternalTool>> configureToolsByFileId = new HashMap<>();
    private Map<Long, List<ExternalTool>> exploreToolsByFileId = new HashMap<>();

    private GuestbookResponse guestbookResponse;

    private List<FileMetadata> selectedDownloadableFiles;

    private List<FileMetadata> selectedNonDownloadableFiles;

    private Boolean downloadButtonAvailable = null;

    private String[] selectedTags = {};

    private List<String> tabFileTagsByName;

    private String[] selectedTabFileTags = {};

    private List<String> categoriesByName;

    private List<FileMetadata> selectedRestrictedFiles = new ArrayList<>();

    private List<FileMetadata> selectedUnrestrictedFiles = new ArrayList<>();

    private List<String> tabFileTags = null;

    private boolean tabularDataSelected = false;

    private String newCategoryName = null;
    private boolean bulkFileDeleteInProgress = false;

    private List<FileMetadata> filesToBeDeleted = new ArrayList<>();

    private Boolean lockedFromDownloadVar;
    private Boolean lockedFromEditsVar;
    private boolean lockedDueToDcmUpload;

    
    // -------------------- CONSTRUCTORS --------------------
    
    @Deprecated
    public DatasetFilesTab() {
        
    }
    
    @Inject
    public DatasetFilesTab(FileDownloadHelper fileDownloadHelper, DataFileServiceBean datafileService,
            PermissionServiceBean permissionService, PermissionsWrapper permissionsWrapper,
            DataverseRequestServiceBean dvRequestService, DatasetServiceBean datasetService, DataverseSession session,
            FileDownloadServiceBean fileDownloadService, GuestbookResponseServiceBean guestbookResponseService,
            SettingsServiceBean settingsService, EjbDataverseEngine commandEngine,
            ExternalToolServiceBean externalToolService, TermsOfUseFormMapper termsOfUseFormMapper) {
        this.fileDownloadHelper = fileDownloadHelper;
        this.datafileService = datafileService;
        this.permissionService = permissionService;
        this.permissionsWrapper = permissionsWrapper;
        this.dvRequestService = dvRequestService;
        this.datasetService = datasetService;
        this.session = session;
        this.fileDownloadService = fileDownloadService;
        this.guestbookResponseService = guestbookResponseService;
        this.settingsService = settingsService;
        this.commandEngine = commandEngine;
        this.externalToolService = externalToolService;
        this.termsOfUseFormMapper = termsOfUseFormMapper;
    }
    
    
    public void init(DatasetVersion workingVersion) {
        this.dataset = workingVersion.getDataset();
        this.workingVersion = workingVersion;
        rowsPerPage = 10;

        guestbookResponse = new GuestbookResponse();
        
        fileMetadatasSearch = workingVersion.getFileMetadatasSorted();
        this.guestbookResponse = guestbookResponseService.initGuestbookResponseForFragment(workingVersion, null, session);
        this.getFileDownloadHelper().setGuestbookResponse(guestbookResponse);
        

        logger.fine("Checking if rsync support is enabled.");
        if (DataCaptureModuleUtil.rsyncSupportEnabled(settingsService.getValueForKey(SettingsServiceBean.Key.UploadMethods))
                && dataset.getFiles().isEmpty()) { //only check for rsync if no files exist
            try {
                ScriptRequestResponse scriptRequestResponse = commandEngine.submit(new RequestRsyncScriptCommand(dvRequestService.getDataverseRequest(), dataset));
                logger.fine("script: " + scriptRequestResponse.getScript());
                if (scriptRequestResponse.getScript() != null && !scriptRequestResponse.getScript().isEmpty()) {
                    rsyncScript = scriptRequestResponse.getScript();
                    rsyncScriptFilename = "upload-" + workingVersion.getDataset().getIdentifier() + ".bash";
                    rsyncScriptFilename = rsyncScriptFilename.replace("/", "_");
                }
            } catch (RuntimeException ex) {
                logger.warning("Problem getting rsync script: " + ex.getLocalizedMessage());
            } catch (CommandException cex) {
                logger.warning("Problem getting rsync script (Command Exception): " + cex.getLocalizedMessage());
            }
        }
        
        for (DataFile f : dataset.getFiles()) {
            if (f.isTabularData()) {
                hasTabular = true;
                break;
            }
        }

        if (dataset.isLockedFor(DatasetLock.Reason.DcmUpload)) {
            lockedDueToDcmUpload = false;
        }
        
        configureTools = externalToolService.findByType(ExternalTool.Type.CONFIGURE);
        exploreTools = externalToolService.findByType(ExternalTool.Type.EXPLORE);
    }
    
    // -------------------- GETTERS --------------------

    public Dataset getDataset() {
        return dataset;
    }

    public DatasetVersion getWorkingVersion() {
        return workingVersion;
    }

    public int getFilePaginatorPage() {
        return filePaginatorPage;
    }

    public int getRowsPerPage() {
        return rowsPerPage;
    }

    public List<FileMetadata> getSelectedFiles() {
        return selectedFiles;
    }

    public List<FileMetadata> getFileMetadatasSearch() {
        return fileMetadatasSearch;
    }

    public boolean isSelectAllFiles() {
        return selectAllFiles;
    }

    public String getFileLabelSearchTerm() {
        return fileLabelSearchTerm;
    }


    public GuestbookResponse getGuestbookResponse() {
        return guestbookResponse;
    }

    public List<FileMetadata> getSelectedNonDownloadableFiles() {
        return selectedNonDownloadableFiles;
    }

    public boolean isHasTabular() {
        return hasTabular;
    }

    public String[] getSelectedTabFileTags() {
        return selectedTabFileTags;
    }

    public List<String> getCategoriesByName() {
        return categoriesByName;
    }

    public boolean isTabularDataSelected() {
        return tabularDataSelected;
    }

    public String[] getSelectedTags() {
        return selectedTags;
    }

    public String getNewCategoryName() {
        return newCategoryName;
    }

    public boolean isRemoveUnusedTags() {
        return removeUnusedTags;
    }

    public GuestbookResponseServiceBean getGuestbookResponseService() {
        return guestbookResponseService;
    }

    public FileDownloadServiceBean getFileDownloadService() {
        return fileDownloadService;
    }

    public FileDownloadHelper getFileDownloadHelper() {
        return fileDownloadHelper;
    }

    public boolean isLockedDueToDcmUpload() {
        return lockedDueToDcmUpload;
    }
    
    
    // -------------------- LOGIC --------------------
    
    public void refresh() {
        fileMetadatasSearch = workingVersion.getFileMetadatasSorted();
        lockedFromDownloadVar = null;
        lockedFromEditsVar = null;
    }
    
    public void refreshPaginator() {
        FacesContext facesContext = FacesContext.getCurrentInstance();
        org.primefaces.component.datatable.DataTable dt = (org.primefaces.component.datatable.DataTable) facesContext.getViewRoot().findComponent("datasetForm:tabView:filesTable");
        filePaginatorPage = dt.getPage();
        rowsPerPage = dt.getRowsToRender();
    }

    /**
     * Reset page number to the first element found for showing search results.
     */
    public void resetPaginator() {
        RequestContext.getCurrentInstance().execute("PF('filesTable').getPaginator().setPage(0)");
    }

    public void fileListingPaginatorListener(PageEvent event) {
        filePaginatorPage = event.getPage();
    }

    public void selectAllFiles() {
        logger.fine("selectAllFiles called");
        if(fileLabelSearchTerm.isEmpty()) {
            selectedFiles = workingVersion.getFileMetadatas();
        } else {
            selectedFiles = fileMetadatasSearch;
        }
    }

    public void clearSelection() {
        logger.info("clearSelection called");
        selectedFiles = Collections.emptyList();
    }

    public void toggleAllSelected() {
        //This is here so that if the user selects all on the dataset page
        // s/he will get all files on download
        this.selectAllFiles = !this.selectAllFiles;
    }

    public void updateFileSearch() {
        logger.info("updating file search list");
        this.fileMetadatasSearch = selectFileMetadatasForDisplay(this.fileLabelSearchTerm);
    }

    public boolean isThumbnailAvailable(FileMetadata fileMetadata) {

        // new and optimized logic: 
        // - check download permission here (should be cached - so it's free!)
        // - only then check if the thumbnail is available/exists.
        // then cache the results!

        Long dataFileId = fileMetadata.getDataFile().getId();

        if (datafileThumbnailsMap.containsKey(dataFileId)) {
            return !"".equals(datafileThumbnailsMap.get(dataFileId));
        }

        if (!FileUtil.isThumbnailSupported(fileMetadata.getDataFile())) {
            datafileThumbnailsMap.put(dataFileId, "");
            return false;
        }

        if (!this.fileDownloadHelper.canDownloadFile(fileMetadata)) {
            datafileThumbnailsMap.put(dataFileId, "");
            return false;
        }


        String thumbnailAsBase64 = ImageThumbConverter.getImageThumbnailAsBase64(fileMetadata.getDataFile(), ImageThumbConverter.DEFAULT_THUMBNAIL_SIZE);


        //if (datafileService.isThumbnailAvailable(fileMetadata.getDataFile())) {
        if (!StringUtil.isEmpty(thumbnailAsBase64)) {
            datafileThumbnailsMap.put(dataFileId, thumbnailAsBase64);
            return true;
        }

        datafileThumbnailsMap.put(dataFileId, "");
        return false;

    }

    public void downloadRsyncScript() {

        FacesContext ctx = FacesContext.getCurrentInstance();
        HttpServletResponse response = (HttpServletResponse) ctx.getExternalContext().getResponse();
        response.setContentType("application/download");

        String contentDispositionString;

        contentDispositionString = "attachment;filename=" + rsyncScriptFilename;
        response.setHeader("Content-Disposition", contentDispositionString);

        try {
            ServletOutputStream out = response.getOutputStream();
            out.write(rsyncScript.getBytes());
            out.flush();
            ctx.responseComplete();
        } catch (IOException e) {
            String error = "Problem getting bytes from rsync script: " + e;
            logger.warning(error);
            return;
        }

        // If the script has been successfully downloaded, lock the dataset:
        String lockInfoMessage = "script downloaded";
        DatasetLock lock = datasetService.addDatasetLock(dataset.getId(), DatasetLock.Reason.DcmUpload, session.getUser() != null ? ((AuthenticatedUser) session.getUser()).getId() : null, lockInfoMessage);
        if (lock != null) {
            dataset.addLock(lock);
        } else {
            logger.log(Level.WARNING, "Failed to lock the dataset (dataset id={0})", dataset.getId());
        }

    }
    
    public String getDataFileThumbnailAsBase64(FileMetadata fileMetadata) {
        return datafileThumbnailsMap.get(fileMetadata.getDataFile().getId());
    }

    /*
       TODO/OPTIMIZATION: This is still costing us N SELECT FROM GuestbookResponse queries, 
       where N is the number of files. This could of course be replaced by a query that'll 
       look up all N at once... Not sure if it's worth it; especially now that N
       will always be 10, for the initial page load. -- L.A. 4.2.1
     */
    public Long getGuestbookResponseCount(FileMetadata fileMetadata) {
        return guestbookResponseService.getCountGuestbookResponsesByDataFileId(fileMetadata.getDataFile().getId());
    }
    
    public boolean isDownloadPopupRequired() {
        return FileUtil.isDownloadPopupRequired(workingVersion);
    }

    public boolean isRequestAccessPopupRequired(FileMetadata fileMetadata) {
        return FileUtil.isRequestAccessPopupRequired(fileMetadata);
    }

    public List<ExternalTool> getConfigureToolsForDataFile(Long fileId) {
        return getCachedToolsForDataFile(fileId, ExternalTool.Type.CONFIGURE);
    }

    public List<ExternalTool> getExploreToolsForDataFile(Long fileId) {
        return getCachedToolsForDataFile(fileId, ExternalTool.Type.EXPLORE);
    }

    // Another convenience method - to cache Update Permission on the dataset: 
    public boolean canUpdateDataset() {
        return permissionsWrapper.canUpdateDataset(dvRequestService.getDataverseRequest(), this.dataset);
    }

    public boolean isFileAccessRequestMultiButtonRequired() {
        if (session.getUser().isAuthenticated()) {
            return false;
        }
        if (workingVersion == null) {
            return false;
        }
        for (FileMetadata fmd : workingVersion.getFileMetadatas()) {
            if (!this.fileDownloadHelper.canDownloadFile(fmd)) {
                return true;
            }
        }
        return false;
    }

    public boolean isFileAccessRequestMultiSignUpButtonRequired() {
        if (session.getUser().isAuthenticated()) {
            return false;
        }
        for (FileMetadata fmd : workingVersion.getFileMetadatas()) {
            if (!this.fileDownloadHelper.canDownloadFile(fmd)) {
                return true;
            }
        }
        return false;
    }

    public String requestAccessMultipleFiles() {

        if (selectedFiles.isEmpty()) {
            RequestContext requestContext = RequestContext.getCurrentInstance();
            requestContext.execute("PF('selectFilesForRequestAccess').show()");
            return "";
        } else {
            boolean anyFileAccessPopupRequired = false;
            
            fileDownloadHelper.clearRequestAccessFiles();
            for (FileMetadata fmd : selectedFiles) {
                if (isRequestAccessPopupRequired(fmd)) {
                    fileDownloadHelper.addMultipleFilesForRequestAccess(fmd.getDataFile());
                    anyFileAccessPopupRequired = true;
                }
            }
            
            if (anyFileAccessPopupRequired) {
                RequestContext requestContext = RequestContext.getCurrentInstance();
                requestContext.execute("PF('requestAccessPopup').show()");
                return "";
            } else {
                //No popup required
                fileDownloadHelper.requestAccessIndirect();
                return "";
            }
        }
    }


    public void validateFilesForDownload(boolean guestbookRequired, boolean downloadOriginal) {
        selectedDownloadableFiles = new ArrayList<>();
        selectedNonDownloadableFiles = new ArrayList<>();

        if (this.selectedFiles.isEmpty()) {
            RequestContext requestContext = RequestContext.getCurrentInstance();
            requestContext.execute("PF('selectFilesForDownload').show()");
            return;
        }
        for (FileMetadata fmd : this.selectedFiles) {
            if (this.fileDownloadHelper.canDownloadFile(fmd)) {
                selectedDownloadableFiles.add(fmd);
            } else {
                selectedNonDownloadableFiles.add(fmd);
            }
        }

        // If some of the files were restricted and we had to drop them off the 
        // list, and NONE of the files are left on the downloadable list
        // - we show them a "you're out of luck" popup: 
        if (selectedDownloadableFiles.isEmpty() && !selectedNonDownloadableFiles.isEmpty()) {
            RequestContext requestContext = RequestContext.getCurrentInstance();
            requestContext.execute("PF('downloadInvalid').show()");
            return;
        }

        // Note that the GuestbookResponse object may still have information from 
        // the last download action performed by the user. For example, it may 
        // still have the non-null Datafile in it, if the user has just downloaded
        // a single file; or it may still have the format set to "original" - 
        // even if that's not what they are trying to do now. 
        // So make sure to reset these values:
        guestbookResponse.setDataFile(null);
        guestbookResponse.setSelectedFileIds(joinDataFileIdsFromFileMetadata(this.selectedDownloadableFiles));
        if (downloadOriginal) {
            guestbookResponse.setFileFormat("original");
        } else {
            guestbookResponse.setFileFormat("");
        }
        guestbookResponse.setDownloadtype("Download");

        // If we have a bunch of files that we can download, AND there were no files 
        // that we had to take off the list, because of permissions - we can 
        // either send the user directly to the download API (if no guestbook/terms
        // popup is required), or send them to the download popup:
        if (!selectedDownloadableFiles.isEmpty() && selectedNonDownloadableFiles.isEmpty()) {
            if (guestbookRequired) {
                openDownloadPopupForMultipleFileDownload();
            } else {
                startMultipleFileDownload();
            }
            return;
        }

        // ... and if some files were restricted, but some are downloadable, 
        // we are showing them this "you are somewhat in luck" popup; that will 
        // then direct them to the download, or popup, as needed:
        if (!selectedDownloadableFiles.isEmpty() && !selectedNonDownloadableFiles.isEmpty()) {
            RequestContext requestContext = RequestContext.getCurrentInstance();
            requestContext.execute("PF('downloadMixed').show()");
        }

    }


    public void openDownloadPopupForMultipleFileDownload() {
        if (this.selectedFiles.isEmpty()) {
            RequestContext requestContext = RequestContext.getCurrentInstance();
            requestContext.execute("PF('selectFilesForDownload').show()");
            return;
        }

        // There's a chance that this is not really a batch download - i.e., 
        // there may only be one file on the downloadable list. But the fileDownloadService 
        // method below will check for that, and will redirect to the single download, if
        // that's the case. -- L.A.

        this.guestbookResponse.setDownloadtype("Download");
        RequestContext requestContext = RequestContext.getCurrentInstance();
        requestContext.execute("PF('downloadPopup').show();handleResizeDialog('downloadPopup');");
    }

    public void startMultipleFileDownload() {

        boolean doNotSaveGuestbookResponse = workingVersion.isDraft();
        // There's a chance that this is not really a batch download - i.e., 
        // there may only be one file on the downloadable list. But the fileDownloadService 
        // method below will check for that, and will redirect to the single download, if
        // that's the case. -- L.A.
        fileDownloadService.writeGuestbookAndStartBatchDownload(guestbookResponse, doNotSaveGuestbookResponse);
    }

    public boolean isDownloadButtonAvailable() {

        if (downloadButtonAvailable != null) {
            return downloadButtonAvailable;
        }

        for (FileMetadata fmd : workingVersion.getFileMetadatas()) {
            if (this.fileDownloadHelper.canDownloadFile(fmd)) {
                downloadButtonAvailable = true;
                return true;
            }
        }
        downloadButtonAvailable = false;
        return false;
    }

    public void refreshTagsPopUp() {
        if (bulkUpdateCheckVersion()) {
            refreshSelectedFiles();
        }
        updateFileCounts();
        refreshCategoriesByName();
        refreshTabFileTagsByName();
    }

    public void handleSelection(final AjaxBehaviorEvent event) {
        if (selectedTags != null) {
            selectedTags = selectedTags.clone();
        }
    }

    public List<String> getTabFileTags() {
        if (tabFileTags == null) {
            tabFileTags = DataFileTag.listTags();
        }
        return tabFileTags;
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

    public String editFileMetadata() {
        // If there are no files selected, return an empty string - which 
        // means, do nothing, don't redirect anywhere, stay on this page. 
        // The dialogue telling the user to select at least one file will 
        // be shown to them by an onclick javascript method attached to the 
        // filemetadata edit button on the page.
        // -- L.A. 4.2.1
        if (this.selectedFiles == null || this.selectedFiles.size() < 1) {
            return "";
        }
        return "/editdatafiles.xhtml?selectedFileIds=" + joinDataFileIdsFromFileMetadata(this.selectedFiles) + "&datasetId=" + dataset.getId() + "&faces-redirect=true";
    }
    
    
    /* This method handles saving both "tabular file tags" and
     * "file categories" (which are also considered "tags" in 4.0)
     */
    public String saveFileTagsAndCategories() {
        // 1. New Category name:
        // With we don't need to do anything for the file categories that the user
        // selected from the pull down list; that was done directly from the 
        // page with the FileMetadata.setCategoriesByName() method. 
        // So here we only need to take care of the new, custom category
        // name, if entered: 
        if (bulkUpdateCheckVersion()) {
            refreshSelectedFiles();
        }
        for (FileMetadata fmd : workingVersion.getFileMetadatas()) {
            if (selectedFiles != null && selectedFiles.size() > 0) {
                for (FileMetadata fm : selectedFiles) {
                    if (fm.getDataFile().equals(fmd.getDataFile())) {
                        fmd.setCategories(new ArrayList<>());
                        if (newCategoryName != null) {
                            fmd.addCategoryByName(newCategoryName);
                        }
                        // 2. Tabular DataFile Tags: 
                        if (selectedTags != null) {
                            for (String selectedTag : selectedTags) {
                                fmd.addCategoryByName(selectedTag);
                            }
                        }
                        if (fmd.getDataFile().isTabularData()) {
                            fmd.getDataFile().setTags(null);
                            for (String selectedTabFileTag : selectedTabFileTags) {
                                DataFileTag tag = new DataFileTag();
                                try {
                                    tag.setTypeByLabel(selectedTabFileTag);
                                    tag.setDataFile(fmd.getDataFile());
                                    fmd.getDataFile().addTag(tag);
                                } catch (IllegalArgumentException iax) {
                                    // ignore 
                                }
                            }
                        }
                    }
                }
            }
        }
        // success message:
        String successMessage = BundleUtil.getStringFromBundle("file.assignedTabFileTags.success");
        logger.fine(successMessage);
        successMessage = successMessage.replace("{0}", "Selected Files");
        JsfHelper.addFlashMessage(successMessage);
        selectedTags = null;

        logger.fine("New category name: " + newCategoryName);

        newCategoryName = null;

        if (removeUnusedTags) {
            removeUnusedFileTagsFromDataset();
        }
        save();
        return returnToDraftVersion();
    }

    public String deleteFilesAndSave() {
        bulkFileDeleteInProgress = true;
        if (bulkUpdateCheckVersion()) {
            refreshSelectedFiles();
        }
        deleteFiles();
        return save();
    }

    public String saveTermsOfUse(TermsOfUseForm termsOfUseForm) {

        if (bulkUpdateCheckVersion()) {
            refreshSelectedFiles();
        }

        FileTermsOfUse termsOfUse = termsOfUseFormMapper.mapToFileTermsOfUse(termsOfUseForm);

        for (FileMetadata fm : selectedFiles) {
            fm.setTermsOfUse(termsOfUse.createCopy());
        }

        save();

        return returnToDraftVersion();
    }
    
    public boolean isLocked() {
        return dataset.isLocked();
    }
    
    // TODO: investigate why this method was needed in the first place?
    // It appears that it was written under the assumption that downloads 
    // should not be allowed when a dataset is locked... (why?)
    // There are calls to the method throghout the file-download-buttons fragment; 
    // except the way it's done there, it's actually disregarded (??) - so the 
    // download buttons ARE always enabled. The only place where this method is 
    // honored is on the batch (mutliple file) download buttons in filesFragment.xhtml. 
    // As I'm working on #4000, I've been asked to re-enable the batch download 
    // buttons there as well, even when the dataset is locked. I'm doing that - but 
    // I feel we should probably figure out why we went to the trouble of creating 
    // this code in the first place... is there some reason we are forgetting now, 
    // why we do actually want to disable downloads on locked datasets??? 
    // -- L.A. Aug. 2018
    public boolean isLockedFromDownload() {
        if (lockedFromDownloadVar == null) {
            try {
                permissionService.checkDownloadFileLock(dataset, dvRequestService.getDataverseRequest(), new CreateNewDatasetCommand(dataset, dvRequestService.getDataverseRequest()));
                lockedFromDownloadVar = false;
            } catch (IllegalCommandException ex) {
                lockedFromDownloadVar = true;
                return true;
            }
        }
        return lockedFromDownloadVar;
    }
    
    public boolean isLockedFromEdits() {
        if (lockedFromEditsVar == null) {
            try {
                permissionService.checkEditDatasetLock(dataset, dvRequestService.getDataverseRequest(), new UpdateDatasetVersionCommand(dataset, dvRequestService.getDataverseRequest()));
                lockedFromEditsVar = false;
            } catch (IllegalCommandException ex) {
                lockedFromEditsVar = true;
            }
        }
        return lockedFromEditsVar;
    }
    
    // -------------------- PRIVATE --------------------

    private List<FileMetadata> selectFileMetadatasForDisplay(String searchTerm) {
        Set<Long> searchResultsIdSet = null;

        if (searchTerm != null && !searchTerm.equals("")) {
            List<Integer> searchResultsIdList = datafileService.findFileMetadataIdsByDatasetVersionIdLabelSearchTerm(workingVersion.getId(), searchTerm, "", "");
            searchResultsIdSet = new HashSet<>();
            for (Integer id : searchResultsIdList) {
                searchResultsIdSet.add(id.longValue());
            }
        }

        List<FileMetadata> retList = new ArrayList<>();

        for (FileMetadata fileMetadata : workingVersion.getFileMetadatasSorted()) {
            if (searchResultsIdSet == null || searchResultsIdSet.contains(fileMetadata.getId())) {
                retList.add(fileMetadata);
            }
        }

        return retList;
    }

    private List<ExternalTool> getCachedToolsForDataFile(Long fileId, ExternalTool.Type type) {
        Map<Long, List<ExternalTool>> cachedToolsByFileId = new HashMap<>();
        List<ExternalTool> externalTools = new ArrayList<>();
        switch (type) {
            case EXPLORE:
                cachedToolsByFileId = exploreToolsByFileId;
                externalTools = exploreTools;
                break;
            case CONFIGURE:
                cachedToolsByFileId = configureToolsByFileId;
                externalTools = configureTools;
                break;
            default:
                break;
        }
        List<ExternalTool> cachedTools = cachedToolsByFileId.get(fileId);
        if (cachedTools != null) { //if already queried before and added to list
            return cachedTools;
        }
        DataFile dataFile = datafileService.find(fileId);
        cachedTools = ExternalToolServiceBean.findExternalToolsByFile(externalTools, dataFile);
        cachedToolsByFileId.put(fileId, cachedTools); //add to map so we don't have to do the lifting again
        return cachedTools;
    }


    // helper Method
    private String joinDataFileIdsFromFileMetadata(List<FileMetadata> fileMetadataList) {
        String joinedIdString = "";
        for (FileMetadata fmd : this.selectedFiles) {
            if (!StringUtil.isEmpty(joinedIdString)) {
                joinedIdString += ",";
            }
            joinedIdString += fmd.getDataFile().getId();
        }
        return joinedIdString;
    }

    private void updateFileCounts() {
        selectedUnrestrictedFiles = new ArrayList<>();
        selectedRestrictedFiles = new ArrayList<>();
        tabularDataSelected = false;
        for (FileMetadata fmd : this.selectedFiles) {
            if (fmd.getTermsOfUse().getTermsOfUseType() == TermsOfUseType.RESTRICTED) {
                selectedRestrictedFiles.add(fmd);
            } else {
                selectedUnrestrictedFiles.add(fmd);
            }
            if (fmd.getDataFile().isTabularData()) {
                tabularDataSelected = true;
            }
        }
    }
    
    private void refreshCategoriesByName() {
        categoriesByName = new ArrayList<>();
        for (String category : dataset.getCategoriesByName()) {
            categoriesByName.add(category);
        }
        refreshSelectedTags();
    }

    private void refreshSelectedTags() {
        selectedTags = null;
        selectedTags = new String[0];

        List<String> selectedCategoriesByName = new ArrayList<>();
        for (FileMetadata fm : selectedFiles) {
            if (fm.getCategories() != null) {
                for (int i = 0; i < fm.getCategories().size(); i++) {
                    if (!selectedCategoriesByName.contains(fm.getCategories().get(i).getName())) {
                        selectedCategoriesByName.add(fm.getCategories().get(i).getName());
                    }

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


    private void refreshTabFileTagsByName() {

        tabFileTagsByName = new ArrayList<>();
        for (FileMetadata fm : selectedFiles) {
            if (fm.getDataFile().getTags() != null) {
                for (int i = 0; i < fm.getDataFile().getTags().size(); i++) {
                    if (!tabFileTagsByName.contains(fm.getDataFile().getTags().get(i).getTypeLabel())) {
                        tabFileTagsByName.add(fm.getDataFile().getTags().get(i).getTypeLabel());
                    }
                }
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

    private void refreshSelectedFiles() {
        dataset = datasetService.find(dataset.getId());
        String termsOfAccess = workingVersion.getTermsOfUseAndAccess().getTermsOfAccess();
        boolean requestAccess = workingVersion.getTermsOfUseAndAccess().isFileAccessRequest();
        workingVersion = dataset.getEditVersion();
        workingVersion.getTermsOfUseAndAccess().setTermsOfAccess(termsOfAccess);
        workingVersion.getTermsOfUseAndAccess().setFileAccessRequest(requestAccess);
        List<FileMetadata> newSelectedFiles = new ArrayList<>();
        for (FileMetadata fmd : selectedFiles) {
            for (FileMetadata fmdn : workingVersion.getFileMetadatas()) {
                if (fmd.getDataFile().equals(fmdn.getDataFile())) {
                    newSelectedFiles.add(fmdn);
                }
            }
        }

        selectedFiles.clear();
        for (FileMetadata fmdn : newSelectedFiles) {
            selectedFiles.add(fmdn);
        }
    }

    private boolean bulkUpdateCheckVersion() {
        return workingVersion.isReleased();
    }

    /*
    Remove unused file tags
    When updating datafile tags see if any custom tags are not in use.
    Remove them
    
    */
    private void removeUnusedFileTagsFromDataset() {
        categoriesByName = new ArrayList<>();
        for (FileMetadata fm : workingVersion.getFileMetadatas()) {
            if (fm.getCategories() != null) {
                for (int i = 0; i < fm.getCategories().size(); i++) {
                    if (!categoriesByName.contains(fm.getCategories().get(i).getName())) {
                        categoriesByName.add(fm.getCategories().get(i).getName());
                    }
                }
            }
        }
        List<DataFileCategory> datasetFileCategoriesToRemove = new ArrayList<>();

        for (DataFileCategory test : dataset.getCategories()) {
            boolean remove = true;
            for (String catByName : categoriesByName) {
                if (catByName.equals(test.getName())) {
                    remove = false;
                    break;
                }
            }
            if (remove) {
                datasetFileCategoriesToRemove.add(test);
            }
        }

        if (!datasetFileCategoriesToRemove.isEmpty()) {
            for (DataFileCategory remove : datasetFileCategoriesToRemove) {
                dataset.getCategories().remove(remove);
            }

        }

    }

    private void deleteFiles() {

        for (FileMetadata markedForDelete : selectedFiles) {

            if (markedForDelete.getId() != null) {
                // This FileMetadata has an id, i.e., it exists in the database. 
                // We are going to remove this filemetadata from the version: 
                dataset.getEditVersion().getFileMetadatas().remove(markedForDelete);
                // But the actual delete will be handled inside the UpdateDatasetCommand
                // (called later on). The list "filesToBeDeleted" is passed to the 
                // command as a parameter:
                filesToBeDeleted.add(markedForDelete);
            } else {
                // This FileMetadata does not have an id, meaning it has just been 
                // created, and not yet saved in the database. This in turn means this is 
                // a freshly created DRAFT version; specifically created because 
                // the user is trying to delete a file from an existing published 
                // version. This means we are not really *deleting* the file - 
                // we are going to keep it in the published version; we are simply 
                // going to save a new DRAFT version that does not contain this file. 
                // So below we are deleting the metadata from the version; we are 
                // NOT adding the file to the filesToBeDeleted list that will be 
                // passed to the UpdateDatasetCommand. -- L.A. Aug 2017
                Iterator<FileMetadata> fmit = dataset.getEditVersion().getFileMetadatas().iterator();
                while (fmit.hasNext()) {
                    FileMetadata fmd = fmit.next();
                    if (markedForDelete.getDataFile().getStorageIdentifier().equals(fmd.getDataFile().getStorageIdentifier())) {
                        // And if this is an image file that happens to be assigned 
                        // as the dataset thumbnail, let's null the assignment here:

                        if (fmd.getDataFile().equals(dataset.getThumbnailFile())) {
                            dataset.setThumbnailFile(null);
                        }
                        /* It should not be possible to get here if this file 
                           is not in fact released! - so the code block below 
                           is not needed.
                        //if not published then delete identifier
                        if (!fmd.getDataFile().isReleased()){
                            try{
                                commandEngine.submit(new DeleteDataFileCommand(fmd.getDataFile(), dvRequestService.getDataverseRequest()));
                            } catch (CommandException e){
                                 //this command is here to delete the identifier of unreleased files
                                 //if it fails then a reserved identifier may still be present on the remote provider
                            }                           
                        } */
                        fmit.remove();
                        break;
                    }
                }
            }
        }

        /* 
           Do note that if we are deleting any files that have UNFs (i.e., 
           tabular files), we DO NEED TO RECALCULATE the UNF of the version!
           - but we will do this inside the UpdateDatasetCommand.
        */
    }


    private String save() {

        // Validate
        Set<ConstraintViolation> constraintViolations = workingVersion.validate();
        if (!constraintViolations.isEmpty()) {
            //JsfHelper.addFlashMessage(BundleUtil.getStringFromBundle("dataset.message.validationError"));
            JH.addMessage(FacesMessage.SEVERITY_ERROR, BundleUtil.getStringFromBundle("dataset.message.validationError"));
            //FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Validation Error", "See below for details."));
            return "";
        }

        // Use the Create or Update command to save the dataset: 
        UpdateDatasetVersionCommand cmd;
        Map<Long, String> deleteStorageLocations = null;

        try {
            if (!filesToBeDeleted.isEmpty()) {
                deleteStorageLocations = datafileService.getPhysicalFilesToDelete(filesToBeDeleted);
            }
            cmd = new UpdateDatasetVersionCommand(dataset, dvRequestService.getDataverseRequest(), filesToBeDeleted, clone);
            cmd.setValidateLenient(true);
            
            dataset = commandEngine.submit(cmd);
            logger.fine("Successfully executed SaveDatasetCommand.");
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
            logger.log(Level.FINE, "Couldn''t save dataset: {0}", error.toString());
            populateDatasetUpdateFailureMessage();
            return returnToDraftVersion();
        } catch (CommandException ex) {
            //FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Dataset Save Failed", " - " + ex.toString()));
            logger.log(Level.SEVERE, "CommandException, when attempting to update the dataset: " + ex.getMessage(), ex);
            populateDatasetUpdateFailureMessage();
            return returnToDraftVersion();
        }

        // Have we just deleted some draft datafiles (successfully)? 
        // finalize the physical file deletes:
        // (DataFileService will double-check that the datafiles no 
        // longer exist in the database, before attempting to delete 
        // the physical files)

        if (deleteStorageLocations != null) {
            datafileService.finalizeFileDeletes(deleteStorageLocations);
        }

        // must have been a bulk file update or delete:
        if (bulkFileDeleteInProgress) {
            JsfHelper.addFlashSuccessMessage(BundleUtil.getStringFromBundle("dataset.message.bulkFileDeleteSuccess"));
        } else {
            JsfHelper.addFlashSuccessMessage(BundleUtil.getStringFromBundle("dataset.message.bulkFileUpdateSuccess"));
        }
        bulkFileDeleteInProgress = false;

        logger.fine("Redirecting to the Dataset page.");

        return returnToDraftVersion();
    }

    private void populateDatasetUpdateFailureMessage() {
        // that must have been a bulk file update or delete:
        if (bulkFileDeleteInProgress) {
            JsfHelper.addFlashErrorMessage(BundleUtil.getStringFromBundle("dataset.message.bulkFileDeleteFailure"));
        } else {
            JsfHelper.addFlashErrorMessage(BundleUtil.getStringFromBundle("dataset.message.filesFailure"));
        }
        bulkFileDeleteInProgress = false;
    }

    private String returnToDraftVersion() {
        return "/dataset.xhtml?persistentId=" + dataset.getGlobalIdString() + "&version=DRAFT" + "&faces-redirect=true";
    }
    
    // -------------------- SETTERS --------------------

    public void setRemoveUnusedTags(boolean removeUnusedTags) {
        this.removeUnusedTags = removeUnusedTags;
    }

    public void setSelectedFiles(List<FileMetadata> selectedFiles) {
        this.selectedFiles = selectedFiles;
    }

    public void setSelectAllFiles(boolean selectAllFiles) {
        this.selectAllFiles = selectAllFiles;
    }

    public void setFileLabelSearchTerm(String fileLabelSearchTerm) {
        this.fileLabelSearchTerm = StringUtils.trimToEmpty(fileLabelSearchTerm);
    }

    public void setGuestbookResponse(GuestbookResponse guestbookResponse) {
        this.guestbookResponse = guestbookResponse;
    }

    public void setSelectedTabFileTags(String[] selectedTabFileTags) {
        this.selectedTabFileTags = selectedTabFileTags;
    }

    public void setSelectedTags(String[] selectedTags) {
        this.selectedTags = selectedTags;
    }

    public void setNewCategoryName(String newCategoryName) {
        this.newCategoryName = newCategoryName;
    }
    
    
}
