package edu.harvard.iq.dataverse.search.query;

import java.util.Arrays;

public enum SearchObjectType {
    
    DATAVERSES("dataverses"),
    DATASETS("datasets"),
    FILES("files");
    
    private String solrValue;
    
    private SearchObjectType(String solrValue) {
        this.solrValue = solrValue;
    }

    public String getSolrValue() {
        return solrValue;
    }
    
    public static SearchObjectType fromSolrValue(String solrValue) {
        return Arrays.asList(SearchObjectType.values()).stream()
                .filter(type -> type.getSolrValue().equals(solrValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Provided value does not represent known indexed dvObject type"));
    }
}