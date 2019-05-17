package edu.harvard.iq.dataverse.dataverse;

import java.util.List;

public class MetadataBlockViewOptions {

    private boolean showDatasetFieldTypes;
    private boolean editableDatasetFieldTypes;
    private boolean selected;
    private List<DatasetFieldViewOptions> datasetFieldViewOptions;

    // -------------------- CONSTRUCTORS --------------------

    private MetadataBlockViewOptions(Builder builder) {
        showDatasetFieldTypes = builder.showDatasetFieldTypes;
        editableDatasetFieldTypes = builder.editableDatasetFieldTypes;
        selected = builder.selected;
        datasetFieldViewOptions = builder.datasetFieldViewOptions;
    }

    // -------------------- GETTERS --------------------

    /**
     * Indicates if the metadata fields should be visible (used when creating/editing dataverse).
     */
    public boolean isShowDatasetFieldTypes() {
        return showDatasetFieldTypes;
    }

    /**
     * Indicates if you are able to edit metadata blocks fields to be (Optional/Required/Hidden).
     */
    public boolean isEditableDatasetFieldTypes() {
        return editableDatasetFieldTypes;
    }

    /**
     * Indicates if this metadata block is selected ( in order to mark the checkbox).
     */
    public boolean isSelected() {
        return selected;
    }

    /**
     * Retrives dataset fields view options that belong to metadata block.
     */
    public List<DatasetFieldViewOptions> getDatasetFieldViewOptions() {
        return datasetFieldViewOptions;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    // -------------------- LOGIC --------------------

    public static final class Builder {
        private boolean showDatasetFieldTypes;
        private boolean editableDatasetFieldTypes;
        private boolean selected;
        private List<DatasetFieldViewOptions> datasetFieldViewOptions;

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

        public Builder datasetFieldViewOptions(List<DatasetFieldViewOptions> val) {
            datasetFieldViewOptions = val;
            return this;
        }

        public MetadataBlockViewOptions build() {
            return new MetadataBlockViewOptions(this);
        }
    }

}
