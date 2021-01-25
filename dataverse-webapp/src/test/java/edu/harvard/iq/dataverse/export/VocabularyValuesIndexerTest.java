package edu.harvard.iq.dataverse.export;


import edu.harvard.iq.dataverse.persistence.dataset.ControlledVocabularyValue;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetField;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetFieldType;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.dataset.FieldType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class VocabularyValuesIndexerTest {

    VocabularyValuesIndexer indexer = new VocabularyValuesIndexer();

    // -------------------- TESTS --------------------

    @Test
    @DisplayName("Should create index of controlled vocabulary values found in dataset version")
    void indexTest() {

        // given
        String vocabularyType1 = "vocabularyType1";
        String vocabularyType2 = "vocabularyType2";

        HashMap<String, List<String>> vocabularyData = new HashMap<>();
        vocabularyData.put(vocabularyType1, Arrays.asList("value 1-1", "value 1-2", "value 1-3"));
        vocabularyData.put(vocabularyType2, Arrays.asList("value 2-1", "value 2-2"));
        DatasetVersion datasetVersion = createDatasetVersionWithGivenVocabularies(vocabularyData);

        // when
        Map<String, Map<String, String>> index = indexer.indexLocalizedNamesOfUsedKeysByTypeAndValue(
                datasetVersion, Locale.ENGLISH);

        // then
        assertThat(index.keySet()).containsExactlyInAnyOrder(vocabularyType1, vocabularyType2);

        assertThat(index.get(vocabularyType1)).hasSize(3);
        assertThat(index.get(vocabularyType1).get("value 1-1")).isEqualTo("value 1-1");
        assertThat(index.get(vocabularyType1).get("value 1-2")).isEqualTo("value 1-2");
        assertThat(index.get(vocabularyType1).get("value 1-3")).isEqualTo("value 1-3");

        assertThat(index.get(vocabularyType2)).hasSize(2);
        assertThat(index.get(vocabularyType2).get("value 2-1")).isEqualTo("value 2-1");
        assertThat(index.get(vocabularyType2).get("value 2-2")).isEqualTo("value 2-2");
    }

    // -------------------- PRIVATE --------------------

    private DatasetVersion createDatasetVersionWithGivenVocabularies(Map<String, List<String>> vocabularyData) {
        DatasetVersion datasetVersion = new DatasetVersion();
        Random idGenerator = new Random();

        for (Map.Entry<String, List<String>> entry : vocabularyData.entrySet()) {
            DatasetFieldType type = new DatasetFieldType(entry.getKey(), FieldType.TEXT, true);
            Set<ControlledVocabularyValue> values = entry.getValue().stream()
                    .map(v -> new ControlledVocabularyValue(Math.abs(idGenerator.nextLong()), v, type))
                    .collect(Collectors.toSet());
            DatasetField field = new DatasetField();
            field.getControlledVocabularyValues().addAll(values);
            datasetVersion.getDatasetFields().add(field);
        }

        return datasetVersion;
    }
}