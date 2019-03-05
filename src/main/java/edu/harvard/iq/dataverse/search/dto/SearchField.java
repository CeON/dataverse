package edu.harvard.iq.dataverse.search.dto;

import edu.harvard.iq.dataverse.FieldType;

/**
 * Class that holds vital information regarding field.
 */
public class SearchField {

    private String name;
    private String displayName;
    private String description;
    private FieldType fieldType;

    public SearchField(String name, String displayName, String description, FieldType fieldType) {
        this.name = name;
        this.displayName = displayName;
        this.description = description;
        this.fieldType = fieldType;
    }

    /**
     * Returns the name that is used as id in Metadata Blocks
     *
     * @return name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns localized name that is used for displaying
     * @return display name
     */
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
