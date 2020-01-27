package edu.harvard.iq.dataverse.search.query;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SearchForTypesTest {

    // -------------------- TESTS --------------------

    @Test
    public void byType() {
        // when
        SearchForTypes typesToSearch = SearchForTypes.byTypes(SearchObjectType.DATAVERSES, SearchObjectType.FILES);
        // then
        assertThat(typesToSearch.getTypes(), containsInAnyOrder(SearchObjectType.DATAVERSES, SearchObjectType.FILES));
    }

    @Test
    public void byType__multiple_same_types() {
        // when
        SearchForTypes typesToSearch = SearchForTypes.byTypes(SearchObjectType.DATAVERSES, SearchObjectType.DATAVERSES);
        // then
        assertThat(typesToSearch.getTypes(), containsInAnyOrder(SearchObjectType.DATAVERSES));
    }

    @Test
    public void byType__no_type() {
        // when & then
        assertThrows(IllegalArgumentException.class, () -> SearchForTypes.byTypes());
    }

    @Test
    public void all() {
        // when
        SearchForTypes typesToSearch = SearchForTypes.all();
        // then
        assertThat(typesToSearch.getTypes(), containsInAnyOrder(
                SearchObjectType.DATAVERSES, SearchObjectType.DATASETS, SearchObjectType.FILES));
    }

}
