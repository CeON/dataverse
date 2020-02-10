package edu.harvard.iq.dataverse.persistence.dataset;

import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.List;

/**
 * Model class grouping dataset fields with the same type
 * 
 * @author madryk
 */
public class DatasetFieldsByType {

    private DatasetFieldType datasetFieldType;
    
    private List<DatasetField> datasetFields = new ArrayList<>();

    // -------------------- CONSTRUCTORS --------------------
    
    public DatasetFieldsByType(DatasetFieldType datasetFieldType, List<DatasetField> datasetFields) {
        datasetFields.forEach(field -> Preconditions.checkArgument(field.getDatasetFieldType().equals(datasetFieldType)));
        
        this.datasetFieldType = datasetFieldType;
        this.datasetFields = datasetFields;
    }
    
    // -------------------- GETTERS --------------------
    
    public DatasetFieldType getDatasetFieldType() {
        return datasetFieldType;
    }

    public List<DatasetField> getDatasetFields() {
        return datasetFields;
    }
}
