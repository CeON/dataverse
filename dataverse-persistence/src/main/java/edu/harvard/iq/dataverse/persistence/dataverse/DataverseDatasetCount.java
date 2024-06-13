package edu.harvard.iq.dataverse.persistence.dataverse;

/**
 * Data class holding a given dataverse with the calculated number of datasets in it.
 */
public class DataverseDatasetCount {
    private final Dataverse dataverse;
    private final Long datasetCount;

    // -------------------- CONSTRUCTORS --------------------

    public DataverseDatasetCount(Dataverse dataverse, Long datasetCount) {
        this.dataverse = dataverse;
        this.datasetCount = datasetCount;
    }

    // -------------------- GETTERS --------------------

    public Long getDatasetCount() {
        return datasetCount;
    }

    public Dataverse getDataverse() {
        return dataverse;
    }
}
