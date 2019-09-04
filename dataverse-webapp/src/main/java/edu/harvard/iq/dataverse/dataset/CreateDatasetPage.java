package edu.harvard.iq.dataverse.dataset;

import edu.harvard.iq.dataverse.DatasetPage;
import edu.harvard.iq.dataverse.DatasetVersionUI;
import edu.harvard.iq.dataverse.DataverseFieldTypeInputLevelServiceBean;
import edu.harvard.iq.dataverse.DataverseServiceBean;
import edu.harvard.iq.dataverse.DataverseSession;
import edu.harvard.iq.dataverse.PermissionsWrapper;
import edu.harvard.iq.dataverse.DatasetVersionUI.MetadataBlocksMode;
import edu.harvard.iq.dataverse.common.BundleUtil;
import edu.harvard.iq.dataverse.engine.command.exception.CommandException;
import edu.harvard.iq.dataverse.engine.command.exception.NotAuthenticatedException;
import edu.harvard.iq.dataverse.license.TermsOfUseFormMapper;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.datafile.FileMetadata;
import edu.harvard.iq.dataverse.persistence.datafile.license.FileTermsOfUse;
import edu.harvard.iq.dataverse.persistence.datafile.license.TermsOfUseForm;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetField;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.dataset.MetadataBlock;
import edu.harvard.iq.dataverse.persistence.dataset.Template;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import edu.harvard.iq.dataverse.util.JsfHelper;
import org.apache.commons.lang3.StringUtils;

import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.faces.application.FacesMessage;
import javax.faces.event.AjaxBehaviorEvent;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.validation.ConstraintViolation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static edu.harvard.iq.dataverse.util.JsfHelper.JH;

@ViewScoped
@Named("CreateDatasetPage")
public class CreateDatasetPage implements Serializable {

    private static final Logger logger = Logger.getLogger(DatasetPage.class.getCanonicalName());
    
    @EJB
    private DataverseServiceBean dataverseService;
    @Inject
    private PermissionsWrapper permissionsWrapper;
    @EJB
    private SettingsServiceBean settingsService;
    @EJB
    private DataverseFieldTypeInputLevelServiceBean dataverseFieldTypeInputLevelService;
    @Inject
    private DatasetVersionUI datasetVersionUI;
    @Inject
    private DataverseSession session;
    @EJB
    private TermsOfUseFormMapper termsOfUseFormMapper;
    @EJB
    private UserDataFieldFiller userDataFieldFiller;
    @EJB
    private DatasetSaver datasetSaver;

    private Dataset dataset;
    private Long ownerId;
    
    private DatasetVersion workingVersion;
    private List<DataFile> newFiles = new ArrayList<>();
    private List<FileMetadata> selectedFiles = new ArrayList<>();
    
    private List<Template> dataverseTemplates = new ArrayList<>();
    private Template selectedTemplate;
    
    private Map<MetadataBlock, List<DatasetField>> metadataBlocksForEdit = new HashMap<>();
    
    
    public String init() {
        
        Dataverse ownerDataverse = dataverseService.find(ownerId);

        if (ownerDataverse == null) {
            return permissionsWrapper.notFound();
        }
        if (!permissionsWrapper.canIssueCreateDatasetCommand(ownerDataverse)) {
            return permissionsWrapper.notAuthorized();
        }
        
        dataverseTemplates = fetchApplicableTemplates(ownerDataverse);
        selectedTemplate = ownerDataverse.getDefaultTemplate();
        
        dataset = new Dataset();
        dataset.setOwner(ownerDataverse);
        
        workingVersion = dataset.getLatestVersion();
        resetDatasetFields();

        if (settingsService.isTrueForKey(SettingsServiceBean.Key.PublicInstall)) {
            JH.addMessage(FacesMessage.SEVERITY_WARN, BundleUtil.getStringFromBundle("dataset.message.publicInstall"));
        }
        
        return StringUtils.EMPTY;
    }
    
    // -------------------- GETTERS --------------------

