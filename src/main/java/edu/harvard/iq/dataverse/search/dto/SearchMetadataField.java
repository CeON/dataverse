package edu.harvard.iq.dataverse.search.dto;

import edu.harvard.iq.dataverse.FieldType;

public class SearchMetadataField {

    public SearchMetadataField(String name, String displayName, String description, FieldType fieldType) {
        this.name = name;
        this.displayName = displayName;
        this.description = description;
        this.fieldType = fieldType;
    }

    private String name;
    private String displayName;
    private String description;
    private FieldType fieldType;

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public FieldType getFieldType() {
        return fieldType;
    }
}
