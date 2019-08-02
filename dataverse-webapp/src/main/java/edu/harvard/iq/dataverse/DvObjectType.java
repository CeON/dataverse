package edu.harvard.iq.dataverse;

public enum DvObjectType {

    DATAVERSE("Dataverse"),
    DATASET("Dataset"),
    DATASET_VERSION("DatasetVersion"),
    DATAFILE("DataFile");

    private String dvObject;

    DvObjectType(String objectType) {
        this.dvObject = objectType;
    }

    public String getDvObject() {
        return dvObject;
    }
}
