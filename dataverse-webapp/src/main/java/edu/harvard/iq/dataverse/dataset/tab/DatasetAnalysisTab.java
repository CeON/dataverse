package edu.harvard.iq.dataverse.dataset.tab;

import java.io.Serializable;
import java.text.SimpleDateFormat;

import javax.ejb.EJB;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;

import edu.harvard.iq.dataverse.DatasetFieldServiceBean;
import edu.harvard.iq.dataverse.DataverseRequestServiceBean;
import edu.harvard.iq.dataverse.PermissionsWrapper;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetField;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetFieldType;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;

@ViewScoped
@Named("datasetAnalysisTab")
public class DatasetAnalysisTab implements Serializable {
    
    
    private DatasetVersion datasetVersion;
    
    @EJB
    private DatasetFieldServiceBean datasetFields;
    
    private SettingsServiceBean settingsService;
    
    private PermissionsWrapper permissionsWrapper;

    private DataverseRequestServiceBean dvRequestService;

    @Inject
    public DatasetAnalysisTab(SettingsServiceBean settingsService, PermissionsWrapper permissionsWrapper,
            DataverseRequestServiceBean dvRequestService) {
        this.settingsService = settingsService;
        this.permissionsWrapper = permissionsWrapper;
        this.dvRequestService = dvRequestService;


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
        return false;
    }

    public boolean isAnalysisPerformed() {
        return true;
    }

    public boolean isAnalysisSucceeded() {
        return true;
    }

    public boolean isAnalysisFailure() {
        return false;
    }

    public boolean showAnalysisResults() {
        return (!isDatasetUnderEmbargo() || (isDatasetUnderEmbargo() && isPermissionToViewFiles())) && isAnalysisSucceeded();
    }

    public String getEmbargoDateForDisplay() {
        SimpleDateFormat format = new SimpleDateFormat(settingsService.getValueForKey(SettingsServiceBean.Key.DefaultDateFormat));
        return format.format(datasetVersion.getDataset().getEmbargoDate().getOrNull());
    }

}
