package edu.harvard.iq.dataverse.persistence.dataverse.link;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import java.io.Serializable;

@Entity
@Table(indexes = {@Index(columnList = "savedsearch_id")})
public class SavedSearchFilterQuery implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String filterQuery;

    @ManyToOne(cascade = CascadeType.PERSIST)
    @JoinColumn(nullable = false)
    private SavedSearch savedSearch;

    /**
     * This default constructor is only here to prevent this error at
     * deployment:
     * <p>
     * The instance creation method
     * [edu.harvard.iq.dataverse.search.savedsearch.SavedSearchFilterQuery.<Default Constructor>],
     * with no parameters, does not exist, or is not accessible.
     * <p>
     * Don't use it.
     */
    @Deprecated
    public SavedSearchFilterQuery() {
    }

    public SavedSearchFilterQuery(String filterQuery, SavedSearch savedSearch) {
        this.filterQuery = filterQuery;
        this.savedSearch = savedSearch;
    }

    @Override
    public String toString() {
        return "SavedSearchFilterQuery{" + "id=" + id + ", filterQuery=" + filterQuery + '}';
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFilterQuery() {
        return filterQuery;
    }

    public void setFilterQuery(String filterQuery) {
        this.filterQuery = filterQuery;
    }

    public SavedSearch getSavedSearch() {
        return savedSearch;
    }

    public void setSavedSearch(SavedSearch savedSearch) {
        this.savedSearch = savedSearch;
    }

}
