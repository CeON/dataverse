package edu.harvard.iq.dataverse.dataset.tab;

import java.io.Serializable;

import javax.ejb.EJB;
import javax.faces.view.ViewScoped;
import javax.inject.Named;

import edu.harvard.iq.dataverse.DatasetFieldServiceBean;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetField;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetFieldType;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;

@ViewScoped
@Named("datasetAnalysisTab")
public class DatasetAnalysisTab implements Serializable {
    
    
    private DatasetVersion datasetVersion;
    
    @EJB
    private DatasetFieldServiceBean datasetFields;

    
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

}
