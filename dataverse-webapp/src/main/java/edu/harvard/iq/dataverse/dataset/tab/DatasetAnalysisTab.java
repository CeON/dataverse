package edu.harvard.iq.dataverse.dataset.tab;

import java.io.IOException;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.logging.Logger;

import javax.ejb.EJB;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;

import org.omnifaces.util.Faces;

import edu.harvard.iq.dataverse.DatasetFieldServiceBean;
import edu.harvard.iq.dataverse.DataverseRequestServiceBean;
import edu.harvard.iq.dataverse.PermissionsWrapper;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetField;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetFieldType;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.workflow.WorkflowArtifact;
import edu.harvard.iq.dataverse.persistence.workflow.WorkflowExecution;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import edu.harvard.iq.dataverse.workflow.artifacts.WorkflowArtifactServiceBean;
import edu.harvard.iq.dataverse.workflow.execution.WorkflowContext.TriggerType;
import edu.harvard.iq.dataverse.workflow.execution.WorkflowExecutionServiceBean;

@ViewScoped
@Named("datasetAnalysisTab")
public class DatasetAnalysisTab implements Serializable {
    
    private static final Logger logger = Logger.getLogger(DatasetAnalysisTab.class.getCanonicalName());

    private DatasetVersion datasetVersion;
    
    @EJB
    private DatasetFieldServiceBean datasetFields;
    
    private SettingsServiceBean settingsService;
    
    private PermissionsWrapper permissionsWrapper;

    private DataverseRequestServiceBean dvRequestService;
    
    private WorkflowExecutionServiceBean workflowServiceBean;
    
    private WorkflowExecution workflowExecution = null;
    
    private WorkflowArtifactServiceBean workflowArfifactService;

    @Inject
    public DatasetAnalysisTab(SettingsServiceBean settingsService, PermissionsWrapper permissionsWrapper,
            DataverseRequestServiceBean dvRequestService, WorkflowExecutionServiceBean workflowServiceBean,
            WorkflowArtifactServiceBean workflowArtifactService) {
        this.settingsService = settingsService;
        this.permissionsWrapper = permissionsWrapper;
        this.dvRequestService = dvRequestService;
        this.workflowServiceBean = workflowServiceBean;
        this.workflowArfifactService = workflowArtifactService;

    }
    
    // -------------------- GETTERS --------------------

    
    public String getDatasetFieldValue(String name, String source) {
        
        for (DatasetField field:datasetVersion.getDatasetFields()) {
            if (field.getDatasetFieldsChildren().isEmpty()) {
                if (field.getDatasetFieldType().getName().equals(name) && field.getSource().equals(source)) {
                    return field.getValue();
                }
            } else {
                for (DatasetField child:field.getDatasetFieldsChildren()) {
                    if (child.getDatasetFieldType().getName().equals(name) && child.getSource().equals(source)) {
                        return child.getValue();
                    }
                }
            }
        }
        return "";
    }
    
    public String getDatasetFieldName(String name) {
        
        DatasetFieldType fieldType = datasetFields.findByName(name);
        if (fieldType != null) {
            return fieldType.getDisplayName();
        } else {
            return "";
        }
    }
    
    // -------------------- LOGIC --------------------

    
    public void init(DatasetVersion datasetVersion) {
        this.datasetVersion = datasetVersion;
        if (!datasetVersion.isDraft()) {
            workflowExecution = workflowServiceBean.findLatestByTriggerTypeAndDatasetVersion(TriggerType.PostPublishDataset, datasetVersion.getDataset().getId(), datasetVersion.getVersionNumber(), datasetVersion.getMinorVersionNumber()).get();
        }
    }
    
    public boolean isDatasetUnderEmbargo() {
        return datasetVersion.getDataset().hasActiveEmbargo();
    }

    public boolean isDatasetInDraft() {
        return datasetVersion.isDraft();
    }

    public boolean isPermissionToViewFiles() {
        return permissionsWrapper.canViewUnpublishedDataset(dvRequestService.getDataverseRequest(), datasetVersion.getDataset());
    }

    public boolean isAnalysisInProgress() {
        return workflowExecution != null && !workflowExecution.isFinished();
    }

    public boolean isAnalysisNotPerformed() {
        return workflowExecution == null;
    }

    public boolean isAnalysisSucceeded() {
        return workflowExecution != null && workflowExecution.isFinished() && workflowExecution.getLastStep().getFinishedSuccessfully();
    }

    public boolean isAnalysisFailure() {
        return workflowExecution != null && workflowExecution.isFinished() && !workflowExecution.getLastStep().getFinishedSuccessfully();
    }

    public boolean showAnalysisResults() {
        return !isDatasetInDraft() && (!isDatasetUnderEmbargo() || (isDatasetUnderEmbargo() && isPermissionToViewFiles())) && isAnalysisSucceeded();
    }

    public String getEmbargoDateForDisplay() {
        SimpleDateFormat format = new SimpleDateFormat(settingsService.getValueForKey(SettingsServiceBean.Key.DefaultDateFormat));
        return format.format(datasetVersion.getDataset().getEmbargoDate().getOrNull());
    }
    
    public List<WorkflowArtifact> getArtifacts() {
        return workflowArfifactService.findAll(workflowExecution.getId());
    }

    public void downloadArtifact(String name, String location) {
        try {
            Faces.sendFile(workflowArfifactService.readAsStream(location).get().getInput(), name, true);
        } catch (IOException e) {
            String error = "Problem getting stream from " + location + ": " + e;
            logger.warning(error);
        }
    }
}
