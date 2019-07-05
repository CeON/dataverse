package edu.harvard.iq.dataverse.search;

import edu.harvard.iq.dataverse.FieldType;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

public class SolrFieldFactoryTest {

    private SolrFieldFactory solrFieldFactory = new SolrFieldFactory();

    private final String FIELD_NAME = "test";
    private final String NULL_FIELD_NAME = "nullfield";

    // -------------------- TESTS --------------------

    @Test
    public void getSolrField_forIntType() {

        //when
        SolrField solrField = solrFieldFactory.getSolrField(FIELD_NAME, NULL_FIELD_NAME, FieldType.INT, true, false);

        //then
        Assert.assertEquals(SolrField.SolrType.INTEGER, solrField.getSolrType());
        Assert.assertTrue(solrField.getNameSearchable().contains(FIELD_NAME));
        Assert.assertTrue(solrField.isAllowedToBeMultivalued());
        Assert.assertFalse(solrField.isFacetable());
    }

    @Test
    public void getSolrField_forURLType() {

        //when
        SolrField solrField = solrFieldFactory.getSolrField(FIELD_NAME, NULL_FIELD_NAME, FieldType.URL, true, false);

        //then
        Assert.assertEquals(SolrField.SolrType.TEXT_EN, solrField.getSolrType());
        Assert.assertTrue(solrField.getNameSearchable().contains(FIELD_NAME));
        Assert.assertTrue(solrField.isAllowedToBeMultivalued());
        Assert.assertFalse(solrField.isFacetable());
    }

    @Test
    public void getSolrField_forNullType() {

        //when
        SolrField solrField = solrFieldFactory.getSolrField(FIELD_NAME, NULL_FIELD_NAME, null, true, false);

        //then
        Assert.assertEquals(SolrField.SolrType.TEXT_EN, solrField.getSolrType());
        Assert.assertTrue(solrField.getNameSearchable().contains(NULL_FIELD_NAME));
        Assert.assertTrue(solrField.isAllowedToBeMultivalued());
        Assert.assertFalse(solrField.isFacetable());
    }
}