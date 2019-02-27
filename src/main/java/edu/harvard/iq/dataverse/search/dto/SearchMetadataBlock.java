package edu.harvard.iq.dataverse.search.dto;

import java.util.List;

/**
 * Model for Advanced Search Page, it is responsible for gathering different types of field values for MetadataBlocks.
 */
public class SearchMetadataBlock {

    private String metadataBlockName;
    private String metadataBlockDisplayName;
    private List<SearchMetadataField> searchMetadataFields;

    public SearchMetadataBlock(String metadataBlockName, String metadataBlockDisplayName, List<SearchMetadataField> searchMetadataFields) {
        this.metadataBlockName = metadataBlockName;
        this.metadataBlockDisplayName = metadataBlockDisplayName;
        this.searchMetadataFields = searchMetadataFields;
    }

    // -------------------- GETTERS --------------------

    /**
     * Metadata block name, that is used as an id in tsv files.
     *
     * @return metadata block name
     */
    public String getMetadataBlockName() {
        return metadataBlockName;
    }

    /**
     * Metadata block name, that is localized and used for displaying purposes.
     *
     * @return localized metadata block name
     */
    public String getMetadataBlockDisplayName() {
        return metadataBlockDisplayName;
    }

    /**
     * List of field's with values, that user wants to search for.
     *
     * @return search values
     */
    public List<SearchMetadataField> getSearchMetadataFields() {
        return searchMetadataFields;
    }
}
