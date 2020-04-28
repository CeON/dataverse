package edu.harvard.iq.dataverse.search.advanced;

import org.apache.commons.lang.StringUtils;

import javax.ejb.Stateless;
import java.util.Arrays;
import java.util.List;


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
                .flatMap(searchBlock -> searchBlock.getSearchFields().stream())
                .forEach(searchField -> {
                    String constructedQuery = constructQueryForField(searchField);
                    queryBuilder
                            .append(constructedQuery.isEmpty() ? StringUtils.EMPTY : " AND " + constructedQuery);
                });

        return queryBuilder.toString()
                .replaceFirst("AND", StringUtils.EMPTY)
                .trim();
    }

    // -------------------- PRIVATE --------------------

    private String constructQueryForField(SearchField searchField) {

        if (searchField.getSearchFieldType().equals(SearchFieldType.TEXT)) {
            return constructQueryForTextField((TextSearchField) searchField);
        } else if (searchField.getSearchFieldType().equals(SearchFieldType.NUMBER)) {
            return constructQueryForNumberField((NumberSearchField) searchField);
        } else if (searchField.getSearchFieldType().equals(SearchFieldType.CHECKBOX)) {
            return constructQueryForCheckboxField((CheckboxSearchField) searchField);
        } else if (searchField.getSearchFieldType().equals(SearchFieldType.SELECT_ONE_VALUE)) {
            return constructQueryForSelectOneField((SelectOneSearchField) searchField);
        } else if (searchField.getSearchFieldType().equals(SearchFieldType.DATE)) {
            return constructQueryForDateField((DateSearchField) searchField);
        } 

        return StringUtils.EMPTY;
    }

    private String constructQueryForTextField(TextSearchField textSearchField) {
        if (textSearchField.getFieldValue() == null) {
            return StringUtils.EMPTY;
        }

        StringBuilder textQueryBuilder = new StringBuilder();

        List<String> fieldValues = Arrays.asList(textSearchField.getFieldValue().split(" "));

        fieldValues.forEach(fieldValue ->
                                    textQueryBuilder
                                            .append(textQueryBuilder.length() == 0 ? StringUtils.EMPTY : " AND ")
                                            .append(textSearchField.getName())
                                            .append(":")
                                            .append(fieldValue));

        return textQueryBuilder.toString();
    }

    private String constructQueryForCheckboxField(CheckboxSearchField checkboxSearchField) {
        StringBuilder checkboxQueryBuilder = new StringBuilder();

        checkboxSearchField.getCheckedFieldValues()
                .forEach(value -> checkboxQueryBuilder
                        .append(checkboxQueryBuilder.length() == 0 ? StringUtils.EMPTY : " AND ")
                        .append(checkboxSearchField.getName())
                        .append(":")
                        .append("\"")
                        .append(value)
                        .append("\"")
                );

        return checkboxQueryBuilder.toString();
    }

    private String constructQueryForSelectOneField(SelectOneSearchField selectOneSearchField) {
        if (selectOneSearchField.getCheckedFieldValue() == null) {
            return StringUtils.EMPTY;
        }

        StringBuilder selectOneQueryBuilder = new StringBuilder();

        String fieldValue = selectOneSearchField.getCheckedFieldValue();

        selectOneQueryBuilder
                .append(selectOneSearchField.getName())
                .append(":")
                .append("\"")
                .append(fieldValue)
                .append("\"");

        return selectOneQueryBuilder.toString();
    }

    private String constructQueryForNumberField(NumberSearchField numberSearchField) {
        StringBuilder intQueryBuilder = new StringBuilder();

        if (isOneNumberPresent(numberSearchField)) {
            intQueryBuilder
                    .append(numberSearchField.getName())
                    .append(":[")
                    .append(numberSearchField.getMinimum() == null ? "*" : numberSearchField.getMinimum())
                    .append(" TO ")
                    .append(numberSearchField.getMaximum() == null ? "*" : numberSearchField.getMaximum())
                    .append("]");
        }

        return intQueryBuilder.toString();
    }

    private String constructQueryForDateField(DateSearchField dateSearchField) {
        StringBuilder dateQueryBuilder = new StringBuilder();

        if (StringUtils.isNotEmpty(dateSearchField.getLowerLimit()) || StringUtils.isNotEmpty(dateSearchField.getUpperLimit())) {
        	dateQueryBuilder
                    .append(dateSearchField.getName())
                    .append(":[")
                    .append(StringUtils.isEmpty(dateSearchField.getLowerLimit()) ? "*" : dateSearchField.getLowerLimit())
                    .append(" TO ")
                    .append(StringUtils.isEmpty(dateSearchField.getUpperLimit()) ? "*" : dateSearchField.getUpperLimit())
                    .append("]");
        }

        return dateQueryBuilder.toString();
    }

    private boolean isOneNumberPresent(NumberSearchField numberField) {
        return numberField.getMinimum() != null || numberField.getMaximum() != null;
    }
}
