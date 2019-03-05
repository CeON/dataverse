package edu.harvard.iq.dataverse.search;

import edu.harvard.iq.dataverse.FieldType;
import edu.harvard.iq.dataverse.search.dto.CheckboxSearchField;
import edu.harvard.iq.dataverse.search.dto.NumberSearchField;
import edu.harvard.iq.dataverse.search.dto.SearchBlock;
import edu.harvard.iq.dataverse.search.dto.SearchField;
import edu.harvard.iq.dataverse.search.dto.TextSearchField;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static io.vavr.collection.List.of;

class SolrQueryCreatorTest {

    private SolrQueryCreator solrQueryCreator = new SolrQueryCreator();

    @Test
    public void constructTextQuery() {
        //given
        SearchBlock searchBlock = new SearchBlock("TEST", "TEST", createTextSearchFields());
        //when
        String result = solrQueryCreator.constructQuery(of(searchBlock).asJava());
        //then
        Assert.assertEquals("text1:testValue1 AND text2:testValue2", result);
    }

    @Test
    public void constructNumberQuery() {
        //given
        SearchBlock searchBlock = new SearchBlock("TEST", "TEST", createNumberSearchFields());
        //when
        String result = solrQueryCreator.constructQuery(of(searchBlock).asJava());
        //then
        Assert.assertEquals("number1:[1 TO 2] AND number2:[3.1 TO 4.1]", result);
    }

    @Test
    public void constructCheckboxQuery() {
        //given
        SearchBlock searchBlock = new SearchBlock("TEST", "TEST", createCheckboxSearchFields());
        //when
        String result = solrQueryCreator.constructQuery(of(searchBlock).asJava());
        //then
        Assert.assertEquals("checkboxValues:checkboxValue1 AND checkboxValues:checkboxValue2", result);
    }

    private List<SearchField> createNumberSearchFields() {
        SearchField testValue1 = new NumberSearchField("number1", "number1", "desc", FieldType.INT);
        ((NumberSearchField) testValue1).setMinimum(new BigDecimal(1));
        ((NumberSearchField) testValue1).setMaximum(new BigDecimal(2));

        SearchField testValue2 = new NumberSearchField("number2", "number2", "desc", FieldType.FLOAT);
        ((NumberSearchField) testValue2).setMinimum(new BigDecimal("3.1"));
        ((NumberSearchField) testValue2).setMaximum(new BigDecimal("4.1"));

        return of(testValue1, testValue2).asJava();
    }

    private List<SearchField> createCheckboxSearchFields() {
        SearchField testValue1 = new CheckboxSearchField("checkboxValues", "checkboxValues", "desc", FieldType.CHECKBOX);
        ((CheckboxSearchField) testValue1).getCheckedFieldValues().addAll(of("checkboxValue1", "checkboxValue2").asJava());

        return of(testValue1).asJava();
    }

    private List<SearchField> createTextSearchFields() {
        SearchField testValue1 = new TextSearchField("text1", "text1", "desc", FieldType.TEXT);
        ((TextSearchField) testValue1).setFieldValue("testValue1");

        SearchField testValue2 = new TextSearchField("text2", "text2", "desc", FieldType.TEXT);
        ((TextSearchField) testValue2).setFieldValue("testValue2");

        return of(testValue1, testValue2).asJava();
    }
}