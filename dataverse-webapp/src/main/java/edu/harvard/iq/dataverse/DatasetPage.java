package edu.harvard.iq.dataverse;

import edu.harvard.iq.dataverse.api.AbstractApiBean;
import edu.harvard.iq.dataverse.common.BundleUtil;
import edu.harvard.iq.dataverse.common.DatasetFieldConstant;
import edu.harvard.iq.dataverse.dataaccess.DataAccess;
import edu.harvard.iq.dataverse.dataaccess.ImageThumbConverter;
import edu.harvard.iq.dataverse.datacapturemodule.DataCaptureModuleUtil;
import edu.harvard.iq.dataverse.datacapturemodule.ScriptRequestResponse;
import edu.harvard.iq.dataverse.dataset.DatasetThumbnail;
import edu.harvard.iq.dataverse.dataset.DatasetUtil;
import edu.harvard.iq.dataverse.dataset.tab.DatasetMetadataTab;
import edu.harvard.iq.dataverse.engine.command.Command;
import edu.harvard.iq.dataverse.engine.command.exception.CommandException;
import edu.harvard.iq.dataverse.engine.command.exception.IllegalCommandException;
import edu.harvard.iq.dataverse.engine.command.impl.AbstractSubmitToArchiveCommand;
import edu.harvard.iq.dataverse.engine.command.impl.CreateNewDatasetCommand;
import edu.harvard.iq.dataverse.engine.command.impl.CreatePrivateUrlCommand;
import edu.harvard.iq.dataverse.engine.command.impl.CuratePublishedDatasetVersionCommand;
import edu.harvard.iq.dataverse.engine.command.impl.DeleteDatasetVersionCommand;
import edu.harvard.iq.dataverse.engine.command.impl.DeletePrivateUrlCommand;
import edu.harvard.iq.dataverse.engine.command.impl.DestroyDatasetCommand;
import edu.harvard.iq.dataverse.engine.command.impl.GetPrivateUrlCommand;
import edu.harvard.iq.dataverse.engine.command.impl.LinkDatasetCommand;
import edu.harvard.iq.dataverse.engine.command.impl.PublishDatasetCommand;
import edu.harvard.iq.dataverse.engine.command.impl.PublishDatasetResult;
import edu.harvard.iq.dataverse.engine.command.impl.PublishDataverseCommand;
import edu.harvard.iq.dataverse.engine.command.impl.RequestRsyncScriptCommand;
import edu.harvard.iq.dataverse.engine.command.impl.ReturnDatasetToAuthorCommand;
import edu.harvard.iq.dataverse.engine.command.impl.SubmitDatasetForReviewCommand;
import edu.harvard.iq.dataverse.engine.command.impl.UpdateDatasetVersionCommand;
import edu.harvard.iq.dataverse.externaltools.ExternalToolServiceBean;
import edu.harvard.iq.dataverse.ingest.IngestServiceBean;
import edu.harvard.iq.dataverse.license.TermsOfUseFormMapper;
import edu.harvard.iq.dataverse.notification.NotificationObjectType;
import edu.harvard.iq.dataverse.notification.UserNotificationService;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.datafile.DataFileCategory;
import edu.harvard.iq.dataverse.persistence.datafile.DataFileTag;
import edu.harvard.iq.dataverse.persistence.datafile.ExternalTool;
import edu.harvard.iq.dataverse.persistence.datafile.FileMetadata;
import edu.harvard.iq.dataverse.persistence.datafile.MapLayerMetadata;
import edu.harvard.iq.dataverse.persistence.datafile.license.FileTermsOfUse;
import edu.harvard.iq.dataverse.persistence.datafile.license.FileTermsOfUse.TermsOfUseType;
import edu.harvard.iq.dataverse.persistence.datafile.license.TermsOfUseForm;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetField;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetFieldCompoundValue;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetFieldType;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetLock;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.dataset.Template;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.guestbook.GuestbookResponse;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.persistence.user.NotificationType;
import edu.harvard.iq.dataverse.persistence.user.Permission;
import edu.harvard.iq.dataverse.persistence.user.PrivateUrlUser;
import edu.harvard.iq.dataverse.privateurl.PrivateUrl;
import edu.harvard.iq.dataverse.privateurl.PrivateUrlUtil;
import edu.harvard.iq.dataverse.provenance.ProvPopupFragmentBean;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import edu.harvard.iq.dataverse.util.ArchiverUtil;
import edu.harvard.iq.dataverse.util.FileUtil;
import edu.harvard.iq.dataverse.util.JsfHelper;
import edu.harvard.iq.dataverse.util.StringUtil;
import edu.harvard.iq.dataverse.util.SystemConfig;
import org.apache.commons.lang.StringEscapeUtils;
import org.primefaces.context.RequestContext;
import org.primefaces.event.CloseEvent;
import org.primefaces.event.data.PageEvent;

import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.faces.application.FacesMessage;
import javax.faces.component.UIInput;
import javax.faces.context.FacesContext;
import javax.faces.event.ActionEvent;
import javax.faces.event.AjaxBehaviorEvent;
import javax.faces.event.ValueChangeEvent;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.validation.ConstraintViolation;
import java.io.IOException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static edu.harvard.iq.dataverse.util.JsfHelper.JH;

/**
 * @author gdurand
 */
@ViewScoped
@Named("DatasetPage")
public class DatasetPage implements java.io.Serializable {

    private static final Logger logger = Logger.getLogger(DatasetPage.class.getCanonicalName());

    public enum EditMode {
        CREATE
    }


    @EJB
    DatasetServiceBean datasetService;
    @EJB
    DatasetVersionServiceBean datasetVersionService;
    @EJB
    DataFileServiceBean datafileService;
    @EJB
    PermissionServiceBean permissionService;
    @EJB
    DataverseServiceBean dataverseService;
    @EJB
    DatasetFieldServiceBean fieldService;
    @EJB
    IngestServiceBean ingestService;
    @EJB
    EjbDataverseEngine commandEngine;
    @Inject
    DataverseSession session;
    @EJB
    UserNotificationService userNotificationService;
    @EJB
    MapLayerMetadataServiceBean mapLayerMetadataService;
    @EJB
    DataverseFieldTypeInputLevelServiceBean dataverseFieldTypeInputLevelService;
    @EJB
    SettingsServiceBean settingsService;
    @EJB
    SystemConfig systemConfig;
    @EJB
    GuestbookResponseServiceBean guestbookResponseService;
    @EJB
    FileDownloadServiceBean fileDownloadService;
    @EJB
    ExternalToolServiceBean externalToolService;
    @EJB
    TermsOfUseFormMapper termsOfUseFormMapper;
    @Inject
    DataverseRequestServiceBean dvRequestService;
    @Inject
    DatasetVersionUI datasetVersionUI;
    @Inject
    PermissionsWrapper permissionsWrapper;
    @Inject
    FileDownloadHelper fileDownloadHelper;
    @Inject
    ThumbnailServiceWrapper thumbnailServiceWrapper;
    @Inject
    ProvPopupFragmentBean provPopupFragmentBean;
    @Inject
    private DatasetMetadataTab metadataTab;

    private Dataset dataset = new Dataset();
    private EditMode editMode;
    private boolean bulkFileDeleteInProgress = false;

    private Long ownerId;
    private Long versionId;
    private int selectedTabIndex;
    private List<DataFile> newFiles = new ArrayList<>();
    private DatasetVersion workingVersion;
    private DatasetVersion clone;
    private int releaseRadio = 1;
    private String datasetNextMajorVersion = "1.0";
    private String datasetNextMinorVersion = "";
    private String displayCitation;
    private List<Template> dataverseTemplates = new ArrayList<>();
    private Template selectedTemplate;
    /**
     * In the file listing, the page the user is on. This is zero-indexed so if
     * the user clicks page 2 in the UI, this will be 1.
     */
    private int filePaginatorPage;
    private int rowsPerPage;

    private String persistentId;
    private String version;

    private boolean stateChanged = false;


    private String dataverseSiteUrl = "";

    private boolean removeUnusedTags;

    private Boolean hasTabular = false;

    private List<ExternalTool> configureTools = new ArrayList<>();
    private List<ExternalTool> exploreTools = new ArrayList<>();
    private Map<Long, List<ExternalTool>> configureToolsByFileId = new HashMap<>();
    private Map<Long, List<ExternalTool>> exploreToolsByFileId = new HashMap<>();

    private Boolean sameTermsOfUseForAllFiles;

    /**
     * The contents of the script.
     */
    private String rsyncScript = "";

    public String getRsyncScript() {
        return rsyncScript;
    }

    public void setRsyncScript(String rsyncScript) {
        this.rsyncScript = rsyncScript;
    }

    private String rsyncScriptFilename;

    private String thumbnailString = null;

    // This is the Dataset-level thumbnail; 
    // it's either the thumbnail of the designated datafile, 
    // or scaled down uploaded "logo" file, or randomly selected
    // image datafile from this dataset. 
    public String getThumbnailString() {
        // This method gets called 30 (!) times, just to load the page!
        // - so let's cache that string the first time it's called. 

        if (thumbnailString != null) {
            if ("".equals(thumbnailString)) {
                return null;
            }
            return thumbnailString;
        }

        if (!readOnly) {
            DatasetThumbnail datasetThumbnail = DatasetUtil.getThumbnail(dataset);
            if (datasetThumbnail == null) {
                thumbnailString = "";
                return null;
            }

            if (datasetThumbnail.isFromDataFile()) {
                if (!datasetThumbnail.getDataFile().equals(dataset.getThumbnailFile())) {
                    datasetService.assignDatasetThumbnailByNativeQuery(dataset, datasetThumbnail.getDataFile());
                    // refresh the dataset:
                    dataset = datasetService.find(dataset.getId());
                }
            }

            thumbnailString = datasetThumbnail.getBase64image();
        } else {
            thumbnailString = thumbnailServiceWrapper.getDatasetCardImageAsBase64Url(dataset, workingVersion.getId(), !workingVersion.isDraft(), new DataAccess());
            if (thumbnailString == null) {
                thumbnailString = "";
                return null;
            }


        }
        return thumbnailString;
    }

