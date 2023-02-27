package edu.harvard.iq.dataverse.search.response;

import edu.harvard.iq.dataverse.persistence.dataset.FieldType;
import edu.harvard.iq.dataverse.persistence.dataset.ValidatableField;
import edu.harvard.iq.dataverse.search.advanced.field.SearchField;
import edu.harvard.iq.dataverse.validation.field.validators.geobox.GeoboxFields;
import io.vavr.Tuple;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GeolocalizationFilterQuery extends FilterQuery {

    // -------------------- CONSTRUCTORS --------------------

    private GeolocalizationFilterQuery(String query, String friendlyFieldName, String friendlyFieldValue) {
        super(query, friendlyFieldName, friendlyFieldValue);
    }

    // -------------------- LOGIC --------------------

    public static GeolocalizationFilterQuery of(String query, SearchField searchField) {
        if (searchField.getDatasetFieldType() == null || searchField.getDatasetFieldType().getFieldType() != FieldType.GEOBOX) {
            return null;
        }
        Map<String, String> coords = searchField.getChildren().stream()
                .filter(f -> f.getDatasetFieldType() != null)
                .collect(Collectors.toMap(f -> f.getDatasetFieldType().getMetadata("geoboxCoord"),
                        ValidatableField::getSingleValue, (prev, next) -> next));
        String friendlyValue = Stream.of(GeoboxFields.X1, GeoboxFields.X2, GeoboxFields.Y1, GeoboxFields.Y2)
                .map(v -> Tuple.of(v.fieldType(), coords.get(v.fieldType())))
                .filter(t -> t._2() != null)
                .map(t -> t._2() + t._1())
                .collect(Collectors.joining(", "));
        return new GeolocalizationFilterQuery(query, searchField.getDisplayName(), friendlyValue);
    }
}
