package edu.harvard.iq.dataverse.search.advanced.query;

import edu.harvard.iq.dataverse.search.advanced.field.SearchField;
import org.apache.commons.lang3.StringUtils;

public class QueryPart {
    public static final QueryPart EMPTY = new QueryPart(QueryPartType.NONE, StringUtils.EMPTY, null);

    public final QueryPartType queryPartType;
    public final String solrQueryFragment;
    public final SearchField searchField;

    // -------------------- CONSTRUCTORS --------------------

    public QueryPart(QueryPartType queryPartType, String solrQueryFragment, SearchField searchField) {
        this.queryPartType = queryPartType;
        this.solrQueryFragment = solrQueryFragment;
        this.searchField = searchField;
    }
}
