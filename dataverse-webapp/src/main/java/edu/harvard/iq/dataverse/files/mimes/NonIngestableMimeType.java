package edu.harvard.iq.dataverse.files.mimes;

public enum NonIngestableMimeType {

    FIXED_FIELD("text/x-fixed-field"),
    SAS_TRANSPORT("application/x-sas-transport"),
    SAS_SYSTEM("application/x-sas-system");

    private String mimeType;

    NonIngestableMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getMimeType() {
        return mimeType;
    }
}