    public void setThumbnailString(String thumbnailString) {
        //Dummy method
    }

    public boolean isRemoveUnusedTags() {
        return removeUnusedTags;
    }

    public void setRemoveUnusedTags(boolean removeUnusedTags) {
        this.removeUnusedTags = removeUnusedTags;
    }

    private List<FileMetadata> fileMetadatas;
    private String fileSortField;
    private String fileSortOrder;

    private String fileLabelSearchTerm;

    public String getFileLabelSearchTerm() {
        return fileLabelSearchTerm;
    }

    public void setFileLabelSearchTerm(String fileLabelSearchTerm) {
        if (fileLabelSearchTerm != null) {
            this.fileLabelSearchTerm = fileLabelSearchTerm.trim();
        } else {
            this.fileLabelSearchTerm = "";
        }
    }

    private List<FileMetadata> fileMetadatasSearch;

    public List<FileMetadata> getFileMetadatasSearch() {
        return fileMetadatasSearch;
    }

    public void setFileMetadatasSearch(List<FileMetadata> fileMetadatasSearch) {
        this.fileMetadatasSearch = fileMetadatasSearch;
    }

    public void updateFileSearch() {
        logger.info("updating file search list");
        if (readOnly) {
            this.fileMetadatasSearch = selectFileMetadatasForDisplay(this.fileLabelSearchTerm);
        } else {
            this.fileMetadatasSearch = datafileService.findFileMetadataByDatasetVersionIdLabelSearchTerm(workingVersion.getId(), this.fileLabelSearchTerm, "", "");
        }
    }

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

    public String getDataverseSiteUrl() {
        return this.dataverseSiteUrl;
    }

    public void setDataverseSiteUrl(String dataverseSiteUrl) {
        this.dataverseSiteUrl = dataverseSiteUrl;
    }

    public List<DataFile> getNewFiles() {
        return newFiles;
    }

    public void setNewFiles(List<DataFile> newFiles) {
        this.newFiles = newFiles;
    }

    private Map<Long, String> datafileThumbnailsMap = new HashMap<>();

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

    public String getDataFileThumbnailAsBase64(FileMetadata fileMetadata) {
        return datafileThumbnailsMap.get(fileMetadata.getDataFile().getId());
    }

    // Another convenience method - to cache Update Permission on the dataset: 
    public boolean canUpdateDataset() {
        return permissionsWrapper.canUpdateDataset(dvRequestService.getDataverseRequest(), this.dataset);
    }

    public boolean canPublishDataverse() {
        return permissionsWrapper.canIssuePublishDataverseCommand(dataset.getOwner());
    }

    public boolean canViewUnpublishedDataset() {
        return permissionsWrapper.canViewUnpublishedDataset(dvRequestService.getDataverseRequest(), dataset);
    }

