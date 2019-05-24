package edu.harvard.iq.dataverse.dataverse;

import org.apache.commons.lang3.SerializationUtils;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class DataverseMetaBlockOptions implements Serializable {

    private Map<Long, MetadataBlockViewOptions> mdbViewOptions = new HashMap<>();
    private Map<Long, DatasetFieldViewOptions> datasetFieldViewOptions = new HashMap<>();
    private boolean inheritMetaBlocksFromParent = true;

    // -------------------- CONSTRUCTORS --------------------

    public DataverseMetaBlockOptions() {
    }

    public DataverseMetaBlockOptions(Map<Long, MetadataBlockViewOptions> mdbViewOptions,
                                     Map<Long, DatasetFieldViewOptions> datasetFieldViewOptions,
                                     boolean inheritMetaBlocksFromParent) {
        this.mdbViewOptions = mdbViewOptions;
        this.datasetFieldViewOptions = datasetFieldViewOptions;
        this.inheritMetaBlocksFromParent = inheritMetaBlocksFromParent;
    }

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

    public DataverseMetaBlockOptions deepCopy() {
        return SerializationUtils.clone(this);
    }

    // -------------------- SETTERS --------------------

    public void setInheritMetaBlocksFromParent(boolean inheritMetaBlocksFromParent) {
        this.inheritMetaBlocksFromParent = inheritMetaBlocksFromParent;
    }
}