    public Dataset getDataset() {
        return dataset;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public DatasetVersion getWorkingVersion() {
        return workingVersion;
    }

    public List<DataFile> getNewFiles() {
        return newFiles;
    }

    public List<FileMetadata> getSelectedFiles() {
        return selectedFiles;
    }

    public List<Template> getDataverseTemplates() {
        return dataverseTemplates;
    }

    public Template getSelectedTemplate() {
        return selectedTemplate;
    }

    public Map<MetadataBlock, List<DatasetField>> getMetadataBlocksForEdit() {
        return metadataBlocksForEdit;
    }
    
    
    // -------------------- LOGIC --------------------
    
    public void updateSelectedTemplate(AjaxBehaviorEvent event) {
        resetDatasetFields();
    }
    
    
    public String save() {
        // Validate
        Set<ConstraintViolation> constraintViolations = workingVersion.validate();
        if (!constraintViolations.isEmpty()) {
            JH.addMessage(FacesMessage.SEVERITY_ERROR, BundleUtil.getStringFromBundle("dataset.message.validationError"));
            return StringUtils.EMPTY;
        }
        
        mapTermsOfUseInNewFiles();
        
        
        try {
            datasetSaver.createDataset(dataset, selectedTemplate);
            
        } catch (NotAuthenticatedException ex) {
            logger.log(Level.SEVERE, "Attempt to create dataset by not authenticated user: " + ex.getMessage(), ex);
            JH.addMessage(FacesMessage.SEVERITY_FATAL, BundleUtil.getStringFromBundle("dataset.create.authenticatedUsersOnly"));
            return StringUtils.EMPTY;
        } catch (EJBException | CommandException ex) {
            logger.log(Level.SEVERE, "Exception when attempting to create the dataset: " + ex.getMessage(), ex);
            JsfHelper.addFlashErrorMessage(BundleUtil.getStringFromBundle("dataset.message.createFailure"));
            return StringUtils.EMPTY;
        }
        
        
        AddFilesResult addFilesResult;
        try {
            addFilesResult = datasetSaver.addFilesToDataset(dataset.getId(), newFiles);
            dataset = addFilesResult.getDataset();
        } catch (Exception e) {
            JsfHelper.addFlashWarningMessage(BundleUtil.getStringFromBundle("dataset.message.createSuccess.failedToSaveFiles"));
            return returnToDraftVersion();
        }
        
        int filesToSave = newFiles.size();
        int savedFiles = filesToSave - addFilesResult.getNotSavedFilesCount();
        
        
        if (filesToSave == savedFiles) {
            JsfHelper.addFlashSuccessMessage(BundleUtil.getStringFromBundle("dataset.message.createSuccess"));
        } else if (savedFiles == 0) {
            JsfHelper.addFlashWarningMessage(BundleUtil.getStringFromBundle("dataset.message.createSuccess.failedToSaveFiles"));
        } else {
            String partialSuccessMessage = BundleUtil.getStringFromBundle("dataset.message.createSuccess.partialSuccessSavingFiles",
                    savedFiles, filesToSave);
            JsfHelper.addFlashWarningMessage(partialSuccessMessage);
        }

        if (addFilesResult.isHasProvenanceErrors()) {
            JsfHelper.addFlashErrorMessage(BundleUtil.getStringFromBundle("file.metadataTab.provenance.error"));
        }
        
        
        return returnToDraftVersion();
    }

    // -------------------- PRIVATE --------------------
    
    private List<Template> fetchApplicableTemplates(Dataverse dataverse) {
        List<Template> templates = new ArrayList<>();
        templates.addAll(dataverse.getTemplates());
        if (!dataverse.isTemplateRoot()) {
            templates.addAll(dataverse.getParentTemplates());
        }
        Collections.sort(templates, (Template t1, Template t2) -> t1.getName().compareToIgnoreCase(t2.getName()));
        return templates;
    }
    
    private void resetDatasetFields() {
        workingVersion.initDefaultValues();
        
        if (selectedTemplate != null) {
            //then create new working version from the selected template
            workingVersion.updateDefaultValuesFromTemplate(selectedTemplate);
        }
        
        datasetVersionUI = datasetVersionUI.initDatasetVersionUI(workingVersion, MetadataBlocksMode.FOR_EDIT);
        metadataBlocksForEdit = datasetVersionUI.getMetadataBlocks();
        
        if (session.getUser().isAuthenticated()) {
            userDataFieldFiller.fillUserDataInDatasetFields(workingVersion.getDatasetFields(), (AuthenticatedUser) session.getUser());
        }
    }

    private void mapTermsOfUseInNewFiles() {
        for (DataFile newFile : newFiles) {
            TermsOfUseForm termsOfUseForm = newFile.getFileMetadata().getTermsOfUseForm();
            FileTermsOfUse termsOfUse = termsOfUseFormMapper.mapToFileTermsOfUse(termsOfUseForm);

            newFile.getFileMetadata().setTermsOfUse(termsOfUse);
        }
    }

    private String returnToDraftVersion() {
        return "/dataset.xhtml?persistentId=" + dataset.getGlobalIdString() + "&version=DRAFT" + "&faces-redirect=true";
    }

    // -------------------- SETTERS --------------------
    
    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public void setSelectedTemplate(Template selectedTemplate) {
        this.selectedTemplate = selectedTemplate;
    }
    
}
