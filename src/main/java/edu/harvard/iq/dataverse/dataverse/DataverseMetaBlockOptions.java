package edu.harvard.iq.dataverse.dataverse;

import java.util.HashMap;
import java.util.Map;

public class DataverseMetaBlockOptions {

    private Map<Long, MetadataBlockViewOptions> mdbViewOptions = new HashMap<>();
    private Map<Long, DatasetFieldViewOptions> datasetFieldViewOptions = new HashMap<>();
    private boolean inheritMetaBlocksFromParent = true;

    // -------------------- GETTERS --------------------

    public Map<Long, MetadataBlockViewOptions> getMdbViewOptions() {
        return mdbViewOptions;
    }

    /**
     * Retrives dataset fields view options that belong to metadata block.
     */
    public Map<Long, DatasetFieldViewOptions> getDatasetFieldViewOptions() {
        return datasetFieldViewOptions;
    }


    public boolean isInheritMetaBlocksFromParent() {
        return inheritMetaBlocksFromParent;
    }
    // -------------------- LOGIC --------------------

    public boolean isShowDatasetFieldTypes(Long mdbId) {
        return mdbViewOptions.entrySet().stream()
                .filter(map -> map.getKey().equals(mdbId))
                .map(map -> map.getValue().isShowDatasetFieldTypes())
                .findFirst()
                .orElse(false);
    }

    public boolean isEditableDatasetFieldTypes(Long mdbId) {
        return mdbViewOptions.entrySet().stream()
                .filter(map -> map.getKey().equals(mdbId))
                .map(map -> map.getValue().isEditableDatasetFieldTypes())
                .findFirst()
                .orElse(false);
    }

    public boolean isMetaBlockSelected(Long mdbId) {
        return mdbViewOptions.entrySet().stream()
                .filter(map -> map.getKey().equals(mdbId))
                .map(map -> map.getValue().isSelected())
                .findFirst()
                .orElse(false);
    }

    public boolean isDsftIncludedField(Long dsftId) {
        return datasetFieldViewOptions.entrySet().stream()
                .filter(map -> map.getKey().equals(dsftId))
                .map(map -> map.getValue().isIncluded())
                .findFirst()
                .orElse(false);
    }

    // -------------------- SETTERS --------------------

    public void setInheritMetaBlocksFromParent(boolean inheritMetaBlocksFromParent) {
        this.inheritMetaBlocksFromParent = inheritMetaBlocksFromParent;
    }
}
