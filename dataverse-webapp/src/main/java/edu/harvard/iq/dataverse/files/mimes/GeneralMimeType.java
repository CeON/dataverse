package edu.harvard.iq.dataverse.files.mimes;

public enum GeneralMimeType {

    TSV("text/tsv"),
    TSV_ALT("text/tab-separated-values"),
    PLAIN_TEXT("text/plain"),
    NETWORK_GRAPHML("text/xml-graphml"),
    FITS("application/fits"),
    SPSS_SAV("application/x-spss-sav"),
    SPSS_POR("application/x-spss-por"),
    DOCUMENT_PDF("application/pdf"),
    DOCUMENT_MSWORD("application/msword"),
    DOCUMENT_MSEXCEL("application/vnd.ms-excel"),
    DOCUMENT_MSWORD_OPENXML("application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private String mimeType;

    GeneralMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getMimeType() {
        return mimeType;
    }
}
