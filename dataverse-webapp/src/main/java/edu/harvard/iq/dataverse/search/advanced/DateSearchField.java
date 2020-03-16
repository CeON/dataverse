package edu.harvard.iq.dataverse.search.advanced;

/**
 * Class responsible for holding field value represented as Date String.
 */
public class DateSearchField extends SearchField {

    private String fieldValue;

    public DateSearchField(String name, String displayName, String description) {
        super(name, displayName, description, SearchFieldType.DATE);
    }

    public String getFieldValue() {
        return fieldValue;
    }

    public void setFieldValue(String fieldValue) {
        this.fieldValue = fieldValue;
    }
}
