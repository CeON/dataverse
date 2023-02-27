package edu.harvard.iq.dataverse.search.advanced;

import edu.harvard.iq.dataverse.persistence.dataset.DatasetFieldType;
import edu.harvard.iq.dataverse.persistence.dataset.FieldType;
import edu.harvard.iq.dataverse.search.advanced.field.SearchField;
import edu.harvard.iq.dataverse.search.advanced.query.QueryPart;
import edu.harvard.iq.dataverse.search.advanced.query.QueryPartType;
import edu.harvard.iq.dataverse.search.advanced.query.QueryWrapper;
import io.vavr.control.Option;

import javax.ejb.Stateless;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


/** Class used for creating solr query used in advanced search. */
@Stateless
public class SolrQueryCreator {

    // -------------------- LOGIC --------------------

    /** Creates wrapped solr query for given Search Blocks */
    public QueryWrapper constructQueryWrapper(List<SearchBlock> searchBlocks) {
        Map<QueryPartType, List<QueryPart>> collectedQueryParts = groupQueryParts(searchBlocks);
        String query = collectedQueryParts.getOrDefault(QueryPartType.QUERY, Collections.emptyList()).stream()
                .map(q -> q.solrQueryFragment)
                .collect(Collectors.joining(" AND "));
        QueryWrapper wrapper = new QueryWrapper(query);
        collectedQueryParts.entrySet().stream()
                .filter(e -> e.getKey() != QueryPartType.NONE && e.getKey() != QueryPartType.QUERY)
                .forEach(e -> wrapper.getAdditions().put(e.getKey(), e.getValue()));
        return wrapper;
    }

    // -------------------- PRIVATE --------------------

    private Map<QueryPartType, List<QueryPart>> groupQueryParts(List<SearchBlock> searchBlocks) {
        Map<QueryPartType, List<QueryPart>> collectedQueryParts = new HashMap<>();
        Set<SearchField> geoboxParents = new HashSet<>();
        for (SearchBlock block : searchBlocks) {
            for (SearchField field : block.getSearchFields()) {
                Option<SearchField> parent = field.getParent();
                if (parent.map(SearchField::getDatasetFieldType)
                        .map(DatasetFieldType::getFieldType)
                        .getOrNull() == FieldType.GEOBOX) {
                    if (geoboxParents.contains(parent.get())) {
                        continue;
                    }
                    geoboxParents.add(parent.get());
                }
                QueryPart queryPart = field.getQueryPart();
                QueryPartType key = queryPart.queryPartType;
                if (collectedQueryParts.containsKey(key)) {
                    collectedQueryParts.get(key).add(queryPart);
                } else {
                    collectedQueryParts.put(key, new ArrayList<>(Collections.singletonList(queryPart)));
                }
            }
        }
        return collectedQueryParts;
    }
}
