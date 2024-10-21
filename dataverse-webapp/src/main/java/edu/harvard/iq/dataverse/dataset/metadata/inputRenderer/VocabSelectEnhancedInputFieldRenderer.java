package edu.harvard.iq.dataverse.dataset.metadata.inputRenderer;

import edu.harvard.iq.dataverse.persistence.dataset.ControlledVocabularyValue;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetField;
import edu.harvard.iq.dataverse.persistence.dataset.InputRendererType;

import java.util.*;
import java.util.stream.Collectors;


public class VocabSelectEnhancedInputFieldRenderer implements InputFieldRenderer {

    private Collection<ControlledVocabularyValue> all = new ArrayList<>();
    private int maxResults = 10;

    // -------------------- GETTERS --------------------

    /**
     * {@inheritDoc}
     * <p>
     * This implementation always returns {@link InputRendererType#VOCABULARY_SELECT}
     */
    @Override
    public InputRendererType getType() {
        return InputRendererType.VOCABULARY_ENHANCED_SELECT;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation returns value provided in constructor
     */
    @Override
    public boolean renderInTwoColumns() {
        return false;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation always returns {@code false}
     */
    @Override
    public boolean isHidden() {
        return false;
    }

    public Integer getMaxResults() {
        return maxResults;
    }

    public void onSelection(DatasetField datasetField, String autoCompleteId) {
        queryControlledVocabularyValues(datasetField, autoCompleteId);
    }

    public List<ControlledVocabularyValue> complete(DatasetField datasetField, String autoCompleteId) {
        return queryControlledVocabularyValues(datasetField, autoCompleteId);
    }

    public List<ControlledVocabularyValue> loadMore(DatasetField datasetField, String autoCompleteId) {
        maxResults += 10;
        return queryControlledVocabularyValues(datasetField, autoCompleteId);
    }

    public List<ControlledVocabularyValue> queryControlledVocabularyValues(DatasetField datasetField, String autoCompleteId) {
        if (all == null || all.isEmpty()) {
            all = datasetField.getDatasetFieldType().getControlledVocabularyValues();
        }
        String query = SuggestionAutocompleteHelper.processSuggestionQuery(autoCompleteId).orElse("");
        return all.stream()
                .filter(item -> !datasetField.getControlledVocabularyValues().contains(item))
                .filter(item -> item.getStrValue().toLowerCase().contains(query.toLowerCase()))
                .limit(maxResults)
                .collect(Collectors.toList());
    }
}
