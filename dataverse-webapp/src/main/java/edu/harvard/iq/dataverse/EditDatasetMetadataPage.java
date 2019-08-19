package edu.harvard.iq.dataverse;

import static edu.harvard.iq.dataverse.util.JsfHelper.JH;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.faces.application.FacesMessage;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import javax.validation.ConstraintViolation;

import edu.harvard.iq.dataverse.persistence.dataset.DatasetLock;
import edu.harvard.iq.dataverse.persistence.user.Permission;
import org.apache.commons.lang.StringUtils;
import edu.harvard.iq.dataverse.common.BundleUtil;
import edu.harvard.iq.dataverse.engine.command.exception.CommandException;
import edu.harvard.iq.dataverse.engine.command.impl.UpdateDatasetVersionCommand;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetField;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.dataset.MetadataBlock;
import edu.harvard.iq.dataverse.util.JsfHelper;

@ViewScoped
@Named("editDatasetMetadataPage")
public class EditDatasetMetadataPage implements Serializable {

    private static final Logger logger = Logger.getLogger(EditDatasetMetadataPage.class.getCanonicalName());

    @EJB
    private DatasetServiceBean datasetService;
    @EJB
    private DatasetVersionServiceBean datasetVersionService;
    @Inject
    private PermissionsWrapper permissionsWrapper;
    @Inject
    private DataverseRequestServiceBean dvRequestService;
    @EJB
    private DataverseFieldTypeInputLevelServiceBean dataverseFieldTypeInputLevelService;
    @EJB
    private EjbDataverseEngine commandEngine;
    @Inject
    private DatasetVersionUI datasetVersionUI;
    @Inject
    private PermissionServiceBean permissionService;

    private Long datasetId;
    private String persistentId;

    private Dataset dataset;
    private DatasetVersion workingVersion;
    private DatasetVersion clone;
    private Map<MetadataBlock, List<DatasetField>> metadataBlocksForEdit;

    // -------------------- GETTERS --------------------

    public Dataset getDataset() {
        return dataset;
    }

    public Long getDatasetId() {
        return datasetId;
    }

    public String getPersistentId() {
        return persistentId;
    }

    public DatasetVersion getWorkingVersion() {
        return workingVersion;
    }

    public Map<MetadataBlock, List<DatasetField>> getMetadataBlocksForEdit() {
        return metadataBlocksForEdit;
    }

    // -------------------- LOGIC --------------------

    public String init() {

        if (persistentId != null) {
            dataset = datasetService.findByGlobalId(persistentId);
        } else if (datasetId != null) {
            dataset = datasetService.find(datasetId);
        }

        if (dataset == null) {
            return permissionsWrapper.notFound();
        }


        // Check permisisons
        if (!permissionsWrapper.canUpdateDataset(dvRequestService.getDataverseRequest(), dataset)) {
            return permissionsWrapper.notAuthorized();
        }
        // Locked dataset can be edited if it's InReview by a user with edit and publish permissions
        if (!permissionsWrapper.canEditDatasetInReview(dataset)) {
            return permissionsWrapper.notAuthorized();
        }

        workingVersion = dataset.getEditVersion();
        clone = workingVersion.cloneDatasetVersion();

        datasetVersionUI = datasetVersionUI.initDatasetVersionUI(workingVersion, true);
        metadataBlocksForEdit = datasetVersionUI.getMetadataBlocksForEdit();

        updateDatasetFieldsIncludeFlag();

        JH.addMessage(FacesMessage.SEVERITY_INFO,
                BundleUtil.getStringFromBundle("dataset.message.editMetadata.label"),
                BundleUtil.getStringFromBundle("dataset.message.editMetadata.message"));


        return StringUtils.EMPTY;
    }

    public String save() {
        // Validate
        Set<ConstraintViolation> constraintViolations = workingVersion.validate();
        if (!constraintViolations.isEmpty()) {
            //JsfHelper.addFlashMessage(BundleUtil.getStringFromBundle("dataset.message.validationError"));
            JH.addMessage(FacesMessage.SEVERITY_ERROR, BundleUtil.getStringFromBundle("dataset.message.validationError"));
            return StringUtils.EMPTY;
        }

        try {
            UpdateDatasetVersionCommand cmd = new UpdateDatasetVersionCommand(dataset, dvRequestService.getDataverseRequest(), new ArrayList<>(), clone);
            cmd.setValidateLenient(true);
            dataset = commandEngine.submit(cmd);
            logger.fine("Successfully executed UpdateDatasetVersionCommand.");

        } catch (EJBException ex) {
            logger.log(Level.SEVERE, "Couldn't edit dataset metadata: " + ex.getMessage(), ex);
            JsfHelper.addFlashErrorMessage(BundleUtil.getStringFromBundle("dataset.message.metadataFailure"));
            return StringUtils.EMPTY;
        } catch (CommandException ex) {
            logger.log(Level.SEVERE, "CommandException, when attempting to update the dataset: " + ex.getMessage(), ex);
            JsfHelper.addFlashErrorMessage(BundleUtil.getStringFromBundle("dataset.message.metadataFailure"));
            return StringUtils.EMPTY;
        }

        return returnToLatestVersion();
    }

    public String cancel() {
        return returnToLatestVersion();
    }

    // -------------------- PRIVATE --------------------

    private void updateDatasetFieldsIncludeFlag() {
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

    private String returnToLatestVersion() {
        dataset = datasetService.find(dataset.getId());
        workingVersion = dataset.getLatestVersion();
        if (workingVersion.isDeaccessioned() && dataset.getReleasedVersion() != null) {
            workingVersion = dataset.getReleasedVersion();
        }
        return "/dataset.xhtml?persistentId=" + dataset.getGlobalIdString() + "&version=" + workingVersion.getFriendlyVersionNumber() + "&faces-redirect=true";
    }

    // -------------------- SETTERS --------------------

    public void setDatasetId(Long datasetId) {
        this.datasetId = datasetId;
    }

    public void setPersistentId(String persistentId) {
        this.persistentId = persistentId;
    }

}
