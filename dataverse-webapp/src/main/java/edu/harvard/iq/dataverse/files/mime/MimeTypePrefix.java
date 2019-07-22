package edu.harvard.iq.dataverse.files.mime;

public enum MimeTypePrefix {

    AUDIO("audio"),
    CODE("code"),
    DOCUMENT("document"),
    ASTRO("astro"),
    IMAGE("image"),
    NETWORK("network"),
    GEO("geodata"),
    TABULAR("tabular"),
    VIDEO("video"),
    PACKAGE("package"),
    OTHER("other");

    private String prefix;

    MimeTypePrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getPrefix() {
        return prefix;
    }
}
