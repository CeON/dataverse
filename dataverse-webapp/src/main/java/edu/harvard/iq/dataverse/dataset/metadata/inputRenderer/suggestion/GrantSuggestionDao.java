package edu.harvard.iq.dataverse.dataset.metadata.inputRenderer.suggestion;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import java.util.List;
import java.util.Map;

@Stateless
public class GrantSuggestionDao {

    @PersistenceContext(unitName = "VDCNet-ejbPU")
    private EntityManager em;

    // -------------------- LOGIC --------------------

    public List<String> fetchSuggestions(Map<String, String> filteredBy,
                                         String suggestionSourceFieldName,
                                         String suggestionSourceFieldValue,
                                         int queryLimit) {
        String filters = generateAndFilters(filteredBy);

        TypedQuery<String> query = em.createQuery("SELECT DISTINCT grant." + suggestionSourceFieldName + " FROM GrantSuggestion grant WHERE "
                                                          + filters + " AND UPPER(grant." + suggestionSourceFieldName + ")" +
                                                          " LIKE UPPER(:" + suggestionSourceFieldName + ")",
                                                  String.class)
                .setParameter(suggestionSourceFieldName, "%" + suggestionSourceFieldValue + "%")
                .setMaxResults(queryLimit);

        fillQueryWithValues(filteredBy, query);

        return query.getResultList();
    }

    public List<String> fetchSuggestions(String suggestionSourceFieldName,
                                         String suggestionSourceFieldValue,
                                         int queryLimit) {

        List<String> result = em.createQuery("SELECT DISTINCT grant." + suggestionSourceFieldName + " FROM GrantSuggestion grant " +
                                                     " WHERE UPPER(grant." + suggestionSourceFieldName + ") LIKE UPPER(:" + suggestionSourceFieldName + ")",
                                                 String.class)
                .setParameter(suggestionSourceFieldName, "%" + suggestionSourceFieldValue + "%")
                .setMaxResults(queryLimit)
                .getResultList();

        return result;
    }

    // -------------------- PRIVATE --------------------

    private String generateAndFilters(Map<String, String> filteredBy) {
        StringBuilder filterBuilder = new StringBuilder();

        filteredBy.forEach((key, value) -> {
            filterBuilder.append("grant.");
            filterBuilder.append(key);
            filterBuilder.append(" = ");
            filterBuilder.append(":").append(key);
            filterBuilder.append(" AND ");
        });

        filterBuilder.delete(filterBuilder.lastIndexOf(" AND "), filterBuilder.length());

        return filterBuilder.toString();
    }

    private TypedQuery<String> fillQueryWithValues(Map<String, String> filterValues, TypedQuery<String> queryWithFilters) {
        for (Map.Entry<String, String> entry : filterValues.entrySet()) {
            queryWithFilters.setParameter(entry.getKey(), entry.getValue());
        }

        return queryWithFilters;
    }
}
