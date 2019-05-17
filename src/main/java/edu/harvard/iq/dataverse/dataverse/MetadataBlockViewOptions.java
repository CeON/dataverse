package edu.harvard.iq.dataverse.dataverse;

public class MetadataBlockViewOptions {

    private boolean showDatasetFieldTypes;
    private boolean editableDatasetFieldTypes;
    private boolean selected;

    // -------------------- CONSTRUCTORS --------------------

    private MetadataBlockViewOptions(Builder builder) {
        showDatasetFieldTypes = builder.showDatasetFieldTypes;
        editableDatasetFieldTypes = builder.editableDatasetFieldTypes;
        selected = builder.selected;
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

    /**
     * Indicates if this Metadata Block is selected ( in order to mark the checkbox).
     */
    public boolean isSelected() {
        return selected;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    // -------------------- LOGIC --------------------

    public static final class Builder {
        private boolean showDatasetFieldTypes;
        private boolean editableDatasetFieldTypes;
        private boolean selected;

        private Builder() {
        }

        public Builder showDatasetFieldTypes(boolean val) {
            showDatasetFieldTypes = val;
            return this;
        }

        public Builder editableDatasetFieldTypes(boolean val) {
            editableDatasetFieldTypes = val;
            return this;
        }

        public Builder selected(boolean val) {
            selected = val;
            return this;
        }

        public MetadataBlockViewOptions build() {
            return new MetadataBlockViewOptions(this);
        }
    }

}
