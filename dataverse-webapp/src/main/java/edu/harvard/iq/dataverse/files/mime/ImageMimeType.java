package edu.harvard.iq.dataverse.files.mime;

public enum ImageMimeType {

    FITSIMAGE("image/fits");

    private String mimeType;

    ImageMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getMimeType() {
        return mimeType;
    }
}
