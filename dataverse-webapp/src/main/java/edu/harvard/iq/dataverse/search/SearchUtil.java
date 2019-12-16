package edu.harvard.iq.dataverse.search;

import com.google.common.collect.Sets;
import edu.harvard.iq.dataverse.common.Util;
import edu.harvard.iq.dataverse.search.SearchServiceBean.SortOrder;
import org.apache.commons.lang.StringUtils;
import org.apache.solr.common.SolrInputDocument;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SearchUtil {

    private static final String ASCENDING = "asc";
    private static final String DESCENDING = "desc";

    private static List<String> allowedOrderStrings() {
        return Arrays.asList(ASCENDING, DESCENDING);
    }

    
    // -------------------- LOGIC --------------------

    public static SolrInputDocument createSolrDoc(DvObjectSolrDoc dvObjectSolrDoc) {
        if (dvObjectSolrDoc == null) {
            return null;
        }
        SolrInputDocument solrInputDocument = new SolrInputDocument();
        solrInputDocument.addField(SearchFields.ID, dvObjectSolrDoc.getSolrId() + IndexServiceBean.discoverabilityPermissionSuffix);
        solrInputDocument.addField(SearchFields.DEFINITION_POINT, dvObjectSolrDoc.getSolrId());
        solrInputDocument.addField(SearchFields.DEFINITION_POINT_DVOBJECT_ID, dvObjectSolrDoc.getDvObjectId());
        solrInputDocument.addField(SearchFields.DISCOVERABLE_BY, dvObjectSolrDoc.getPermissions());
        return solrInputDocument;
    }

    public static String getTimestampOrNull(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        /**
         * @todo Is seconds enough precision?
         */
        return Util.getDateTimeFormat().format(timestamp);
    }

    public static SortBy getSortBy(String sortField, String sortOrder) throws Exception {

        if (StringUtils.isBlank(sortField)) {
            sortField = SearchFields.RELEVANCE;
        } else if (StringUtils.equals(sortField, "name")) {
            // "name" sounds better than "name_sort" so we convert it here so users don't have to pass in "name_sort"
            sortField = SearchFields.NAME_SORT;
        } else if (StringUtils.equals(sortField, "date")) {
            // "date" sounds better than "release_or_create_date_dt"
            sortField = SearchFields.RELEASE_OR_CREATE_DATE;
        }

        SortOrder parsedSortOrder = null;
        if (StringUtils.isBlank(sortOrder)) {
            // default sorting per field if not specified
            if (sortField.equals(SearchFields.RELEVANCE)) {
                parsedSortOrder = SortOrder.desc;
            } else if (sortField.equals(SearchFields.NAME_SORT)) {
                parsedSortOrder = SortOrder.asc;
            } else if (sortField.equals(SearchFields.RELEASE_OR_CREATE_DATE)) {
                parsedSortOrder = SortOrder.desc;
            } else {
                // asc for alphabetical by default despite GitHub using desc by default:
                // "The sort order if sort parameter is provided. One of asc or desc. Default: desc"
                // http://developer.github.com/v3/search/
                parsedSortOrder = SortOrder.asc;
            }
        }
        
        if (parsedSortOrder == null) {
            if (!allowedOrderStrings().contains(sortOrder)) {
                throw new Exception("The 'order' parameter was '" + sortOrder + "' but expected one of " + allowedOrderStrings() + ". (The 'sort' parameter was/became '" + sortField + "'.)");
            }
            
            parsedSortOrder = sortOrder.equals(ASCENDING) ? SortOrder.asc : SortOrder.desc;
        }
        
        return new SortBy(sortField, parsedSortOrder);
    }

    public static String determineFinalQuery(String userSuppliedQuery) {
        String wildcardQuery = "*";
        if (userSuppliedQuery == null) {
            return wildcardQuery;
        } else if (userSuppliedQuery.isEmpty()) {
            return wildcardQuery;
        } else {
            return userSuppliedQuery;
        }
    }

}
