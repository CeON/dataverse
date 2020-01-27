package edu.harvard.iq.dataverse.search.query;

import java.util.Arrays;

/**
 * Enum containing all possible object types that
 * can be assigned to dvObject in search.
 * 
 * @author madryk
 */
public enum SearchObjectType {
    
    DATAVERSES("dataverses"),
    DATASETS("datasets"),
    FILES("files");
    
    private String solrValue;
    
    // -------------------- CONSTRUCTORS --------------------
    
    private SearchObjectType(String solrValue) {
        this.solrValue = solrValue;
    }

    // -------------------- GETTERS --------------------
    
    /**
     * Returns value of field that is stored in solr index.
     */
    public String getSolrValue() {
        return solrValue;
    }
    
    // -------------------- LOGIC --------------------
    
    /**
     * Returns enum based on value from solr index.
     */
    public static SearchObjectType fromSolrValue(String solrValue) {
        return Arrays.asList(SearchObjectType.values()).stream()
                .filter(type -> type.getSolrValue().equals(solrValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Provided value does not represent known indexed dvObject type"));
    }
}