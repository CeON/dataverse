package edu.harvard.iq.dataverse.search.advanced.query;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QueryWrapper {
    private String query;
    private Map<QueryPartType, List<QueryPart>> additions = new HashMap<>();

    public static final String QUERY_WRAPPER_PARAM = "queryWrapper";

    // -------------------- CONSTRUCTORS --------------------

    public QueryWrapper(String query) {
        this.query = query;
    }

    // -------------------- GETTERS --------------------

    public String getQuery() {
        return query;
    }

    public Map<QueryPartType, List<QueryPart>> getAdditions() {
        return additions;
    }

    // -------------------- SETTERS --------------------

    public void setQuery(String query) {
        this.query = query;
    }
}
