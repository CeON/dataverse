package edu.harvard.iq.dataverse.dataverse;

import org.apache.commons.lang3.SerializationUtils;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class DataverseMetaBlockOptions implements Serializable {

    private Map<Long, MetadataBlockViewOptions> mdbViewOptions = new HashMap<>();
    private Map<Long, DatasetFieldViewOptions> datasetFieldViewOptions = new HashMap<>();
    private boolean inheritMetaBlocksFromParent = true;

    // -------------------- GETTERS --------------------

    /**
     * Retrives metadata block view options.
     */
    public Map<Long, MetadataBlockViewOptions> getMdbViewOptions() {
        return mdbViewOptions;
    }

    /**
     * Retrives dataset fields view options.
     */
    public Map<Long, DatasetFieldViewOptions> getDatasetFieldViewOptions() {
        return datasetFieldViewOptions;
    }

    /**
     * Indicates if metadata blocks are inherited from parent dataverse or if they are being edited by user.
     */
    public boolean isInheritMetaBlocksFromParent() {
        return inheritMetaBlocksFromParent;
    }
    // -------------------- LOGIC --------------------

    /**
     * Indicates if dataset field should be shown.
     *
     * @return true/false or false if null.
     */
    public boolean isShowDatasetFieldTypes(Long mdbId) {
        return mdbViewOptions.entrySet().stream()
                .filter(map -> map.getKey().equals(mdbId))
                .map(map -> map.getValue().isShowDatasetFieldTypes())
                .findFirst()
                .orElse(false);

    }

    /**
     * Indicates if dataset field should be editable.
     * @return true/false or false if null.
     */
    public boolean isEditableDatasetFieldTypes(Long mdbId) {
        return mdbViewOptions.entrySet().stream()
                .filter(map -> map.getKey().equals(mdbId))
                .map(map -> map.getValue().isEditableDatasetFieldTypes())
                .findFirst()
                .orElse(false);
    }

    /**
     * Indicates if dataset field is included (required/optional).
     * @return true/false or false if null.
     */
    public boolean isDsftIncludedField(Long dsftId) {
        return datasetFieldViewOptions.entrySet().stream()
                .filter(map -> map.getKey().equals(dsftId))
                .map(map -> map.getValue().isIncluded())
                .findFirst()
                .orElse(false);
    }

    /**
     * Indicates if metadata block is selected.
     *
     * @return true/false or false if null.
     */
    public boolean isMetaBlockSelected(Long mdbId) {
        return mdbViewOptions.entrySet().stream()
                .filter(map -> map.getKey().equals(mdbId))
                .map(map -> map.getValue().isSelected())
                .findFirst()
                .orElse(false);
    }

    /**
     * Makes a deep copy using serialization/deserialization.
     * If top performance is really required, it would be advisable to implement different deep copy mechanism.
     */
    public DataverseMetaBlockOptions deepCopy() {
        return SerializationUtils.clone(this);
    }

    // -------------------- SETTERS --------------------

    public void setInheritMetaBlocksFromParent(boolean inheritMetaBlocksFromParent) {
        this.inheritMetaBlocksFromParent = inheritMetaBlocksFromParent;
    }
}
