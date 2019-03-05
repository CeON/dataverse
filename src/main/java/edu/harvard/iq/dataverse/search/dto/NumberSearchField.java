package edu.harvard.iq.dataverse.search.dto;

import edu.harvard.iq.dataverse.FieldType;

import java.math.BigDecimal;

/**
 * Object responsible for holding numbers such as Integer or Double.
 */
public class NumberSearchField extends SearchField {

    private BigDecimal minimum;
    private BigDecimal maximum;

    public NumberSearchField(String name, String displayName, String description, FieldType fieldType) {
        super(name, displayName, description, fieldType);
    }

    public BigDecimal getMinimum() {
        return minimum;
    }

    public BigDecimal getMaximum() {
        return maximum;
    }

    public void setMinimum(BigDecimal minimum) {
        this.minimum = minimum;
    }

    public void setMaximum(BigDecimal maximum) {
        this.maximum = maximum;
    }
}
