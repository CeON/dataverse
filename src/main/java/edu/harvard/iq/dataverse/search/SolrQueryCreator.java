package edu.harvard.iq.dataverse.search;

import edu.harvard.iq.dataverse.FieldType;
import edu.harvard.iq.dataverse.search.dto.CheckboxSearchField;
import edu.harvard.iq.dataverse.search.dto.NumberSearchField;
import edu.harvard.iq.dataverse.search.dto.SearchBlock;
import edu.harvard.iq.dataverse.search.dto.SearchField;
import edu.harvard.iq.dataverse.search.dto.TextSearchField;
import org.apache.commons.lang.StringUtils;

import javax.ejb.Stateless;
import java.util.List;
import java.util.stream.Collectors;


/**
 * Class used for creating solr query used in advanced search.
 */
@Stateless
public class SolrQueryCreator {

    // -------------------- LOGIC --------------------

    /**
     * Creates solr query for given Search Blocks
     *
     * @param searchBlocks
     * @return solr string query
     */
    public String constructQuery(List<SearchBlock> searchBlocks) {
        StringBuilder queryBuilder = new StringBuilder();

        searchBlocks.stream()
                .map(SearchBlock::getSearchFields)
                .forEach(searchFields -> {

                            List<TextSearchField> textSearchFields = extractTextOrDateSearchFields(searchFields);
                            queryBuilder.append(constructQueryForTextFields(textSearchFields));

                            List<NumberSearchField> numberSearchFields = extractNumberSearchFields(searchFields);
                            queryBuilder.append(constructQueryForNumberFields(numberSearchFields));

                            List<CheckboxSearchField> checkboxSearchFields = extractCheckBoxSearchFields(searchFields);
                            queryBuilder.append(constructQueryForCheckboxFields(checkboxSearchFields));
                        }

                );

        return queryBuilder.toString()
                .replaceFirst("AND", StringUtils.EMPTY)
                .trim();
    }

    // -------------------- PRIVATE --------------------

    private String constructQueryForTextFields(List<TextSearchField> textSearchFields) {
        StringBuilder textQueryBuilder = new StringBuilder();

        textSearchFields.forEach(textSearchField -> textQueryBuilder
                .append(" AND ")
                .append(textSearchField.getName())
                .append(":")
                .append(textSearchField.getFieldValue()));

        return textQueryBuilder.toString();
    }

    private String constructQueryForCheckboxFields(List<CheckboxSearchField> checkboxSearchFields) {
        StringBuilder checkboxQueryBuilder = new StringBuilder();

        checkboxSearchFields.forEach(checkboxField ->
                checkboxField.getCheckedFieldValues().forEach(value -> checkboxQueryBuilder
                        .append(" AND ")
                        .append(checkboxField.getName())
                        .append(":")
                        .append(value)
                )
        );

        return checkboxQueryBuilder.toString();
    }

    private String constructQueryForNumberFields(List<NumberSearchField> numberSearchFields) {
        StringBuilder intQueryBuilder = new StringBuilder();

        appendFieldWithMinimumNumberPresent(numberSearchFields, intQueryBuilder);

        appendFieldWithMaximumNumberPresent(numberSearchFields, intQueryBuilder);

        appendFieldWithBothNumbersPresent(numberSearchFields, intQueryBuilder);

        return intQueryBuilder.toString();
    }

    private void appendFieldWithBothNumbersPresent(List<NumberSearchField> numberSearchFields, StringBuilder intQueryBuilder) {
        numberSearchFields.stream()
                .filter(this::isBothNumberPresent)
                .forEach(numberField ->
                        intQueryBuilder
                                .append(" AND ")
                                .append(numberField.getName())
                                .append(":[")
                                .append(numberField.getMinimum())
                                .append(" TO ")
                                .append(numberField.getMaximum())
                                .append("]"));
    }

    private void appendFieldWithMaximumNumberPresent(List<NumberSearchField> numberSearchFields, StringBuilder intQueryBuilder) {
        numberSearchFields.stream()
                .filter(this::isMaximumNumberPresent)
                .forEach(numberField ->
                        intQueryBuilder
                                .append(" AND ")
                                .append(numberField.getName())
                                .append(":[")
                                .append("*")
                                .append(" TO ")
                                .append(numberField.getMaximum())
                                .append("]"));
    }

    private void appendFieldWithMinimumNumberPresent(List<NumberSearchField> numberSearchFields, StringBuilder intQueryBuilder) {
        numberSearchFields.stream()
                .filter(this::isMinimumNumberPresent)
                .forEach(numberField ->
                        intQueryBuilder
                                .append(" AND ")
                                .append(numberField.getName())
                                .append(":[")
                                .append(numberField.getMinimum())
                                .append(" TO ")
                                .append("*")
                                .append("]"));
    }

    private boolean isBothNumberPresent(NumberSearchField numberField) {
        return numberField.getMinimum() != null && numberField.getMaximum() != null;
    }

    private boolean isMaximumNumberPresent(NumberSearchField numberField) {
        return numberField.getMaximum() != null && numberField.getMinimum() == null;
    }

    private boolean isMinimumNumberPresent(NumberSearchField numberField) {
        return numberField.getMinimum() != null && numberField.getMaximum() == null;
    }

    private List<TextSearchField> extractTextOrDateSearchFields(List<SearchField> searchFields) {
        return searchFields.stream()
                .filter(this::isTextOrDateField)
                .map(TextSearchField.class::cast)
                .filter(textSearchField -> textSearchField.getFieldValue() != null)
                .collect(Collectors.toList());
    }

    private List<NumberSearchField> extractNumberSearchFields(List<SearchField> searchFields) {
        return searchFields.stream()
                .filter(searchMetadataField -> searchMetadataField.getFieldType().equals(FieldType.INT) || searchMetadataField.getFieldType().equals(FieldType.FLOAT))
                .map(NumberSearchField.class::cast)
                .filter(numberSearchField -> (numberSearchField.getMinimum() != null || numberSearchField.getMaximum() != null))
                .collect(Collectors.toList());
    }

    private List<CheckboxSearchField> extractCheckBoxSearchFields(List<SearchField> searchFields) {
        return searchFields.stream()
                .filter(searchMetadataField -> searchMetadataField.getFieldType().equals(FieldType.CHECKBOX))
                .map(CheckboxSearchField.class::cast)
                .filter(checkboxSearchField -> !checkboxSearchField.getCheckedFieldValues().isEmpty())
                .collect(Collectors.toList());
    }

    private boolean isTextOrDateField(SearchField searchField) {
        FieldType fieldType = searchField.getFieldType();
        return fieldType.equals(FieldType.TEXT) || fieldType.equals(FieldType.TEXTBOX) || fieldType.equals(FieldType.DATE);
    }
}
