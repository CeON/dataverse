package edu.harvard.iq.dataverse.api.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import edu.harvard.iq.dataverse.search.response.FacetCategory;
import edu.harvard.iq.dataverse.search.response.SolrQueryResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonPropertyOrder({"q", "total_count", "start", "spelling_alternatives", "items", "facets", "count_in_response"})
public class SearchDTO {

    private String q;

    @JsonProperty("total_count")
    private Long totalCount;

    private Long start;

    @JsonProperty("spelling_alternatives")
    private Map<String, String> spellingAlternatives;

    private List<SolrSearchResultDTO> items;

    private List<Object> facets = new ArrayList<>();

    @JsonProperty("count_in_response")
    private Integer countInResponse;

    // -------------------- GETTERS --------------------

    public String getQ() {
        return q;
    }

    public Long getTotalCount() {
        return totalCount;
    }

    public Long getStart() {
        return start;
    }

    public Map<String, String> getSpellingAlternatives() {
        return spellingAlternatives;
    }

    public List<SolrSearchResultDTO> getItems() {
        return items;
    }

    public List<Object> getFacets() {
        return facets;
    }

    public Integer getCountInResponse() {
        return countInResponse;
    }

    // -------------------- SETTERS --------------------

    public void setQ(String q) {
        this.q = q;
    }

    public void setTotalCount(Long totalCount) {
        this.totalCount = totalCount;
    }

    public void setStart(Long start) {
        this.start = start;
    }

    public void setSpellingAlternatives(Map<String, String> spellingAlternatives) {
        this.spellingAlternatives = spellingAlternatives;
    }

    public void setItems(List<SolrSearchResultDTO> items) {
        this.items = items;
    }

    public void setFacets(List<Object> facets) {
        this.facets = facets;
    }

    public void setCountInResponse(Integer countInResponse) {
        this.countInResponse = countInResponse;
    }

    // -------------------- INNER CLASSES --------------------

    public static class Creator {
        public SearchDTO create(SolrQueryResponse response) {
            SearchDTO result = new SearchDTO();
            result.setQ(response.getSolrQuery().getQuery());
            result.setTotalCount(response.getNumResultsFound());
            result.setStart(response.getResultsStart());
            result.setSpellingAlternatives(createSpellingAlternatives(response));
            result.setItems(SolrSearchResultDTO.Creator.createResultsForSearch(response));
            result.getFacets().add(createFacets(response));
            result.setCountInResponse(response.getSolrSearchResults().size());
            return result;
        }

        // -------------------- PRIVATE --------------------

        private Map<String, String> createSpellingAlternatives(SolrQueryResponse response) {
            return response.getSpellingSuggestionsByToken()
                    .entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString(), (prev, next) -> next));
        }

        private Object createFacets(SolrQueryResponse response) {
            Map<String, Object> facetCategories = new HashMap<>();
            for (FacetCategory category : response.getFacetCategoryList()) {
                Map<String, Object> facetCategory = new HashMap<>();
                facetCategory.put("friendly", category.getFriendlyName());
                facetCategory.put("labels", category.getFacetLabels().stream()
                        .map(l -> Collections.singletonMap(l.getName(), l.getCount()))
                        .collect(Collectors.toList()));
                facetCategories.put(category.getName(), facetCategory);
            }
            return facetCategories;
        }
    }
}
