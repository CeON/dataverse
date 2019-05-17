package edu.harvard.iq.dataverse.dataverse;

public class DatasetFieldViewOptions {

    private boolean requiredField;
    private boolean included;

    // -------------------- CONSTRUCTORS --------------------

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
}
