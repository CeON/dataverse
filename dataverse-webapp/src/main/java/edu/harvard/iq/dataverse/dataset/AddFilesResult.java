package edu.harvard.iq.dataverse.dataset;

import edu.harvard.iq.dataverse.persistence.dataset.Dataset;

public class AddFilesResult {

    private Dataset dataset;
    
    private int notSavedFilesCount;
    
    private boolean hasProvenanceErrors;

    // -------------------- CONSTRUCTORS --------------------
    
    public AddFilesResult(Dataset dataset, int notSavedFilesCount, boolean hasProvenanceErrors) {
        this.dataset = dataset;
        this.notSavedFilesCount = notSavedFilesCount;
        this.hasProvenanceErrors = hasProvenanceErrors;
    }

    // -------------------- GETTERS --------------------
    
    public Dataset getDataset() {
        return dataset;
    }

    public int getNotSavedFilesCount() {
        return notSavedFilesCount;
    }

    public boolean isHasProvenanceErrors() {
        return hasProvenanceErrors;
    }
}
