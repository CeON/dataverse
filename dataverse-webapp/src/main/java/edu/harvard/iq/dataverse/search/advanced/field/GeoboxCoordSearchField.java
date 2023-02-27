package edu.harvard.iq.dataverse.search.advanced.field;

import edu.harvard.iq.dataverse.persistence.dataset.DatasetFieldType;
import edu.harvard.iq.dataverse.persistence.dataset.ValidatableField;
import edu.harvard.iq.dataverse.search.SolrField;
import edu.harvard.iq.dataverse.search.advanced.SearchFieldType;
import edu.harvard.iq.dataverse.search.advanced.query.QueryPart;
import edu.harvard.iq.dataverse.search.advanced.query.QueryPartType;
import edu.harvard.iq.dataverse.search.index.geobox.Rectangle;
import edu.harvard.iq.dataverse.search.index.geobox.RectangleToSolrConverter;
import edu.harvard.iq.dataverse.validation.field.validators.geobox.GeoboxFields;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GeoboxCoordSearchField extends SearchField {

    private String fieldValue;

    // -------------------- CONSTRUCTORS --------------------

    public GeoboxCoordSearchField(DatasetFieldType datasetFieldType) {
        super(datasetFieldType.getName(), datasetFieldType.getDisplayName(), datasetFieldType.getDescription(),
                SearchFieldType.GEOBOX_COORD, datasetFieldType);
    }

    // -------------------- GETTERS --------------------

    public String getFieldValue() {
        return fieldValue;
    }

    // -------------------- LOGIC --------------------

    @Override
    public List<String> getValidatableValues() {
        return Collections.singletonList(fieldValue);
    }

    @Override
    public QueryPart getQueryPart() {
        SearchField parent = this.getParent().getOrElse((SearchField) null);
        if (parent == null) {
            return QueryPart.EMPTY;
        }
        List<SearchField> children = parent.getChildren();
        Map<String, String> coords = children.stream()
                .filter(f -> StringUtils.isNotBlank(f.getSingleValue()))
                .collect(Collectors.toMap(f -> f.getDatasetFieldType().getMetadata("geoboxCoord"),
                        ValidatableField::getSingleValue,
                        (prev, next) -> next));
        if (coords.size() != 4) {
            return QueryPart.EMPTY;
        }
        Rectangle rectangle = new Rectangle(
                coords.get(GeoboxFields.X1.fieldType()), coords.get(GeoboxFields.Y1.fieldType()),
                coords.get(GeoboxFields.X2.fieldType()), coords.get(GeoboxFields.Y2.fieldType()));
        SolrField parentSolrField = SolrField.of(parent.getDatasetFieldType());
        String queryFragment = new RectangleToSolrConverter()
                .wrapIfNeeded(rectangle.cutIfNeeded());
        return new QueryPart(QueryPartType.GEOBOX_FILTER,
                String.format("{!field f=%s}Intersects(%s)", parentSolrField.getNameSearchable(), queryFragment), parent);
    }

    // -------------------- SETTERS --------------------

    public void setFieldValue(String fieldValue) {
        this.fieldValue = fieldValue;
    }
}
