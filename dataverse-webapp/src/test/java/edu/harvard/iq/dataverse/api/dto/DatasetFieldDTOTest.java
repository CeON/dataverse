package edu.harvard.iq.dataverse.api.dto;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class DatasetFieldDTOTest {

    // -------------------- TESTS --------------------

    @Test
    void getSinglePrimitive() {
        // given
        DatasetFieldDTO field = new DatasetFieldDTO();
        field.setValue("value");

        // when
        String result = field.getSinglePrimitive();

        // then
        assertThat(result).isEqualTo("value");
    }

    @Test
    void getSingleVocabulary() {
        // given
        DatasetFieldDTO field = new DatasetFieldDTO();
        field.setValue("vocabulary-value");

        // when
        String result = field.getSingleVocabulary();

        // then
        assertThat(result).isEqualTo("vocabulary-value");
    }

    @Test
    void getSingleCompound() {
        // given
        DatasetFieldDTO field = new DatasetFieldDTO();

        field.setValue(createInnerFields(""));

        // when
        Set<DatasetFieldDTO> result = field.getSingleCompound();

        // then
        assertThat(result)
                .extracting(DatasetFieldDTO::getTypeName)
                .containsExactlyInAnyOrder("inner-field-1", "inner-field-2");
    }

    @Test
    void getMultiplePrimitive() {
        // given
        DatasetFieldDTO field = new DatasetFieldDTO();
        field.setValue(Arrays.asList("value-1", "value-2", "value-3"));

        // when
        List<String> result = field.getMultiplePrimitive();

        // then
        assertThat(result).containsExactly("value-1", "value-2", "value-3");
    }

    @Test
    void getMultipleVocabulary() {
        // given
        DatasetFieldDTO field = new DatasetFieldDTO();
        field.setValue(Arrays.asList("vocabulary-value-1", "vocabulary-value-2", "vocabulary-value-3"));

        // when
        List<String> result = field.getMultipleVocabulary();

        // then
        assertThat(result).containsExactly("vocabulary-value-1", "vocabulary-value-2", "vocabulary-value-3");
    }

    @Test
    void getMultipleCompound() {
        // given
        DatasetFieldDTO field = new DatasetFieldDTO();
        List<Map<String, DatasetFieldDTO>> values = new ArrayList<>();
        values.add(createInnerFields("first:"));
        values.add(createInnerFields("second:"));
        field.setValue(values);

        // when
        List<Set<DatasetFieldDTO>> result = field.getMultipleCompound();

        // then
        assertThat(result)
                .flatExtracting(e -> e.stream()
                        .map(DatasetFieldDTO::getTypeName)
                        .collect(Collectors.toList()))
                .containsExactly("first:inner-field-1", "first:inner-field-2",
                        "second:inner-field-1", "second:inner-field-2");
    }

    // -------------------- PRIVATE --------------------

    private Map<String, DatasetFieldDTO> createInnerFields(String prefix) {
        DatasetFieldDTO innerField1 = new DatasetFieldDTO();
        innerField1.setTypeName(prefix + "inner-field-1");
        DatasetFieldDTO innerField2 = new DatasetFieldDTO();
        innerField2.setTypeName(prefix + "inner-field-2");

        Map<String, DatasetFieldDTO> innerFields = new LinkedHashMap<>();
        innerFields.put(innerField1.getTypeName(), innerField1);
        innerFields.put(innerField2.getTypeName(), innerField2);
        return innerFields;
    }
}