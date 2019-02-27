package edu.harvard.iq.dataverse.search.dto;

import edu.harvard.iq.dataverse.FieldType;

public class IntegerSearchField extends SearchMetadataField {

    private Integer minimum;
    private Integer maximum;

    public IntegerSearchField(String name, String displayName, String description, FieldType fieldType) {
        super(name, displayName, description, fieldType);
    }

    public Integer getMinimum() {
        return minimum;
    }

    public void setMinimum(Integer minimum) {
        this.minimum = minimum;
    }

    public Integer getMaximum() {
        return maximum;
    }

    public void setMaximum(Integer maximum) {
        this.maximum = maximum;
    }
}
