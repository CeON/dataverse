package edu.harvard.iq.dataverse.search.dto;

import edu.harvard.iq.dataverse.FieldType;
import io.vavr.Tuple2;

import java.util.ArrayList;
import java.util.List;

/**
 * Class that holds fields for checkbox display and values that were checked.
 */
public class CheckboxSearchField extends SearchMetadataField {

    private List<String> checkedFieldValues;
    private List<Tuple2<String, String>> checkboxLabelAndValue;

    public CheckboxSearchField(String name, String displayName, String description, FieldType fieldType) {
        super(name, displayName, description, fieldType);
        checkboxLabelAndValue = new ArrayList<>();
    }

    // -------------------- GETTERS --------------------

    public List<String> getCheckedFieldValues() {
        return checkedFieldValues;
    }

    /**
     * List of fields that are in checkbox list with localized label and value.
     *
     * @return checkboxLabelAndValue
     */
    public List<Tuple2<String, String>> getCheckboxLabelAndValue() {
        return checkboxLabelAndValue;
    }

    // -------------------- SETTERS --------------------

    public void setCheckedFieldValues(List<String> checkedFieldValues) {
        this.checkedFieldValues = checkedFieldValues;
    }

    public void setCheckboxLabelAndValue(List<Tuple2<String, String>> checkboxLabelAndValue) {
        this.checkboxLabelAndValue = checkboxLabelAndValue;
    }
}
