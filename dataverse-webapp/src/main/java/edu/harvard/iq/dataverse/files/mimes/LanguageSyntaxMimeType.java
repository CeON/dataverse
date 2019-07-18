package edu.harvard.iq.dataverse.files.mimes;

public enum LanguageSyntaxMimeType {
    R_SYNTAX("application/x-r-syntax"),
    STATA_SYNTAX("text/x-stata-syntax"),
    SPSS_CCARD("text/x-spss-syntax"),
    SAS_SYNTAX("text/x-sas-syntax");

    private String mimeType;

    LanguageSyntaxMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getMimeType() {
        return mimeType;
    }
}
