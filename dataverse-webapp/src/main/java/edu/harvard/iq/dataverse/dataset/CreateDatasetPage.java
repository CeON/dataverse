package edu.harvard.iq.dataverse.dataset;

import edu.harvard.iq.dataverse.DatasetPage;
import edu.harvard.iq.dataverse.DataverseDao;
import edu.harvard.iq.dataverse.DataverseSession;
import edu.harvard.iq.dataverse.PermissionsWrapper;
import edu.harvard.iq.dataverse.common.BundleUtil;
import edu.harvard.iq.dataverse.dataset.metadata.inputRenderer.InputFieldRenderer;
import edu.harvard.iq.dataverse.dataset.metadata.inputRenderer.InputFieldRendererManager;
import edu.harvard.iq.dataverse.engine.command.exception.CommandException;
import edu.harvard.iq.dataverse.engine.command.exception.NotAuthenticatedException;
import edu.harvard.iq.dataverse.importer.metadata.ImporterRegistry;
import edu.harvard.iq.dataverse.importer.metadata.MetadataImporter;
import edu.harvard.iq.dataverse.importers.ui.ImporterForm;
import edu.harvard.iq.dataverse.importers.ui.ImportersForView;
import edu.harvard.iq.dataverse.license.TermsOfUseFormMapper;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.datafile.FileMetadata;
import edu.harvard.iq.dataverse.persistence.datafile.license.FileTermsOfUse;
import edu.harvard.iq.dataverse.persistence.datafile.license.TermsOfUseForm;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetField;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetFieldType;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetFieldUtil;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetFieldsByType;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.dataset.MetadataBlock;
import edu.harvard.iq.dataverse.persistence.dataset.Template;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import edu.harvard.iq.dataverse.util.JsfHelper;
import edu.harvard.iq.dataverse.validation.DatasetFieldValidationService;
import edu.harvard.iq.dataverse.validation.field.FieldValidationResult;
import io.vavr.control.Try;
import org.apache.commons.lang3.StringUtils;
import org.omnifaces.cdi.ViewScoped;

import javax.ejb.EJBException;
import javax.faces.event.AjaxBehaviorEvent;
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

@ViewScoped
@Named("CreateDatasetPage")
public class CreateDatasetPage implements Serializable {

    private static final Logger logger = Logger.getLogger(DatasetPage.class.getCanonicalName());

    private ImporterRegistry importerRegistry;
    private DataverseDao dataverseDao;
    private PermissionsWrapper permissionsWrapper;
    private SettingsServiceBean settingsService;
    private DatasetFieldsInitializer datasetFieldsInitializer;
    private DataverseSession session;
    private TermsOfUseFormMapper termsOfUseFormMapper;
    private UserDataFieldFiller userDataFieldFiller;
    private DatasetService datasetService;
    private InputFieldRendererManager inputFieldRendererManager;
    private DatasetFieldValidationService fieldValidationService;

    private Dataset dataset;
    private Long ownerId;

    private DatasetVersion workingVersion;
    private List<DataFile> newFiles = new ArrayList<>();
    private List<FileMetadata> selectedFiles = new ArrayList<>();

    private List<Template> dataverseTemplates = new ArrayList<>();
    private Template selectedTemplate;

    private Map<MetadataBlock, List<DatasetFieldsByType>> metadataBlocksForEdit = new HashMap<>();
    private Map<DatasetFieldType, InputFieldRenderer> inputRenderersByFieldType = new HashMap<>();

    private ImportersForView importers;
    private MetadataImporter selectedImporter;
    private ImporterForm importerForm;

    // -------------------- CONSTRUCTORS --------------------

    public CreateDatasetPage() { }

