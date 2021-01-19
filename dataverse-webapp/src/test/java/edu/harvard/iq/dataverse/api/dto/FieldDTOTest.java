package edu.harvard.iq.dataverse.api.dto;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import junit.framework.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author ellenk
 */
class FieldDTOTest {

    private FieldDTO author;

    @BeforeEach
    void setUp() {
        author = FieldDTO.createCompoundFieldDTO("author",
                FieldDTO.createPrimitiveFieldDTO("authorAffiliation", "Top"),
                FieldDTO.createPrimitiveFieldDTO("authorIdentifier", "ellenId"),
                FieldDTO.createVocabFieldDTO("authorIdentifierScheme", "ORCID"));
    }

    @Test
    @DisplayName("Should create a field with primitive value")
    void createPrimitiveFieldDTO() {
        // given & when
        FieldDTO affiliation = FieldDTO.createPrimitiveFieldDTO("authorAffiliation", "Top");

        // then
        assertThat(affiliation.getSinglePrimitive()).isEqualTo("Top");
    }

    @Test
    @DisplayName("Should write and read multiple vocabulary value")
    void shouldSetAndGetMultipleVocab() {
        // given
        FieldDTO astroType = new FieldDTO();
        astroType.setTypeName("astroType");
        List<String> values = Arrays.asList("Image", "Mosaic", "EventList");

        // when
        astroType.setMultipleVocab(values);
        List<String> readValues = astroType.getMultipleVocab();

        // then
        assertThat(readValues).containsExactlyElementsOf(values);
    }

    /**
     * Test of getSingleCompound method, of class FieldDTO.
     */
    @Test
    void testSetMultipleCompound() {
        HashSet<FieldDTO> author1Fields = new HashSet<>();

        author1Fields.add(FieldDTO.createPrimitiveFieldDTO("authorAffiliation", "Top"));
        author1Fields.add(FieldDTO.createPrimitiveFieldDTO("authorIdentifier", "ellenId"));
        author1Fields.add(FieldDTO.createVocabFieldDTO("authorIdentifierScheme", "ORCID"));

        HashSet<FieldDTO> author2Fields = new HashSet<>();

        author2Fields.add(FieldDTO.createPrimitiveFieldDTO("authorAffiliation", "Bottom"));
        author2Fields.add(FieldDTO.createPrimitiveFieldDTO("authorIdentifier", "ernieId"));
        author2Fields.add(FieldDTO.createVocabFieldDTO("authorIdentifierScheme", "DAISY"));

        List<HashSet<FieldDTO>> authorList = new ArrayList<>();
        authorList.add(author1Fields);
        authorList.add(author2Fields);
        FieldDTO compoundField = new FieldDTO();
        compoundField.setTypeName("author");
        compoundField.setMultipleCompound(authorList);

        Assert.assertEquals(compoundField.getMultipleCompound(), authorList);
    }

    /**
     * Test of setSingleCompound method, of class FieldDTO.
     */
    @Test
    void testSetSingleCompound() {
        Set<FieldDTO> authorFields = new HashSet<>();

        authorFields.add(FieldDTO.createPrimitiveFieldDTO("authorAffiliation", "Top"));
        authorFields.add(FieldDTO.createPrimitiveFieldDTO("authorIdentifier", "ellenId"));
        authorFields.add(FieldDTO.createVocabFieldDTO("authorIdentifierScheme", "ORCID"));

        FieldDTO compoundField = new FieldDTO();
        compoundField.setSingleCompound(authorFields.toArray(new FieldDTO[]{}));
        Set<FieldDTO> returned = compoundField.getSingleCompound();
        Assert.assertTrue(returned.equals(authorFields));

    }

    /**
     * Test of setMultipleCompound method, of class FieldDTO.
     */
    @Test
    void testJsonTree() {

        Gson gson = new Gson();
        FieldDTO test1 = new FieldDTO();

        test1.value = gson.toJsonTree("ellen", String.class);
        JsonElement elem = gson.toJsonTree(test1, FieldDTO.class);

        FieldDTO field1 = gson.fromJson(elem.getAsJsonObject(), FieldDTO.class);

    }


    /**
     * Test of getMultipleCompound method, of class FieldDTO.
     */
    @Test
    void testGetMultipleCompound() {

    }

    /**
     * Test of getConvertedValue method, of class FieldDTO.
     */
    @Test
    void testGetConvertedValue() {
    }

    /**
     * Test of toString method, of class FieldDTO.
     */
    @Test
    void testToString() {
    }

}
