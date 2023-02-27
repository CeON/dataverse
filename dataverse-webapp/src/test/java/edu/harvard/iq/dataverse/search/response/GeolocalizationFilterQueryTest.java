package edu.harvard.iq.dataverse.search.response;

import edu.harvard.iq.dataverse.persistence.dataset.FieldType;
import edu.harvard.iq.dataverse.search.advanced.field.GroupingSearchField;
import edu.harvard.iq.dataverse.search.advanced.field.SearchField;
import edu.harvard.iq.dataverse.validation.field.validators.geobox.GeoboxTestUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class GeolocalizationFilterQueryTest {

    private GeoboxTestUtil geoboxUtil = new GeoboxTestUtil();

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        // X1 |  Y1 | X2 |  Y2 | Expected value
        "   1 |   2 |  3 |   4 | 1W, 3E, 2S, 4N",
        "   1 |  -2 |  3 |  -4 | 1W, 3E, -2S, -4N",
        "   1 |   2 |    |   4 | 1W, 2S, 4N", // Should not happen, but works
        "     |     |    |     | ''"          // Should not happen, but works
    })
    void of(String x1, String y1, String x2, String y2, String expectedValue) {
        // given
        SearchField geobox = geoboxUtil.buildGeoboxSearchField(x1, y1, x2, y2);

        // when
        GeolocalizationFilterQuery filterQuery = GeolocalizationFilterQuery.of("query", geobox);

        // then
        assertThat(filterQuery.getFriendlyFieldValue()).isEqualTo(expectedValue);
    }

    @Test
    void of__malformedOrWrongTypeField() {
        // given
        SearchField wrongType = geoboxUtil.buildGeoboxSearchField("", "", "", "");
        wrongType.getDatasetFieldType().setFieldType(FieldType.DATE);
        SearchField noType = new GroupingSearchField("", "", "", null, null);

        // when
        GeolocalizationFilterQuery wrongTypeResult = GeolocalizationFilterQuery.of("", wrongType);
        GeolocalizationFilterQuery noTypeResult = GeolocalizationFilterQuery.of("", noType);

        // then
        assertThat(wrongTypeResult).isNull();
        assertThat(noTypeResult).isNull();
    }
}