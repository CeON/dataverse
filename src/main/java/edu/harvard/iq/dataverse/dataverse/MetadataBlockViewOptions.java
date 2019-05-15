package edu.harvard.iq.dataverse.dataverse;

public class MetadataBlockViewOptions {

    private boolean showDatasetFieldTypes;
    private boolean editableDatasetFieldTypes;

    // -------------------- CONSTRUCTORS --------------------

    public MetadataBlockViewOptions(boolean showDatasetFieldTypes, boolean editableDatasetFieldTypes) {
        this.showDatasetFieldTypes = showDatasetFieldTypes;
        this.editableDatasetFieldTypes = editableDatasetFieldTypes;
    }

    public MetadataBlockViewOptions(boolean showDatasetFieldTypes) {
        this.showDatasetFieldTypes = showDatasetFieldTypes;
    }

    // -------------------- GETTERS --------------------

    /**
     * Indicates if the metadata fields should be visible (used when creating/editing dataverse).
     */
    public boolean isShowDatasetFieldTypes() {
        return showDatasetFieldTypes;
    }

    /**
     * Indicates if you are able to edit Metadata Blocks fields to be (Optional/Required/Hidden).
     */
    public boolean isEditableDatasetFieldTypes() {
        return editableDatasetFieldTypes;
    }
}
