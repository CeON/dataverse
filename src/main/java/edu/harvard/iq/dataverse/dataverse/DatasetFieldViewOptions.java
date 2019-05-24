package edu.harvard.iq.dataverse.dataverse;

import javax.faces.model.SelectItem;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class DatasetFieldViewOptions implements Serializable {

    private boolean requiredField;
    private boolean included;
    private List<SelectItem> selectedDatasetFields = new ArrayList<>();

    // -------------------- CONSTRUCTORS --------------------

    public DatasetFieldViewOptions(boolean requiredField, boolean included, List<SelectItem> selectedDatasetFields) {
        this.requiredField = requiredField;
        this.included = included;
        this.selectedDatasetFields = selectedDatasetFields;
    }

    public DatasetFieldViewOptions(boolean requiredField, boolean included) {
        this.requiredField = requiredField;
        this.included = included;
    }

    // -------------------- GETTERS --------------------

    public boolean isRequiredField() {
        return requiredField;
    }

    public boolean isIncluded() {
        return included;
    }

    public List<SelectItem> getSelectedDatasetFields() {
        return selectedDatasetFields;
    }

    // -------------------- SETTERS --------------------


    public void setRequiredField(boolean requiredField) {
        this.requiredField = requiredField;
    }

    public void setIncluded(boolean included) {
        this.included = included;
    }

    public void setSelectedDatasetFields(List<SelectItem> selectedDatasetFields) {
        this.selectedDatasetFields = selectedDatasetFields;
    }
}
