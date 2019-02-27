package edu.harvard.iq.dataverse.search;

import edu.harvard.iq.dataverse.FieldType;
import edu.harvard.iq.dataverse.search.dto.CheckboxSearchField;
import edu.harvard.iq.dataverse.search.dto.IntegerSearchField;
import edu.harvard.iq.dataverse.search.dto.SearchMetadataField;
import edu.harvard.iq.dataverse.search.dto.TextSearchField;

import javax.ejb.Stateless;
import java.util.List;
import java.util.stream.Collectors;

@Stateless
public class SolrQueryCreator {

    // -------------------- LOGIC --------------------

    public String constructQuery(List<SearchMetadataField> searchFields) {
        StringBuilder queryBuilder = new StringBuilder();

        queryBuilder.append("(");

        List<TextSearchField> textSearchFields = extractTextOrDateSearchFields(searchFields);
        queryBuilder.append(constructQueryForTextFields(textSearchFields));

        return queryBuilder.toString();
    }

    // -------------------- PRIVATE --------------------

    private String constructQueryForTextFields(List<TextSearchField> textSearchFields) {
        StringBuilder textQueryBuilder = new StringBuilder();

        textSearchFields.forEach(textSearchField -> textQueryBuilder
                .append(textSearchField.getName())
                .append(":")
                .append(textSearchField.getFieldValue())
                .append("AND"));

        return textQueryBuilder.toString();
    }

    private List<TextSearchField> extractTextOrDateSearchFields(List<SearchMetadataField> searchFields) {
        return searchFields.stream()
                .filter(this::isTextOrDateField)
                .map(TextSearchField.class::cast)
                .filter(textSearchField -> textSearchField.getFieldValue() != null)
                .collect(Collectors.toList());
    }

    private List<IntegerSearchField> extractIntegerSearchFields(List<SearchMetadataField> searchFields) {
        return searchFields.stream()
                .filter(searchMetadataField -> searchMetadataField.getFieldType().equals(FieldType.INT))
                .map(IntegerSearchField.class::cast)
                .filter(integerSearchField -> (integerSearchField.getMinimum() != null || integerSearchField.getMaximum() != null))
                .collect(Collectors.toList());
    }

    private List<CheckboxSearchField> extractCheckBoxSearchFields(List<SearchMetadataField> searchFields) {
        return searchFields.stream()
                .filter(searchMetadataField -> searchMetadataField.getFieldType().equals(FieldType.CHECKBOX))
                .map(CheckboxSearchField.class::cast)
                .filter(checkboxSearchField -> !checkboxSearchField.getCheckedFieldValues().isEmpty())
                .collect(Collectors.toList());
    }

    private boolean isTextOrDateField(SearchMetadataField searchMetadataField) {
        FieldType fieldType = searchMetadataField.getFieldType();
        return fieldType.equals(FieldType.TEXT) || fieldType.equals(FieldType.TEXTBOX) || fieldType.equals(FieldType.DATE);
    }
}
