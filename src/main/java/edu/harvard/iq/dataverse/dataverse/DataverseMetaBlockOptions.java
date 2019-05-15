package edu.harvard.iq.dataverse.dataverse;

import java.util.HashMap;
import java.util.Map;

public class DataverseMetaBlockOptions {

    private Map<Long, MetadataBlockViewOptions> mdbViewOptions = new HashMap<>();
    private boolean inheritMetaBlocksFromParent;

    // -------------------- GETTERS --------------------

    public Map<Long, MetadataBlockViewOptions> getMdbViewOptions() {
        return mdbViewOptions;
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

    // -------------------- SETTERS --------------------

    public void setInheritMetaBlocksFromParent(boolean inheritMetaBlocksFromParent) {
        this.inheritMetaBlocksFromParent = inheritMetaBlocksFromParent;
    }
}
