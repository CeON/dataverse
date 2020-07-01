package edu.harvard.iq.dataverse;

public class AnalysisResultDTO {

    String fieldName;
    String primaryValue;
    String secondaryValue;
    
    public String getFieldName() {
        return fieldName;
    }
    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }
    public String getPrimaryValue() {
        return primaryValue;
    }
    public void setPrimaryValue(String primaryValue) {
        this.primaryValue = primaryValue;
    }
    public String getSecondaryValue() {
        return secondaryValue;
    }
    public void setSecondaryValue(String secondaryValue) {
        this.secondaryValue = secondaryValue;
    }
}
