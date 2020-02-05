package edu.harvard.iq.dataverse.dataset.difference;

import edu.harvard.iq.dataverse.persistence.dataset.DatasetField;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetFieldType;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;

import java.util.List;

/**
 * Class that contains old and new value of {@link DatasetField}
 * that is different between two {@link DatasetVersion}s
 * 
 * @author madryk
 */
public class DatasetFieldDiff extends MultipleItemDiff<DatasetField> {

    private DatasetFieldType fieldType;

    // -------------------- CONSTRUCTORS --------------------
    
    public DatasetFieldDiff(List<DatasetField> oldValue, List<DatasetField> newValue, DatasetFieldType fieldType) {
        super(oldValue, newValue);
        this.fieldType = fieldType;
    }

    // -------------------- GETTERS --------------------

    public DatasetFieldType getFieldType() {
        return fieldType;
    }
}