    /*
     * 4.2.1 optimization.
     * HOWEVER, this doesn't appear to be saving us anything!
     * i.e., it's just as cheap to use session.getUser().isAuthenticated()
     * every time; it doesn't do any new db lookups.
     */
    public boolean isSessionUserAuthenticated() {
        return session.getUser().isAuthenticated();
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

    private final Map<Long, MapLayerMetadata> mapLayerMetadataLookup = new HashMap<>();

    private GuestbookResponse guestbookResponse;


    public GuestbookResponse getGuestbookResponse() {
        return guestbookResponse;
    }

    public void setGuestbookResponse(GuestbookResponse guestbookResponse) {
        this.guestbookResponse = guestbookResponse;
    }

    public void reset() {
        dataset.setGuestbook(null);
    }

    public int getFilePaginatorPage() {
        return filePaginatorPage;
    }

    public void setFilePaginatorPage(int filePaginatorPage) {
        this.filePaginatorPage = filePaginatorPage;
    }


    public int getRowsPerPage() {
        return rowsPerPage;
    }

    public void setRowsPerPage(int rowsPerPage) {
        this.rowsPerPage = rowsPerPage;
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

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getDisplayCitation() {
        //displayCitation = dataset.getCitation(false, workingVersion);
        return displayCitation;
    }

    public void setDisplayCitation(String displayCitation) {
        this.displayCitation = displayCitation;
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

    public EditMode getEditMode() {
        return editMode;
    }

    public void setEditMode(EditMode editMode) {
        this.editMode = editMode;
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

    public int getSelectedTabIndex() {
        return selectedTabIndex;
    }

    public void setSelectedTabIndex(int selectedTabIndex) {
        this.selectedTabIndex = selectedTabIndex;
    }

    public int getReleaseRadio() {
        return releaseRadio;
    }

    public void setReleaseRadio(int releaseRadio) {
        this.releaseRadio = releaseRadio;
    }

    public String getDatasetNextMajorVersion() {
        return datasetNextMajorVersion;
    }

    public void setDatasetNextMajorVersion(String datasetNextMajorVersion) {
        this.datasetNextMajorVersion = datasetNextMajorVersion;
    }

    public String getDatasetNextMinorVersion() {
        return datasetNextMinorVersion;
    }

    public void setDatasetNextMinorVersion(String datasetNextMinorVersion) {
        this.datasetNextMinorVersion = datasetNextMinorVersion;
    }

    public List<Template> getDataverseTemplates() {
        return dataverseTemplates;
    }

    public void setDataverseTemplates(List<Template> dataverseTemplates) {
        this.dataverseTemplates = dataverseTemplates;
    }

    public Template getSelectedTemplate() {
        return selectedTemplate;
    }

    public void setSelectedTemplate(Template selectedTemplate) {
        this.selectedTemplate = selectedTemplate;
    }

    public void updateSelectedTemplate(ValueChangeEvent event) {

        selectedTemplate = (Template) event.getNewValue();
        if (selectedTemplate != null) {
            //then create new working version from the selected template
            workingVersion.updateDefaultValuesFromTemplate(selectedTemplate);
            updateDatasetFieldInputLevels();
        } else {
            workingVersion.initDefaultValues();
            updateDatasetFieldInputLevels();
        }
        resetVersionUI();
    }

    /***
     *
     * Note: Updated to retrieve DataverseFieldTypeInputLevel objects in single query
     *
     */
    private void updateDatasetFieldInputLevels() {
        // OPTIMIZATION (?): replaced "dataverseService.find(ownerId)" with
        // simply dataset.getOwner()... saves us a few lookups.
        // TODO: could there possibly be any reason we want to look this
        // dataverse up by the id here?? -- L.A. 4.2.1
        Long dvIdForInputLevel = dataset.getOwner().getMetadataRootId();
        
        List<DatasetField> datasetFields = workingVersion.getFlatDatasetFields();
        List<Long> datasetFieldTypeIds = new ArrayList<>();
        
        for (DatasetField dsf: datasetFields) {
            datasetFieldTypeIds.add(dsf.getDatasetFieldType().getId());
        }
        
        List<Long> fieldTypeIdsToHide = dataverseFieldTypeInputLevelService
                .findByDataverseIdAndDatasetFieldTypeIdList(dvIdForInputLevel, datasetFieldTypeIds).stream()
                .filter(inputLevel -> !inputLevel.isInclude())
                .map(inputLevel -> inputLevel.getDatasetFieldType().getId())
                .collect(Collectors.toList());
        
        
        for (DatasetField dsf: datasetFields) {
            dsf.setInclude(true);
            if (fieldTypeIdsToHide.contains(dsf.getDatasetFieldType().getId())) {
                dsf.setInclude(false);
            }
        }
    }

    public void handleChangeButton() {

    }

    /**
     * Create a hashmap consisting of { DataFile.id : MapLayerMetadata object}
     * <p>
     * Very few DataFiles will have associated MapLayerMetadata objects so only
     * use 1 query to get them
     */
    private void loadMapLayerMetadataLookup() {
        if (this.dataset == null) {
        }
        if (this.dataset.getId() == null) {
            return;
        }
        List<MapLayerMetadata> mapLayerMetadataList = mapLayerMetadataService.getMapLayerMetadataForDataset(this.dataset);
        if (mapLayerMetadataList == null) {
            return;
        }
        for (MapLayerMetadata layer_metadata : mapLayerMetadataList) {
            mapLayerMetadataLookup.put(layer_metadata.getDataFile().getId(), layer_metadata);
        }

    }// A DataFile may have a related MapLayerMetadata object


    private boolean readOnly = true;

    public String init() {
        return init(true);
    }

    public String initCitation() {
        return init(false);
    }

    private String init(boolean initFull) {
        //System.out.println("_YE_OLDE_QUERY_COUNTER_");  // for debug purposes
        Long maxFileUploadSizeInBytes = settingsService.getValueForKeyAsLong(SettingsServiceBean.Key.MaxFileUploadSizeInBytes);
        setDataverseSiteUrl(systemConfig.getDataverseSiteUrl());

        guestbookResponse = new GuestbookResponse();

        String protocol = settingsService.getValueForKey(SettingsServiceBean.Key.Protocol);
        String authority = settingsService.getValueForKey(SettingsServiceBean.Key.Authority);
        if (dataset.getId() != null || versionId != null || persistentId != null) { // view mode for a dataset     

            DatasetVersionServiceBean.RetrieveDatasetVersionResponse retrieveDatasetVersionResponse = null;

            // ---------------------------------------
            // Set the workingVersion and Dataset
            // ---------------------------------------           
            if (persistentId != null) {
                logger.fine("initializing DatasetPage with persistent ID " + persistentId);
                // Set Working Version and Dataset by PersistentID
                dataset = datasetService.findByGlobalId(persistentId);
                if (dataset == null) {
                    logger.warning("No such dataset: " + persistentId);
                    return permissionsWrapper.notFound();
                }
                logger.fine("retrieved dataset, id=" + dataset.getId());

                retrieveDatasetVersionResponse = datasetVersionService.selectRequestedVersion(dataset.getVersions(), version);
                //retrieveDatasetVersionResponse = datasetVersionService.retrieveDatasetVersionByPersistentId(persistentId, version);
                this.workingVersion = retrieveDatasetVersionResponse.getDatasetVersion();
                logger.fine("retrieved version: id: " + workingVersion.getId() + ", state: " + this.workingVersion.getVersionState());

            } else if (dataset.getId() != null) {
                // Set Working Version and Dataset by Datasaet Id and Version
                dataset = datasetService.find(dataset.getId());
                if (dataset == null) {
                    logger.warning("No such dataset: " + dataset);
                    return permissionsWrapper.notFound();
                }
                //retrieveDatasetVersionResponse = datasetVersionService.retrieveDatasetVersionById(dataset.getId(), version);
                retrieveDatasetVersionResponse = datasetVersionService.selectRequestedVersion(dataset.getVersions(), version);
                this.workingVersion = retrieveDatasetVersionResponse.getDatasetVersion();
                logger.info("retreived version: id: " + workingVersion.getId() + ", state: " + this.workingVersion.getVersionState());

            } else if (versionId != null) {
                // TODO: 4.2.1 - this method is broken as of now!
                // Set Working Version and Dataset by DatasaetVersion Id
                //retrieveDatasetVersionResponse = datasetVersionService.retrieveDatasetVersionByVersionId(versionId);

            }

            if (retrieveDatasetVersionResponse == null) {
                return permissionsWrapper.notFound();
            }


            //this.dataset = this.workingVersion.getDataset();

            // end: Set the workingVersion and Dataset
            // ---------------------------------------
            // Is the DatasetVersion or Dataset null?
            //
            if (workingVersion == null || this.dataset == null) {
                return permissionsWrapper.notFound();
            }

            // Is the Dataset harvested?

            if (dataset.isHarvested()) {
                // if so, we'll simply forward to the remote URL for the original
                // source of this harvested dataset:
                String originalSourceURL = dataset.getRemoteArchiveURL();
                if (originalSourceURL != null && !originalSourceURL.equals("")) {
                    logger.fine("redirecting to " + originalSourceURL);
                    try {
                        FacesContext.getCurrentInstance().getExternalContext().redirect(originalSourceURL);
                    } catch (IOException ioex) {
                        // must be a bad URL...
                        // we don't need to do anything special here - we'll redirect
                        // to the local 404 page, below.
                        logger.warning("failed to issue a redirect to " + originalSourceURL);
                    }
                    return originalSourceURL;
                }

                return permissionsWrapper.notFound();
            }

            // Check permisisons           
            if (!(workingVersion.isReleased() || workingVersion.isDeaccessioned()) && !this.canViewUnpublishedDataset()) {
                return permissionsWrapper.notAuthorized();
            }

            if (!retrieveDatasetVersionResponse.wasRequestedVersionRetrieved()) {
                //msg("checkit " + retrieveDatasetVersionResponse.getDifferentVersionMessage());
                JsfHelper.addFlashWarningMessage(retrieveDatasetVersionResponse.getDifferentVersionMessage());//BundleUtil.getStringFromBundle("dataset.message.metadataSuccess"));
            }

            // init the citation
            displayCitation = dataset.getCitation(true, workingVersion);


            if (initFull) {
                // init the list of FileMetadatas
                if (workingVersion.isDraft() && canUpdateDataset()) {
                    readOnly = false;
                }
                fileMetadatasSearch = workingVersion.getFileMetadatasSorted();

                ownerId = dataset.getOwner().getId();
                datasetNextMajorVersion = this.dataset.getNextMajorVersionString();
                datasetNextMinorVersion = this.dataset.getNextMinorVersionString();
                datasetVersionUI = datasetVersionUI.initDatasetVersionUI(workingVersion, false);
                updateDatasetFieldInputLevels();

                setExistReleasedVersion(resetExistRealeaseVersion());
                //moving setVersionTabList to tab change event
                //setVersionTabList(resetVersionTabList());
                //setReleasedVersionTabList(resetReleasedVersionTabList());
                //SEK - lazymodel may be needed for datascroller in future release
                // lazyModel = new LazyFileMetadataDataModel(workingVersion.getId(), datafileService );
                // populate MapLayerMetadata
                this.loadMapLayerMetadataLookup();  // A DataFile may have a related MapLayerMetadata object
                this.guestbookResponse = guestbookResponseService.initGuestbookResponseForFragment(workingVersion, null, session);
                this.getFileDownloadHelper().setGuestbookResponse(guestbookResponse);
                logger.fine("Checking if rsync support is enabled.");
                if (DataCaptureModuleUtil.rsyncSupportEnabled(settingsService.getValueForKey(SettingsServiceBean.Key.UploadMethods))
                        && dataset.getFiles().isEmpty()) { //only check for rsync if no files exist
                    try {
                        ScriptRequestResponse scriptRequestResponse = commandEngine.submit(new RequestRsyncScriptCommand(dvRequestService.getDataverseRequest(), dataset));
                        logger.fine("script: " + scriptRequestResponse.getScript());
                        if (scriptRequestResponse.getScript() != null && !scriptRequestResponse.getScript().isEmpty()) {
                            setRsyncScript(scriptRequestResponse.getScript());
                            rsyncScriptFilename = "upload-" + workingVersion.getDataset().getIdentifier() + ".bash";
                            rsyncScriptFilename = rsyncScriptFilename.replace("/", "_");
                        }
                    } catch (RuntimeException ex) {
                        logger.warning("Problem getting rsync script: " + ex.getLocalizedMessage());
                    } catch (CommandException cex) {
                        logger.warning("Problem getting rsync script (Command Exception): " + cex.getLocalizedMessage());
                    }
                }

            }
        } else if (ownerId != null) {
            // create mode for a new child dataset
            readOnly = false;
            editMode = EditMode.CREATE;
            dataset.setOwner(dataverseService.find(ownerId));
            dataset.setProtocol(protocol);
            dataset.setAuthority(authority);
            //Wait until the create command before actually getting an identifier  

            if (dataset.getOwner() == null) {
                return permissionsWrapper.notFound();
            } else if (!permissionService.on(dataset.getOwner()).has(Permission.AddDataset)) {
                return permissionsWrapper.notAuthorized();
            }

            dataverseTemplates.addAll(dataverseService.find(ownerId).getTemplates());
            if (!dataverseService.find(ownerId).isTemplateRoot()) {
                dataverseTemplates.addAll(dataverseService.find(ownerId).getParentTemplates());
            }
            Collections.sort(dataverseTemplates, (Template t1, Template t2) -> t1.getName().compareToIgnoreCase(t2.getName()));

            Template defaultTemplate = dataverseService.find(ownerId).getDefaultTemplate();
            if (defaultTemplate != null) {
                selectedTemplate = defaultTemplate;
                for (Template testT : dataverseTemplates) {
                    if (defaultTemplate.getId().equals(testT.getId())) {
                        selectedTemplate = testT;
                    }
                }
                workingVersion = dataset.getEditVersion(selectedTemplate);
                updateDatasetFieldInputLevels();
            } else {
                workingVersion = dataset.getCreateVersion();
                updateDatasetFieldInputLevels();
            }

            if (settingsService.isTrueForKey(SettingsServiceBean.Key.PublicInstall)) {
                JH.addMessage(FacesMessage.SEVERITY_WARN, BundleUtil.getStringFromBundle("dataset.message.publicInstall"));
            }

            resetVersionUI();

            // FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, "Add New Dataset", " - Enter metadata to create the dataset's citation. You can add more metadata about this dataset after it's created."));
        } else {
            return permissionsWrapper.notFound();
        }
        try {
            privateUrl = commandEngine.submit(new GetPrivateUrlCommand(dvRequestService.getDataverseRequest(), dataset));
            if (privateUrl != null) {
                JH.addMessage(FacesMessage.SEVERITY_INFO, BundleUtil.getStringFromBundle("dataset.privateurl.infoMessageAuthor", Arrays.asList(getPrivateUrlLink(privateUrl))));
            }
        } catch (CommandException ex) {
            // No big deal. The user simply doesn't have access to create or delete a Private URL.
        }
        if (session.getUser() instanceof PrivateUrlUser) {
            PrivateUrlUser privateUrlUser = (PrivateUrlUser) session.getUser();
            if (dataset != null && dataset.getId().equals(privateUrlUser.getDatasetId())) {
                JH.addMessage(FacesMessage.SEVERITY_INFO, BundleUtil.getStringFromBundle("dataset.privateurl.infoMessageReviewer"));
            }
        }

        // Various info messages, when the dataset is locked (for various reasons):
        if (dataset.isLocked() && canUpdateDataset()) {
            if (dataset.isLockedFor(DatasetLock.Reason.Workflow)) {
                JH.addMessage(FacesMessage.SEVERITY_WARN, BundleUtil.getStringFromBundle("dataset.locked.message"),
                              BundleUtil.getStringFromBundle("dataset.locked.message.details"));
            }
            if (dataset.isLockedFor(DatasetLock.Reason.InReview)) {
                JH.addMessage(FacesMessage.SEVERITY_WARN, BundleUtil.getStringFromBundle("dataset.locked.inReview.message"),
                              BundleUtil.getStringFromBundle("dataset.inreview.infoMessage"));
            }
            if (dataset.isLockedFor(DatasetLock.Reason.DcmUpload)) {
                JH.addMessage(FacesMessage.SEVERITY_WARN, BundleUtil.getStringFromBundle("file.rsyncUpload.inProgressMessage.summary"),
                              BundleUtil.getStringFromBundle("file.rsyncUpload.inProgressMessage.details"));
                lockedDueToDcmUpload = true;
            }
            //This is a hack to remove dataset locks for File PID registration if 
            //the dataset is released
            //in testing we had cases where datasets with 1000 files were remaining locked after being published successfully
                /*if(dataset.getLatestVersion().isReleased() && dataset.isLockedFor(DatasetLock.Reason.pidRegister)){
                    datasetService.removeDatasetLocks(dataset.getId(), DatasetLock.Reason.pidRegister);
                }*/
            if (dataset.isLockedFor(DatasetLock.Reason.pidRegister)) {
                JH.addMessage(FacesMessage.SEVERITY_WARN, BundleUtil.getStringFromBundle("dataset.publish.workflow.message"),
                              BundleUtil.getStringFromBundle("dataset.pidRegister.workflow.inprogress"));
            }
        }

        for (DataFile f : dataset.getFiles()) {
            if (f.isTabularData()) {
                hasTabular = true;
                break;
            }
        }

        configureTools = externalToolService.findByType(ExternalTool.Type.CONFIGURE);
        exploreTools = externalToolService.findByType(ExternalTool.Type.EXPLORE);
        rowsPerPage = 10;
        return null;
    }

    public boolean isHasTabular() {
        return hasTabular;
    }


    private void resetVersionUI() {

        datasetVersionUI = datasetVersionUI.initDatasetVersionUI(workingVersion, true);
        if (isSessionUserAuthenticated()) {
            AuthenticatedUser au = (AuthenticatedUser) session.getUser();

            //On create set pre-populated fields
            for (DatasetField dsf : dataset.getEditVersion().getDatasetFields()) {
                if (dsf.getDatasetFieldType().getName().equals(DatasetFieldConstant.depositor) && dsf.isEmpty()) {
                    dsf.getDatasetFieldValues().get(0).setValue(au.getLastName() + ", " + au.getFirstName());
                }
                if (dsf.getDatasetFieldType().getName().equals(DatasetFieldConstant.dateOfDeposit) && dsf.isEmpty()) {
                    dsf.getDatasetFieldValues().get(0).setValue(new SimpleDateFormat("yyyy-MM-dd").format(new Timestamp(new Date().getTime())));
                }

                if (dsf.getDatasetFieldType().getName().equals(DatasetFieldConstant.datasetContact) && dsf.isEmpty()) {
                    for (DatasetFieldCompoundValue contactValue : dsf.getDatasetFieldCompoundValues()) {
                        for (DatasetField subField : contactValue.getChildDatasetFields()) {
                            if (subField.getDatasetFieldType().getName().equals(DatasetFieldConstant.datasetContactName)) {
                                subField.getDatasetFieldValues().get(0).setValue(au.getLastName() + ", " + au.getFirstName());
                            }
                            if (subField.getDatasetFieldType().getName().equals(DatasetFieldConstant.datasetContactAffiliation)) {
                                subField.getDatasetFieldValues().get(0).setValue(au.getAffiliation());
                            }
                            if (subField.getDatasetFieldType().getName().equals(DatasetFieldConstant.datasetContactEmail)) {
                                subField.getDatasetFieldValues().get(0).setValue(au.getEmail());
                            }
                        }
                    }
                }

                String creatorOrcidId = au.getOrcidId();
                if (dsf.getDatasetFieldType().getName().equals(DatasetFieldConstant.author) && dsf.isEmpty()) {
                    for (DatasetFieldCompoundValue authorValue : dsf.getDatasetFieldCompoundValues()) {
                        for (DatasetField subField : authorValue.getChildDatasetFields()) {
                            if (subField.getDatasetFieldType().getName().equals(DatasetFieldConstant.authorName)) {
                                subField.getDatasetFieldValues().get(0).setValue(au.getLastName() + ", " + au.getFirstName());
                            }
                            if (subField.getDatasetFieldType().getName().equals(DatasetFieldConstant.authorAffiliation)) {
                                subField.getDatasetFieldValues().get(0).setValue(au.getAffiliation());
                            }
                            if (creatorOrcidId != null) {
                                if (subField.getDatasetFieldType().getName().equals(DatasetFieldConstant.authorIdValue)) {
                                    subField.getDatasetFieldValues().get(0).setValue(creatorOrcidId);
                                }
                                if (subField.getDatasetFieldType().getName().equals(DatasetFieldConstant.authorIdType)) {
                                    DatasetFieldType authorIdTypeDatasetField = fieldService.findByName(DatasetFieldConstant.authorIdType);
                                    subField.setSingleControlledVocabularyValue(fieldService.findControlledVocabularyValueByDatasetFieldTypeAndStrValue(authorIdTypeDatasetField, "ORCID", true));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean bulkUpdateCheckVersion() {
        return workingVersion.isReleased();
    }

    private void refreshSelectedFiles() {
        if (readOnly) {
            dataset = datasetService.find(dataset.getId());
        }
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
        readOnly = false;
    }

    public void edit(EditMode editMode) {
        this.editMode = editMode;
        if (this.readOnly) {
            dataset = datasetService.find(dataset.getId());
        }
        workingVersion = dataset.getEditVersion();
        clone = workingVersion.cloneDatasetVersion();
        this.readOnly = false;
    }

    public String releaseDraft() {
        if (releaseRadio == 1) {
            return releaseDataset(true);
        } else if (releaseRadio == 2) {
            return releaseDataset(false);
        } else if (releaseRadio == 3) {
            return updateCurrentVersion();
        } else {
            return "Invalid Choice";
        }
    }

    public String releaseMajor() {
        return releaseDataset(false);
    }

    public String sendBackToContributor() {
        try {
            //FIXME - Get Return Comment from sendBackToContributor popup
            Command<Dataset> cmd = new ReturnDatasetToAuthorCommand(dvRequestService.getDataverseRequest(), dataset, "");
            dataset = commandEngine.submit(cmd);
            JsfHelper.addFlashSuccessMessage(BundleUtil.getStringFromBundle("dataset.reject.success"));
        } catch (CommandException ex) {
            String message = ex.getMessage();
            logger.log(Level.SEVERE, "sendBackToContributor: {0}", message);
            JsfHelper.addFlashErrorMessage(BundleUtil.getStringFromBundle("dataset.reject.failure", Collections.singletonList(message)));
        }

        return returnToLatestVersion();
    }

    public String submitDataset() {
        try {
            Command<Dataset> cmd = new SubmitDatasetForReviewCommand(dvRequestService.getDataverseRequest(), dataset);
            dataset = commandEngine.submit(cmd);
            //JsfHelper.addFlashSuccessMessage(BundleUtil.getStringFromBundle("dataset.submit.success"));
        } catch (CommandException ex) {
            String message = ex.getMessage();
            logger.log(Level.SEVERE, "submitDataset: {0}", message);
            JsfHelper.addFlashErrorMessage(BundleUtil.getStringFromBundle("dataset.submit.failure", Collections.singletonList(message)));
        }
        return returnToLatestVersion();
    }

    public String releaseParentDVAndDataset() {
        releaseParentDV();
        return releaseDataset(false);
    }

    public String releaseDataset() {
        return releaseDataset(false);
    }

    private void releaseParentDV() {
        if (session.getUser() instanceof AuthenticatedUser) {
            PublishDataverseCommand cmd = new PublishDataverseCommand(dvRequestService.getDataverseRequest(), dataset.getOwner());
            try {
                commandEngine.submit(cmd);
                JsfHelper.addFlashSuccessMessage(BundleUtil.getStringFromBundle("dataverse.publish.success"));

            } catch (CommandException ex) {
                logger.log(Level.SEVERE, "Unexpected Exception calling  publish dataverse command", ex);
                JsfHelper.addFlashErrorMessage(BundleUtil.getStringFromBundle("dataverse.publish.failure"));

            }
        } else {
            FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_INFO, BundleUtil.getStringFromBundle("dataverse.notreleased"), BundleUtil.getStringFromBundle("dataverse.release.authenticatedUsersOnly"));
            FacesContext.getCurrentInstance().addMessage(null, message);
        }

    }

    private String releaseDataset(boolean minor) {
        if (session.getUser() instanceof AuthenticatedUser) {
            try {
                final PublishDatasetResult result = commandEngine.submit(
                        new PublishDatasetCommand(dataset, dvRequestService.getDataverseRequest(), minor)
                );
                dataset = result.getDataset();
                // Sucessfully executing PublishDatasetCommand does not guarantee that the dataset 
                // has been published. If a publishing workflow is configured, this may have sent the 
                // dataset into a workflow limbo, potentially waiting for a third party system to complete 
                // the process. So it may be premature to show the "success" message at this point. 

                if (result.isCompleted()) {
                    JsfHelper.addFlashSuccessMessage(BundleUtil.getStringFromBundle("dataset.message.publishSuccess"));
                } else {
                    JH.addMessage(FacesMessage.SEVERITY_WARN, BundleUtil.getStringFromBundle("dataset.locked.message"), BundleUtil.getStringFromBundle("dataset.locked.message.details"));
                }

            } catch (CommandException ex) {
                JsfHelper.addFlashErrorMessage(ex.getLocalizedMessage());
                logger.severe(ex.getMessage());
            }

        } else {
            JsfHelper.addFlashErrorMessage(BundleUtil.getStringFromBundle("dataset.message.only.authenticatedUsers"));
        }
        return returnToDatasetOnly();
    }

    @Deprecated
    public String registerDataset() {
        try {
            UpdateDatasetVersionCommand cmd = new UpdateDatasetVersionCommand(dataset, dvRequestService.getDataverseRequest());
            cmd.setValidateLenient(true);
            dataset = commandEngine.submit(cmd);
        } catch (CommandException ex) {
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN, BundleUtil.getStringFromBundle("dataset.registration.failed"), " - " + ex.toString()));
            logger.severe(ex.getMessage());
        }
        FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_INFO, BundleUtil.getStringFromBundle("dataset.registered"), BundleUtil.getStringFromBundle("dataset.registered.msg"));
        FacesContext.getCurrentInstance().addMessage(null, message);
        return returnToDatasetOnly();
    }

    public String updateCurrentVersion() {
        /*
         * Note: The code here mirrors that in the
         * edu.harvard.iq.dataverse.api.Datasets:publishDataset method (case
         * "updatecurrent"). Any changes to the core logic (i.e. beyond updating the
         * messaging about results) should be applied to the code there as well.
         */
        String errorMsg = null;
        String successMsg = BundleUtil.getStringFromBundle("datasetversion.update.success");
        try {
            CuratePublishedDatasetVersionCommand cmd = new CuratePublishedDatasetVersionCommand(dataset, dvRequestService.getDataverseRequest());
            dataset = commandEngine.submit(cmd);
            // If configured, and currently published version is archived, try to update archive copy as well
            DatasetVersion updateVersion = dataset.getLatestVersion();
            if (updateVersion.getArchivalCopyLocation() != null) {
                String className = settingsService.getValueForKey(SettingsServiceBean.Key.ArchiverClassName);
                AbstractSubmitToArchiveCommand archiveCommand = ArchiverUtil.createSubmitToArchiveCommand(className, dvRequestService.getDataverseRequest(), updateVersion);
                if (archiveCommand != null) {
                    // Delete the record of any existing copy since it is now out of date/incorrect
                    updateVersion.setArchivalCopyLocation(null);
                    /*
                     * Then try to generate and submit an archival copy. Note that running this
                     * command within the CuratePublishedDatasetVersionCommand was causing an error:
                     * "The attribute [id] of class
                     * [edu.harvard.iq.dataverse.DatasetFieldCompoundValue] is mapped to a primary
                     * key column in the database. Updates are not allowed." To avoid that, and to
                     * simplify reporting back to the GUI whether this optional step succeeded, I've
                     * pulled this out as a separate submit().
                     */
                    try {
                        updateVersion = commandEngine.submit(archiveCommand);
                        if (updateVersion.getArchivalCopyLocation() != null) {
                            successMsg = BundleUtil.getStringFromBundle("datasetversion.update.archive.success");
                        } else {
                            errorMsg = BundleUtil.getStringFromBundle("datasetversion.update.archive.failure");
                        }
                    } catch (CommandException ex) {
                        errorMsg = BundleUtil.getStringFromBundle("datasetversion.update.archive.failure") + " - " + ex.toString();
                        logger.severe(ex.getMessage());
                    }
                }
            }
        } catch (CommandException ex) {
            errorMsg = BundleUtil.getStringFromBundle("datasetversion.update.failure") + " - " + ex.toString();
            logger.severe(ex.getMessage());
        }
        if (errorMsg != null) {
            JsfHelper.addFlashErrorMessage(errorMsg);
        } else {
            JsfHelper.addFlashSuccessMessage(successMsg);
        }
        return returnToDatasetOnly();
    }


    public void refresh(ActionEvent e) {
        refresh();
    }

    public void refresh() {
        logger.fine("refreshing");

        //dataset = datasetService.find(dataset.getId());
        dataset = null;

        logger.fine("refreshing working version");

        DatasetVersionServiceBean.RetrieveDatasetVersionResponse retrieveDatasetVersionResponse = null;

        if (persistentId != null) {
            //retrieveDatasetVersionResponse = datasetVersionService.retrieveDatasetVersionByPersistentId(persistentId, version);
            dataset = datasetService.findByGlobalId(persistentId);
            retrieveDatasetVersionResponse = datasetVersionService.selectRequestedVersion(dataset.getVersions(), version);
        } else if (versionId != null) {
            retrieveDatasetVersionResponse = datasetVersionService.retrieveDatasetVersionByVersionId(versionId);
        } else if (dataset.getId() != null) {
            //retrieveDatasetVersionResponse = datasetVersionService.retrieveDatasetVersionById(dataset.getId(), version);
            dataset = datasetService.find(dataset.getId());
            retrieveDatasetVersionResponse = datasetVersionService.selectRequestedVersion(dataset.getVersions(), version);
        }

        if (retrieveDatasetVersionResponse == null) {
            // TODO: 
            // should probably redirect to the 404 page, if we can't find 
            // this version anymore. 
            // -- L.A. 4.2.3 
            return;
        }
        this.workingVersion = retrieveDatasetVersionResponse.getDatasetVersion();

        if (this.workingVersion == null) {
            // TODO: 
            // same as the above

            return;
        }


        if (dataset == null) {
            // this would be the case if we were retrieving the version by 
            // the versionId, above.
            this.dataset = this.workingVersion.getDataset();
        }


        fileMetadatasSearch = workingVersion.getFileMetadatasSorted();

        displayCitation = dataset.getCitation(true, workingVersion);
        stateChanged = false;
        metadataTab.updateDatasetLockState(isLocked());
    }

    public String deleteDataset() {

        DestroyDatasetCommand cmd;
        boolean deleteCommandSuccess = false;
        Map<Long, String> deleteStorageLocations = datafileService.getPhysicalFilesToDelete(dataset);

        try {
            cmd = new DestroyDatasetCommand(dataset, dvRequestService.getDataverseRequest());
            commandEngine.submit(cmd);
            deleteCommandSuccess = true;
            /* - need to figure out what to do 
             Update notification in Delete Dataset Method
             for (UserNotification und : userNotificationService.findByDvObject(dataset.getId())){
             userNotificationService.delete(und);
             } */
        } catch (CommandException ex) {
            JH.addMessage(FacesMessage.SEVERITY_FATAL, BundleUtil.getStringFromBundle("dataset.message.deleteFailure"));
            logger.severe(ex.getMessage());
        }

        if (deleteCommandSuccess) {
            datafileService.finalizeFileDeletes(deleteStorageLocations);
            JsfHelper.addFlashSuccessMessage(BundleUtil.getStringFromBundle("dataset.message.deleteSuccess"));
        }

        return "/dataverse.xhtml?alias=" + dataset.getOwner().getAlias() + "&faces-redirect=true";
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
        return "/editdatafiles.xhtml?selectedFileIds=" + getSelectedFilesIdsString() + "&datasetId=" + dataset.getId() + "&faces-redirect=true";
    }

    public String deleteDatasetVersion() {
        DeleteDatasetVersionCommand cmd;
        try {
            cmd = new DeleteDatasetVersionCommand(dvRequestService.getDataverseRequest(), dataset);
            commandEngine.submit(cmd);
            JsfHelper.addFlashSuccessMessage(BundleUtil.getStringFromBundle("datasetVersion.message.deleteSuccess"));
        } catch (CommandException ex) {
            JH.addMessage(FacesMessage.SEVERITY_FATAL, BundleUtil.getStringFromBundle("dataset.message.deleteFailure"));
            logger.severe(ex.getMessage());
        }

        return returnToDatasetOnly();
    }

    private List<FileMetadata> selectedFiles = new ArrayList<>();

    public List<FileMetadata> getSelectedFiles() {
        return selectedFiles;
    }

    public void setSelectedFiles(List<FileMetadata> selectedFiles) {
        this.selectedFiles = selectedFiles;
    }

    private Dataverse selectedDataverseForLinking;

    public Dataverse getSelectedDataverseForLinking() {
        return selectedDataverseForLinking;
    }

    public void setSelectedDataverseForLinking(Dataverse sdvfl) {
        this.selectedDataverseForLinking = sdvfl;
    }

    private List<FileMetadata> selectedRestrictedFiles = new ArrayList<>();

    public List<FileMetadata> getSelectedRestrictedFiles() {
        return selectedRestrictedFiles;
    }

    public void setSelectedRestrictedFiles(List<FileMetadata> selectedRestrictedFiles) {
        this.selectedRestrictedFiles = selectedRestrictedFiles;
    }

    private List<FileMetadata> selectedUnrestrictedFiles; // = new ArrayList<>();

    public List<FileMetadata> getSelectedUnrestrictedFiles() {
        return selectedUnrestrictedFiles;
    }

    public void setSelectedUnrestrictedFiles(List<FileMetadata> selectedUnrestrictedFiles) {
        this.selectedUnrestrictedFiles = selectedUnrestrictedFiles;
    }

    private List<FileMetadata> selectedDownloadableFiles;

    public List<FileMetadata> getSelectedDownloadableFiles() {
        return selectedDownloadableFiles;
    }

    public void setSelectedDownloadableFiles(List<FileMetadata> selectedDownloadableFiles) {
        this.selectedDownloadableFiles = selectedDownloadableFiles;
    }

    private List<FileMetadata> selectedNonDownloadableFiles;

    public List<FileMetadata> getSelectedNonDownloadableFiles() {
        return selectedNonDownloadableFiles;
    }

    public void setSelectedNonDownloadableFiles(List<FileMetadata> selectedNonDownloadableFiles) {
        this.selectedNonDownloadableFiles = selectedNonDownloadableFiles;
    }


    public void validateFilesForDownload(boolean guestbookRequired, boolean downloadOriginal) {
        setSelectedDownloadableFiles(new ArrayList<>());
        setSelectedNonDownloadableFiles(new ArrayList<>());

        if (this.selectedFiles.isEmpty()) {
            RequestContext requestContext = RequestContext.getCurrentInstance();
            requestContext.execute("PF('selectFilesForDownload').show()");
            return;
        }
        for (FileMetadata fmd : this.selectedFiles) {
            if (this.fileDownloadHelper.canDownloadFile(fmd)) {
                getSelectedDownloadableFiles().add(fmd);
            } else {
                getSelectedNonDownloadableFiles().add(fmd);
            }
        }

        // If some of the files were restricted and we had to drop them off the 
        // list, and NONE of the files are left on the downloadable list
        // - we show them a "you're out of luck" popup: 
        if (getSelectedDownloadableFiles().isEmpty() && !getSelectedNonDownloadableFiles().isEmpty()) {
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
        guestbookResponse.setSelectedFileIds(getSelectedDownloadableFilesIdsString());
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
        if (!getSelectedDownloadableFiles().isEmpty() && getSelectedNonDownloadableFiles().isEmpty()) {
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
        if (!getSelectedDownloadableFiles().isEmpty() && !getSelectedNonDownloadableFiles().isEmpty()) {
            RequestContext requestContext = RequestContext.getCurrentInstance();
            requestContext.execute("PF('downloadMixed').show()");
        }

    }

    private boolean selectAllFiles;

    public boolean isSelectAllFiles() {
        return selectAllFiles;
    }

    public void setSelectAllFiles(boolean selectAllFiles) {
        this.selectAllFiles = selectAllFiles;
    }

    public void toggleAllSelected() {
        //This is here so that if the user selects all on the dataset page
        // s/he will get all files on download
        this.selectAllFiles = !this.selectAllFiles;
    }


    // helper Method
    public String getSelectedFilesIdsString() {
        String downloadIdString = "";
        for (FileMetadata fmd : this.selectedFiles) {
            if (!StringUtil.isEmpty(downloadIdString)) {
                downloadIdString += ",";
            }
            downloadIdString += fmd.getDataFile().getId();
        }
        return downloadIdString;
    }

    // helper Method
    public String getSelectedDownloadableFilesIdsString() {
        String downloadIdString = "";
        for (FileMetadata fmd : this.selectedDownloadableFiles) {
            if (!StringUtil.isEmpty(downloadIdString)) {
                downloadIdString += ",";
            }
            downloadIdString += fmd.getDataFile().getId();
        }
        return downloadIdString;
    }


    public void updateFileCounts() {
        setSelectedUnrestrictedFiles(new ArrayList<>());
        setSelectedRestrictedFiles(new ArrayList<>());
        setTabularDataSelected(false);
        for (FileMetadata fmd : this.selectedFiles) {
            if (fmd.getTermsOfUse().getTermsOfUseType() == TermsOfUseType.RESTRICTED) {
                getSelectedRestrictedFiles().add(fmd);
            } else {
                getSelectedUnrestrictedFiles().add(fmd);
            }
            if (fmd.getDataFile().isTabularData()) {
                setTabularDataSelected(true);
            }
        }
    }


    private List<String> getSuccessMessageArguments() {
        List<String> arguments = new ArrayList<>();
        String dataverseString = "";
        arguments.add(StringEscapeUtils.escapeHtml(dataset.getDisplayName()));
        dataverseString += " <a href=\"/dataverse/" + selectedDataverseForLinking.getAlias() + "\">" + StringEscapeUtils.escapeHtml(selectedDataverseForLinking.getDisplayName()) + "</a>";
        arguments.add(dataverseString);
        return arguments;
    }


    public void saveLinkingDataverses(ActionEvent evt) {

        if (saveLink(selectedDataverseForLinking)) {
            JsfHelper.addFlashSuccessMessage(BundleUtil.getStringFromBundle("dataset.message.linkSuccess", getSuccessMessageArguments()));
        } else {
            FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_INFO, BundleUtil.getStringFromBundle("dataset.notlinked"), linkingDataverseErrorMessage);
            FacesContext.getCurrentInstance().addMessage(null, message);
        }

    }

    private String linkingDataverseErrorMessage = "";


    UIInput selectedLinkingDataverseMenu;

    public UIInput getSelectedDataverseMenu() {
        return selectedLinkingDataverseMenu;
    }

    public void setSelectedDataverseMenu(UIInput selectedDataverseMenu) {
        this.selectedLinkingDataverseMenu = selectedDataverseMenu;
    }


    private Boolean saveLink(Dataverse dataverse) {
        boolean retVal = true;
        if (readOnly) {
            // Pass a "real", non-readonly dataset the the LinkDatasetCommand: 
            dataset = datasetService.find(dataset.getId());
        }
        LinkDatasetCommand cmd = new LinkDatasetCommand(dvRequestService.getDataverseRequest(), dataverse, dataset);
        try {
            commandEngine.submit(cmd);
        } catch (CommandException ex) {
            String msg = "There was a problem linking this dataset to yours: " + ex;
            logger.severe(msg);
            msg = BundleUtil.getStringFromBundle("dataset.notlinked.msg") + ex;
            /**
             * @todo how do we get this message to show up in the GUI?
             */
            linkingDataverseErrorMessage = msg;
            retVal = false;
        }
        return retVal;
    }


    public List<Dataverse> completeLinkingDataverse(String query) {
        dataset = datasetService.find(dataset.getId());
        if (session.getUser().isAuthenticated()) {
            return dataverseService.filterDataversesForLinking(query, dvRequestService.getDataverseRequest(), dataset);
        } else {
            return null;
        }
    }

    private List<FileMetadata> filesToBeDeleted = new ArrayList<>();

    public String deleteFilesAndSave() {
        bulkFileDeleteInProgress = true;
        if (bulkUpdateCheckVersion()) {
            refreshSelectedFiles();
        }
        deleteFiles();
        return save();
    }

    public void deleteFiles() {

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

    public String save() {
        //Before dataset saved, write cached prov freeform to version
        if (settingsService.isTrueForKey(SettingsServiceBean.Key.ProvCollectionEnabled)) {
            provPopupFragmentBean.saveStageProvFreeformToLatestVersion();
        }

        // Validate
        Set<ConstraintViolation> constraintViolations = workingVersion.validate();
        if (!constraintViolations.isEmpty()) {
            //JsfHelper.addFlashMessage(BundleUtil.getStringFromBundle("dataset.message.validationError"));
            JH.addMessage(FacesMessage.SEVERITY_ERROR, BundleUtil.getStringFromBundle("dataset.message.validationError"));
            //FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Validation Error", "See below for details."));
            return "";
        }

        // Use the Create or Update command to save the dataset: 
        Command<Dataset> cmd;
        Map<Long, String> deleteStorageLocations = null;

        try {
            if (editMode == EditMode.CREATE) {
                if (selectedTemplate != null) {
                    if (isSessionUserAuthenticated()) {
                        cmd = new CreateNewDatasetCommand(dataset, dvRequestService.getDataverseRequest(), false, selectedTemplate);
                    } else {
                        JH.addMessage(FacesMessage.SEVERITY_FATAL, BundleUtil.getStringFromBundle("dataset.create.authenticatedUsersOnly"));
                        return null;
                    }
                } else {
                    cmd = new CreateNewDatasetCommand(dataset, dvRequestService.getDataverseRequest());
                }

            } else {
                if (!filesToBeDeleted.isEmpty()) {
                    deleteStorageLocations = datafileService.getPhysicalFilesToDelete(filesToBeDeleted);
                }
                cmd = new UpdateDatasetVersionCommand(dataset, dvRequestService.getDataverseRequest(), filesToBeDeleted, clone);
                ((UpdateDatasetVersionCommand) cmd).setValidateLenient(true);
            }
            dataset = commandEngine.submit(cmd);
            if (editMode == EditMode.CREATE) {
                if (session.getUser() instanceof AuthenticatedUser) {
                    userNotificationService.sendNotificationWithEmail((AuthenticatedUser) session.getUser(), dataset.getCreateDate(), NotificationType.CREATEDS, dataset.getLatestVersion().getId(), NotificationObjectType.DATASET_VERSION);
                }
            }
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

        if (editMode != null) {
            if (editMode.equals(EditMode.CREATE)) {
                // We allow users to upload files on Create: 
                int nNewFiles = newFiles.size();
                logger.fine("NEW FILES: " + nNewFiles);

                if (nNewFiles > 0) {
                    // Save the NEW files permanently and add the to the dataset: 

                    // But first, fully refresh the newly created dataset (with a 
                    // datasetService.find().
                    // We have reasons to believe that the CreateDatasetCommand 
                    // returns the dataset that doesn't have all the  
                    // RoleAssignments properly linked to it - even though they
                    // have been created in the dataset. 
                    dataset = datasetService.find(dataset.getId());

                    for (DataFile newFile : newFiles) {
                        TermsOfUseForm termsOfUseForm = newFile.getFileMetadata().getTermsOfUseForm();
                        FileTermsOfUse termsOfUse = termsOfUseFormMapper.mapToFileTermsOfUse(termsOfUseForm);

                        newFile.getFileMetadata().setTermsOfUse(termsOfUse);
                    }

                    List<DataFile> filesAdded = ingestService.saveAndAddFilesToDataset(dataset.getEditVersion(), newFiles, new DataAccess());
                    newFiles.clear();

                    // and another update command: 
                    boolean addFilesSuccess = false;
                    cmd = new UpdateDatasetVersionCommand(dataset, dvRequestService.getDataverseRequest(), new ArrayList<FileMetadata>());
                    try {
                        dataset = commandEngine.submit(cmd);
                        addFilesSuccess = true;
                    } catch (Exception ex) {
                        addFilesSuccess = false;
                    }
                    if (addFilesSuccess && dataset.getFiles().size() > 0) {
                        if (nNewFiles == dataset.getFiles().size()) {
                            JsfHelper.addFlashSuccessMessage(BundleUtil.getStringFromBundle("dataset.message.createSuccess"));
                        } else {
                            String partialSuccessMessage = BundleUtil.getStringFromBundle("dataset.message.createSuccess.partialSuccessSavingFiles");
                            partialSuccessMessage = partialSuccessMessage.replace("{0}", "" + dataset.getFiles().size() + "");
                            partialSuccessMessage = partialSuccessMessage.replace("{1}", "" + nNewFiles + "");
                            JsfHelper.addFlashWarningMessage(partialSuccessMessage);
                        }
                    } else {
                        JsfHelper.addFlashWarningMessage(BundleUtil.getStringFromBundle("dataset.message.createSuccess.failedToSaveFiles"));
                    }
                } else {
                    JsfHelper.addFlashSuccessMessage(BundleUtil.getStringFromBundle("dataset.message.createSuccess"));
                }
            }

        } else {
            // must have been a bulk file update or delete:
            if (bulkFileDeleteInProgress) {
                JsfHelper.addFlashSuccessMessage(BundleUtil.getStringFromBundle("dataset.message.bulkFileDeleteSuccess"));
            } else {
                JsfHelper.addFlashSuccessMessage(BundleUtil.getStringFromBundle("dataset.message.bulkFileUpdateSuccess"));
            }
        }

        editMode = null;
        bulkFileDeleteInProgress = false;


        // Call Ingest Service one more time, to
        // queue the data ingest jobs for asynchronous execution: 
        ingestService.startIngestJobsForDataset(dataset, (AuthenticatedUser) session.getUser());

        //After dataset saved, then persist prov json data
        if (settingsService.isTrueForKey(SettingsServiceBean.Key.ProvCollectionEnabled)) {
            try {
                provPopupFragmentBean.saveStagedProvJson(false, dataset.getLatestVersion().getFileMetadatas());
            } catch (AbstractApiBean.WrappedResponse ex) {
                JsfHelper.addFlashErrorMessage(BundleUtil.getStringFromBundle("file.metadataTab.provenance.error"));
                Logger.getLogger(DatasetPage.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        logger.fine("Redirecting to the Dataset page.");

        return returnToDraftVersion();
    }

    private void populateDatasetUpdateFailureMessage() {
        if (editMode == null) {
            // that must have been a bulk file update or delete:
            if (bulkFileDeleteInProgress) {
                JsfHelper.addFlashErrorMessage(BundleUtil.getStringFromBundle("dataset.message.bulkFileDeleteFailure"));
            } else {
                JsfHelper.addFlashErrorMessage(BundleUtil.getStringFromBundle("dataset.message.filesFailure"));
            }
        } else {
            JsfHelper.addFlashErrorMessage(BundleUtil.getStringFromBundle("dataset.message.createFailure"));
        }
        bulkFileDeleteInProgress = false;
    }

    private String returnToLatestVersion() {
        dataset = datasetService.find(dataset.getId());
        workingVersion = dataset.getLatestVersion();
        if (workingVersion.isDeaccessioned() && dataset.getReleasedVersion() != null) {
            workingVersion = dataset.getReleasedVersion();
        }
//        setVersionTabList(resetVersionTabList());
//        setReleasedVersionTabList(resetReleasedVersionTabList());
        newFiles.clear();
        editMode = null;
        return "/dataset.xhtml?persistentId=" + dataset.getGlobalIdString() + "&version=" + workingVersion.getFriendlyVersionNumber() + "&faces-redirect=true";
    }

    private String returnToDatasetOnly() {
        dataset = datasetService.find(dataset.getId());
        editMode = null;
        return "/dataset.xhtml?persistentId=" + dataset.getGlobalIdString() + "&faces-redirect=true";
    }

    private String returnToDraftVersion() {
        return "/dataset.xhtml?persistentId=" + dataset.getGlobalIdString() + "&version=DRAFT" + "&faces-redirect=true";
    }

    public String cancel() {
        return returnToLatestVersion();
    }

    public void refreshAllLocks() {

        logger.fine("checking all locks");
        if (isStillLockedForAnyReason()) {
            logger.fine("(still locked)");
        } else {

            logger.fine("no longer locked!");
            stateChanged = true;
            lockedFromEditsVar = null;
            lockedFromDownloadVar = null;
        }
    }

    /* 

    public boolean isLockedInProgress() {
        if (dataset != null) {
            logger.log(Level.FINE, "checking lock status of dataset {0}", dataset.getId());
            if (dataset.isLocked()) {
                return true;
            }
        }
        return false;
    }*/

    public boolean isDatasetLockedInWorkflow() {
        return (dataset != null) && dataset.isLockedFor(DatasetLock.Reason.Workflow);
    }


    public boolean isStillLockedForAnyReason() {
        if (dataset.getId() != null) {
            Dataset testDataset = datasetService.find(dataset.getId());
            if (testDataset != null && testDataset.getId() != null) {
                logger.log(Level.FINE, "checking lock status of dataset {0}", dataset.getId());
                return testDataset.getLocks().size() > 0;
            }
        }
        return false;
    }

    public boolean isLocked() {
        if (stateChanged) {
            return false;
        }

        if (dataset != null) {
            return dataset.isLocked();
        }
        return false;
    }

    public boolean isLockedForAnyReason() {
        if (dataset.getId() != null) {
            Dataset testDataset = datasetService.find(dataset.getId());
            if (stateChanged) {
                return false;
            }

            if (testDataset != null) {
                return testDataset.getLocks().size() > 0;
            }
        }
        return false;
    }

    private Boolean lockedFromEditsVar;
    private Boolean lockedFromDownloadVar;
    private boolean lockedDueToDcmUpload;

    /**
     * Authors are not allowed to edit but curators are allowed - when Dataset is inReview
     * For all other locks edit should be locked for all editors.
     */
    public boolean isLockedFromEdits() {
        if (null == lockedFromEditsVar || stateChanged) {
            try {
                permissionService.checkEditDatasetLock(dataset, dvRequestService.getDataverseRequest(), new UpdateDatasetVersionCommand(dataset, dvRequestService.getDataverseRequest()));
                lockedFromEditsVar = false;
            } catch (IllegalCommandException ex) {
                lockedFromEditsVar = true;
            }
        }
        return lockedFromEditsVar;
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
        if (null == lockedFromDownloadVar || stateChanged) {
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

    public boolean isLockedDueToDcmUpload() {
        return lockedDueToDcmUpload;
    }

    public void setLocked(boolean locked) {
        // empty method, so that we can use DatasetPage.locked in a hidden 
        // input on the page. 
    }

    public void setLockedForAnyReason(boolean locked) {
        // empty method, so that we can use DatasetPage.locked in a hidden 
        // input on the page. 
    }

    public boolean isStateChanged() {
        return stateChanged;
    }

    public void setStateChanged(boolean stateChanged) {
        // empty method, so that we can use DatasetPage.stateChanged in a hidden 
        // input on the page. 
    }

    public DatasetVersionUI getDatasetVersionUI() {
        return datasetVersionUI;
    }

    public void startMultipleFileDownload() {

        boolean doNotSaveGuestbookResponse = workingVersion.isDraft();
        // There's a chance that this is not really a batch download - i.e., 
        // there may only be one file on the downloadable list. But the fileDownloadService 
        // method below will check for that, and will redirect to the single download, if
        // that's the case. -- L.A.
        fileDownloadService.writeGuestbookAndStartBatchDownload(guestbookResponse, doNotSaveGuestbookResponse);
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


    private boolean existReleasedVersion;

    public boolean isExistReleasedVersion() {
        return existReleasedVersion;
    }

    public void setExistReleasedVersion(boolean existReleasedVersion) {
        this.existReleasedVersion = existReleasedVersion;
    }

    private boolean resetExistRealeaseVersion() {

        for (DatasetVersion version : dataset.getVersions()) {
            if (version.isReleased() || version.isArchived()) {
                return true;
            }
        }
        return false;

    }


    public String getDatasetPublishCustomText() {
        return settingsService.getValueForKey(SettingsServiceBean.Key.DatasetPublishPopupCustomText);
    }

    public Boolean isDatasetPublishPopupCustomTextOnAllVersions() {
        return settingsService.isTrueForKey(SettingsServiceBean.Key.DatasetPublishPopupCustomTextOnAllVersions);
    }

    /*
     * Items for the "Designated this image as the Dataset thumbnail:
     */

    private FileMetadata fileMetadataSelectedForThumbnailPopup = null;


    /*
     * Items for the "Tags (Categories)" popup.
     *
     */
    private FileMetadata fileMetadataSelectedForTagsPopup = null;

    public void clearFileMetadataSelectedForTagsPopup() {
        fileMetadataSelectedForTagsPopup = null;
    }

    private List<String> categoriesByName;

    public void setCategoriesByName(List<String> dummy) {
        categoriesByName = dummy;
    }

    public void refreshTagsPopUp() {
        if (bulkUpdateCheckVersion()) {
            refreshSelectedFiles();
        }
        updateFileCounts();
        refreshCategoriesByName();
        refreshTabFileTagsByName();
    }

    private List<String> tabFileTagsByName;

    private void refreshCategoriesByName() {
        categoriesByName = new ArrayList<>();
        for (String category : dataset.getCategoriesByName()) {
            categoriesByName.add(category);
        }
        refreshSelectedTags();
    }


    public List<String> getCategoriesByName() {
        return categoriesByName;
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

    public void handleSelection(final AjaxBehaviorEvent event) {
        if (selectedTags != null) {
            selectedTags = selectedTags.clone();
        }
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

    private boolean tabularDataSelected = false;

    public boolean isTabularDataSelected() {
        return tabularDataSelected;
    }

    public void setTabularDataSelected(boolean tabularDataSelected) {
        this.tabularDataSelected = tabularDataSelected;
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


    private Boolean downloadButtonAvailable = null;

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

    public boolean isFileAccessRequestMultiButtonRequired() {
        if (!isSessionUserAuthenticated()) {
            return false;
        }
        if (workingVersion == null) {
            return false;
        }
        if (!workingVersion.getTermsOfUseAndAccess().isFileAccessRequest()) {
            // return false;
        }
        for (FileMetadata fmd : workingVersion.getFileMetadatas()) {
            if (!this.fileDownloadHelper.canDownloadFile(fmd)) {
                return true;
            }
        }
        return false;
    }

    public boolean isFileAccessRequestMultiSignUpButtonRequired() {
        if (isSessionUserAuthenticated()) {
            return false;
        }
        for (FileMetadata fmd : workingVersion.getFileMetadatas()) {
            if (!this.fileDownloadHelper.canDownloadFile(fmd)) {
                return true;
            }
        }
        return false;
    }

    public boolean isDownloadPopupRequired() {
        return FileUtil.isDownloadPopupRequired(workingVersion);
    }

    public boolean isRequestAccessPopupRequired(FileMetadata fileMetadata) {
        return FileUtil.isRequestAccessPopupRequired(fileMetadata);
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

    public boolean isSortButtonEnabled() {
        /**
         * @todo The "Sort" Button seems to stop responding to mouse clicks
         * after a while so it can't be shipped in 4.2 and will be deferred, to
         * be picked up in https://github.com/IQSS/dataverse/issues/2506
         */
        return false;
    }

    public String getFileSortField() {
        return fileSortField;
    }

    public void setFileSortField(String fileSortField) {
        this.fileSortField = fileSortField;
    }

    public String getFileSortOrder() {
        return fileSortOrder;
    }

    public void setFileSortOrder(String fileSortOrder) {
        this.fileSortOrder = fileSortOrder;
    }

    public List<FileMetadata> getFileMetadatas() {
        if (isSortButtonEnabled()) {
            return fileMetadatas;
        } else {
            return new ArrayList<>();
        }
    }

    PrivateUrl privateUrl;

    public PrivateUrl getPrivateUrl() {
        return privateUrl;
    }

    public void setPrivateUrl(PrivateUrl privateUrl) {
        this.privateUrl = privateUrl;
    }

    public void initPrivateUrlPopUp() {
        if (privateUrl != null) {
            setPrivateUrlJustCreatedToFalse();
        }
    }

    boolean privateUrlWasJustCreated;

    public boolean isPrivateUrlWasJustCreated() {
        return privateUrlWasJustCreated;
    }

    public void setPrivateUrlJustCreatedToFalse() {
        privateUrlWasJustCreated = false;
    }

    public void createPrivateUrl() {
        try {
            PrivateUrl createdPrivateUrl = commandEngine.submit(new CreatePrivateUrlCommand(dvRequestService.getDataverseRequest(), dataset));
            privateUrl = createdPrivateUrl;
            JH.addMessage(FacesMessage.SEVERITY_INFO, BundleUtil.getStringFromBundle("dataset.privateurl.infoMessageAuthor", Arrays.asList(getPrivateUrlLink(privateUrl))));
            privateUrlWasJustCreated = true;
        } catch (CommandException ex) {
            String msg = BundleUtil.getStringFromBundle("dataset.privateurl.noPermToCreate", PrivateUrlUtil.getRequiredPermissions(ex));
            logger.info("Unable to create a Private URL for dataset id " + dataset.getId() + ". Message to user: " + msg + " Exception: " + ex);
            JsfHelper.addFlashErrorMessage(msg);
        }
    }

    public void disablePrivateUrl() {
        try {
            commandEngine.submit(new DeletePrivateUrlCommand(dvRequestService.getDataverseRequest(), dataset));
            privateUrl = null;
            JsfHelper.addFlashSuccessMessage(BundleUtil.getStringFromBundle("dataset.privateurl.disabledSuccess"));
        } catch (CommandException ex) {
            logger.info("CommandException caught calling DeletePrivateUrlCommand: " + ex);
        }
    }

    public boolean isUserCanCreatePrivateURL() {
        return dataset.getLatestVersion().isDraft();
    }

    public String getPrivateUrlLink(PrivateUrl privateUrl) {
        return privateUrl.getLink();
    }


    public FileDownloadHelper getFileDownloadHelper() {
        return fileDownloadHelper;
    }

    public void setFileDownloadHelper(FileDownloadHelper fileDownloadHelper) {
        this.fileDownloadHelper = fileDownloadHelper;
    }


    public FileDownloadServiceBean getFileDownloadService() {
        return fileDownloadService;
    }

    public void setFileDownloadService(FileDownloadServiceBean fileDownloadService) {
        this.fileDownloadService = fileDownloadService;
    }


    public GuestbookResponseServiceBean getGuestbookResponseService() {
        return guestbookResponseService;
    }

    public void setGuestbookResponseService(GuestbookResponseServiceBean guestbookResponseService) {
        this.guestbookResponseService = guestbookResponseService;
    }


    /**
     * dataset title
     *
     * @return title of workingVersion
     */
    public String getTitle() {
        assert (null != workingVersion);
        return workingVersion.getTitle();
    }

    /**
     * dataset description
     *
     * @return description of workingVersion
     */
    public String getDescription() {
        return workingVersion.getDescriptionPlainText();
    }

    /**
     * dataset authors
     *
     * @return list of author names
     */
    public List<String> getDatasetAuthors() {
        assert (workingVersion != null);
        return workingVersion.getDatasetAuthorNames();
    }

    /**
     * publisher (aka - name of root dataverse)
     *
     * @return the publisher of the version
     */
    public String getPublisher() {
        assert (null != workingVersion);
        return workingVersion.getRootDataverseNameforCitation();
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
            out.write(getRsyncScript().getBytes());
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

    public void closeRsyncScriptPopup(CloseEvent event) {
        finishRsyncScriptAction();
    }

    public String finishRsyncScriptAction() {
        // This method is called when the user clicks on "Close" in the "Rsync Upload" 
        // popup. If they have successfully downloaded the rsync script, the 
        // dataset should now be locked; which means we should put up the 
        // "dcm upload in progress" message - that will be shown on the page 
        // until the rsync upload is completed and the dataset is unlocked. 
        if (isLocked()) {
            JH.addMessage(FacesMessage.SEVERITY_WARN, BundleUtil.getStringFromBundle("file.rsyncUpload.inProgressMessage.summary"), BundleUtil.getStringFromBundle("file.rsyncUpload.inProgressMessage.details"));
        }
        return "";
    }

    /**
     * this method returns the dataset fields to be shown in the dataset summary
     * on the dataset page.
     * It returns the default summary fields( subject, description, keywords, related publications and notes)
     * if the custom summary datafields has not been set, otherwise will set the custom fields set by the sysadmins
     *
     * @return the dataset fields to be shown in the dataset summary
     */
    public List<DatasetField> getDatasetSummaryFields() {
        List<String> customFields = settingsService.getValueForKeyAsList(SettingsServiceBean.Key.CustomDatasetSummaryFields);

        return DatasetUtil.getDatasetSummaryFields(workingVersion, customFields);
    }

    public List<ExternalTool> getConfigureToolsForDataFile(Long fileId) {
        return getCachedToolsForDataFile(fileId, ExternalTool.Type.CONFIGURE);
    }

    public List<ExternalTool> getExploreToolsForDataFile(Long fileId) {
        return getCachedToolsForDataFile(fileId, ExternalTool.Type.EXPLORE);
    }

    public boolean isTwoRavenAmongExternalTools(List<ExternalTool> externalTools) {
        for (ExternalTool externalTool : externalTools) {
            if (externalTool.getDisplayName().equals("TwoRavens")) {
                return true;
            }
        }
        return false;
    }

    public List<ExternalTool> getCachedToolsForDataFile(Long fileId, ExternalTool.Type type) {
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

    public void selectAllFiles() {
        logger.fine("selectAllFiles called");
        selectedFiles = workingVersion.getFileMetadatas();
    }

    public void clearSelection() {
        logger.info("clearSelection called");
        selectedFiles = Collections.emptyList();
    }

    public void fileListingPaginatorListener(PageEvent event) {
        setFilePaginatorPage(event.getPage());
    }

    public void refreshPaginator() {
        FacesContext facesContext = FacesContext.getCurrentInstance();
        org.primefaces.component.datatable.DataTable dt = (org.primefaces.component.datatable.DataTable) facesContext.getViewRoot().findComponent("datasetForm:tabView:filesTable");
        setFilePaginatorPage(dt.getPage());
        setRowsPerPage(dt.getRowsToRender());
    }

    public String redirectToMetrics() {
        return "/metrics.xhtml?faces-redirect=true";
    }

    public boolean isSameTermsOfUseForAllFiles() {
        if (sameTermsOfUseForAllFiles != null) {
            return sameTermsOfUseForAllFiles;
        }
        if (workingVersion.getFileMetadatas().isEmpty()) {
            sameTermsOfUseForAllFiles = true;
            return sameTermsOfUseForAllFiles;
        }
        FileTermsOfUse firstTermsOfUse = workingVersion.getFileMetadatas().get(0).getTermsOfUse();

        for (FileMetadata fileMetadata : workingVersion.getFileMetadatas()) {
            if (!datafileService.isSameTermsOfUse(firstTermsOfUse, fileMetadata.getTermsOfUse())) {
                sameTermsOfUseForAllFiles = false;
                return sameTermsOfUseForAllFiles;
            }
        }

        sameTermsOfUseForAllFiles = true;
        return sameTermsOfUseForAllFiles;
    }

    public Optional<FileTermsOfUse> getTermsOfUseOfFirstFile() {
        if (workingVersion.getFileMetadatas().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(workingVersion.getFileMetadatas().get(0).getTermsOfUse());
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
}
