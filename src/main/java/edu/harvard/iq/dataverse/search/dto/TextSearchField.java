package edu.harvard.iq.dataverse.search.dto;

import edu.harvard.iq.dataverse.FieldType;

public class TextSearchField extends SearchMetadataField {

    private String fieldValue;

    public TextSearchField(String name, String displayName, String description, FieldType fieldType) {
        super(name, displayName, description, fieldType);
    }

    public String getFieldValue() {
        return fieldValue;
    }

    public void setFieldValue(String fieldValue) {
        this.fieldValue = fieldValue;
    }
}
