package edu.harvard.iq.dataverse.dataaccess;

public enum StorageIdentifier {

    FILESYSTEM_STORAGE("file://"),
    SWIFT_STORAGE("swift://"),
    S3_STORAGE("s3://"),
    TMP_STORAGE("tmp://");

    private final String storageScheme;

    StorageIdentifier(String storageScheme) {
        this.storageScheme = storageScheme;
    }

    public String getStorageScheme() {
        return storageScheme;
    }
}