    @Inject
    public CreateDatasetPage(ImporterRegistry importerRegistry, DataverseDao dataverseDao,
                             PermissionsWrapper permissionsWrapper, SettingsServiceBean settingsService,
                             DatasetFieldsInitializer datasetFieldsInitializer, DataverseSession session,
                             TermsOfUseFormMapper termsOfUseFormMapper, UserDataFieldFiller userDataFieldFiller,
                             DatasetService datasetService, InputFieldRendererManager inputFieldRendererManager,
                             DatasetFieldValidationService fieldValidationService) {
        this.importerRegistry = importerRegistry;
        this.dataverseDao = dataverseDao;
        this.permissionsWrapper = permissionsWrapper;
        this.settingsService = settingsService;
        this.datasetFieldsInitializer = datasetFieldsInitializer;
        this.session = session;
        this.termsOfUseFormMapper = termsOfUseFormMapper;
        this.userDataFieldFiller = userDataFieldFiller;
        this.datasetService = datasetService;
        this.inputFieldRendererManager = inputFieldRendererManager;
        this.fieldValidationService = fieldValidationService;
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

    public Map<MetadataBlock, List<DatasetFieldsByType>> getMetadataBlocksForEdit() {
        return metadataBlocksForEdit;
    }

    public Map<DatasetFieldType, InputFieldRenderer> getInputRenderersByFieldType() {
        return inputRenderersByFieldType;
    }

    public ImportersForView getImporters() {
        return importers;
    }

    public MetadataImporter getSelectedImporter() {
        return selectedImporter;
    }

    public ImporterForm getImporterForm() {
        return importerForm;
    }

    // -------------------- LOGIC --------------------

    public String init() {

        Dataverse ownerDataverse = dataverseDao.find(ownerId);

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

        this.importers = ImportersForView.createInitialized(dataset, importerRegistry.getImporters(), session.getLocale());

        workingVersion = dataset.getLatestVersion();
        resetDatasetFields();

        return StringUtils.EMPTY;
    }

    public void updateSelectedTemplate(AjaxBehaviorEvent event) {
        resetDatasetFields();
    }

    public String save() {
        workingVersion.setDatasetFields(DatasetFieldUtil.flattenDatasetFieldsFromBlocks(metadataBlocksForEdit));

        List<FieldValidationResult> fieldValidationResults = fieldValidationService.validateFieldsOfDatasetVersion(workingVersion);
        Set<ConstraintViolation<FileMetadata>> constraintViolations = workingVersion.validateFileMetadata();
        if (!fieldValidationResults.isEmpty() || !constraintViolations.isEmpty()) {
            JsfHelper.addErrorMessage(BundleUtil.getStringFromBundle("dataset.message.validationError"),
                    BundleUtil.getStringFromBundle("dataset.message.validationErrorDetails"));
            return StringUtils.EMPTY;
        }

        mapTermsOfUseInFiles(newFiles);

        Try<Dataset> createDatasetOperation = Try.of(() -> datasetService.createDataset(dataset, selectedTemplate))
                .onFailure(NotAuthenticatedException.class,
                    ex -> handleErrorMessage(BundleUtil.getStringFromBundle("dataset.create.authenticatedUsersOnly"), ex))
                .onFailure(EJBException.class,
                    ex -> handleErrorMessage(BundleUtil.getStringFromBundle("dataset.message.createFailure"), ex))
                .onFailure(CommandException.class,
                    ex -> handleErrorMessage(BundleUtil.getStringFromBundle("dataset.message.createFailure"), ex));

        if (createDatasetOperation.isFailure()) {
            return StringUtils.EMPTY;
        }


        Try.of(() -> datasetService.addFilesToDataset(dataset.getId(), newFiles))
            .onFailure(ex -> handleErrorMessage(BundleUtil.getStringFromBundle("dataset.message.createSuccess.failedToSaveFiles"), ex))
            .onSuccess(addFilesResult -> handleSuccessOrPartialSuccessMessages(newFiles.size(), addFilesResult))
            .onSuccess(addFilesResult -> dataset = addFilesResult.getDataset());

        return returnToDraftVersion();
    }

    public void initMetadataImportDialog() {
        importerForm = ImporterForm.createInitializedForm(selectedImporter, session.getLocale(), this::getMetadataBlocksForEdit);
    }

    public boolean isInstallationPublic() {
        return settingsService.isTrueForKey(SettingsServiceBean.Key.PublicInstall);
    }

    // -------------------- PRIVATE --------------------

    private List<Template> fetchApplicableTemplates(Dataverse dataverse) {
        List<Template> templates = new ArrayList<>(dataverse.getTemplates());
        if (!dataverse.isTemplateRoot()) {
            templates.addAll(dataverse.getParentTemplates());
        }
        Collections.sort(templates, (Template t1, Template t2) -> t1.getName().compareToIgnoreCase(t2.getName()));
        return templates;
    }

    private void resetDatasetFields() {
        List<DatasetField> datasetFields = new ArrayList<>();

        if (selectedTemplate != null) {
            datasetFields = DatasetFieldUtil.copyDatasetFields(selectedTemplate.getDatasetFields());
        }

        datasetFields = datasetFieldsInitializer.prepareDatasetFieldsForEdit(datasetFields, dataset.getOwner().getMetadataBlockRootDataverse());

        if (session.getUser().isAuthenticated()) {
            userDataFieldFiller.fillUserDataInDatasetFields(datasetFields, (AuthenticatedUser) session.getUser());
        }

        inputRenderersByFieldType = inputFieldRendererManager.obtainRenderersByType(datasetFields);

        metadataBlocksForEdit = datasetFieldsInitializer.groupAndUpdateFlagsForEdit(datasetFields, dataset.getOwner().getMetadataBlockRootDataverse());

    }

    private void mapTermsOfUseInFiles(List<DataFile> files) {
        for (DataFile file : files) {
            TermsOfUseForm termsOfUseForm = file.getFileMetadata().getTermsOfUseForm();
            FileTermsOfUse termsOfUse = termsOfUseFormMapper.mapToFileTermsOfUse(termsOfUseForm);

            file.getFileMetadata().setTermsOfUse(termsOfUse);
        }
    }

    private void handleSuccessOrPartialSuccessMessages(int filesToSaveCount, AddFilesResult addFilesResult) {
        int savedFilesCount = filesToSaveCount - addFilesResult.getNotSavedFilesCount();

        if (filesToSaveCount == savedFilesCount) {
            JsfHelper.addFlashSuccessMessage(BundleUtil.getStringFromBundle("dataset.message.createSuccess"));
        } else if (savedFilesCount == 0) {
            JsfHelper.addFlashWarningMessage(BundleUtil.getStringFromBundle("dataset.message.createSuccess.failedToSaveFiles"));
        } else {
            String partialSuccessMessage = BundleUtil.getStringFromBundle("dataset.message.createSuccess.partialSuccessSavingFiles",
                    savedFilesCount, filesToSaveCount);
            JsfHelper.addFlashWarningMessage(partialSuccessMessage);
        }

        if (addFilesResult.isHasProvenanceErrors()) {
            JsfHelper.addFlashErrorMessage(BundleUtil.getStringFromBundle("file.metadataTab.provenance.error"));
        }
    }

    private void handleErrorMessage(String messageToUser, Throwable ex) {
        logger.log(Level.SEVERE, ex.getMessage(), ex);
        JsfHelper.addFlashErrorMessage(messageToUser);
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

    public void setSelectedImporter(MetadataImporter selectedImporter) {
        this.selectedImporter = selectedImporter;
    }
}
