package edu.harvard.iq.dataverse.mydata;

import edu.harvard.iq.dataverse.DataFileServiceBean;
import edu.harvard.iq.dataverse.DatasetServiceBean;
import edu.harvard.iq.dataverse.DatasetVersionServiceBean;
import edu.harvard.iq.dataverse.DataverseRoleServiceBean;
import edu.harvard.iq.dataverse.DataverseServiceBean;
import edu.harvard.iq.dataverse.DataverseSession;
import edu.harvard.iq.dataverse.DvObjectServiceBean;
import edu.harvard.iq.dataverse.ThumbnailServiceWrapper;
import edu.harvard.iq.dataverse.WidgetWrapper;
import edu.harvard.iq.dataverse.api.AbstractApiBean;
import edu.harvard.iq.dataverse.authorization.AuthenticationServiceBean;
import edu.harvard.iq.dataverse.authorization.DataverseRolePermissionHelper;
import edu.harvard.iq.dataverse.authorization.groups.GroupServiceBean;
import edu.harvard.iq.dataverse.engine.command.DataverseRequest;
import edu.harvard.iq.dataverse.persistence.DvObject;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.datafile.DataTable;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.persistence.user.DataverseRole;
import edu.harvard.iq.dataverse.search.FacetCategory;
import edu.harvard.iq.dataverse.search.FacetLabel;
import edu.harvard.iq.dataverse.search.IndexServiceBean;
import edu.harvard.iq.dataverse.search.SearchConstants;
import edu.harvard.iq.dataverse.search.SearchException;
import edu.harvard.iq.dataverse.search.SearchFields;
import edu.harvard.iq.dataverse.search.SearchIncludeFragment;
import edu.harvard.iq.dataverse.search.SearchServiceBean;
import edu.harvard.iq.dataverse.search.SolrQueryResponse;
import edu.harvard.iq.dataverse.search.SolrSearchResult;
import edu.harvard.iq.dataverse.search.SortBy;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import org.apache.commons.lang.StringUtils;

import javax.ejb.EJB;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.naming.directory.SearchResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@ViewScoped
@Named("MyDataSearchFragment")
public class MyDataSearchFragment extends AbstractApiBean implements java.io.Serializable {

    private static final Logger logger = Logger.getLogger(MyDataSearchFragment.class.getCanonicalName());

    @EJB
    SearchServiceBean searchService;
    @EJB
    DataverseServiceBean dataverseService;
    @EJB
    DatasetServiceBean datasetService;
    @EJB
    DvObjectServiceBean dvObjectService;
    @Inject
    DataverseSession session;
    @EJB
    SettingsServiceBean settingsService;
    @Inject
    ThumbnailServiceWrapper thumbnailServiceWrapper;
    @EJB
    private AuthenticationServiceBean authenticationService;
    @EJB
    private DataverseRoleServiceBean dataverseRoleService;
    @EJB
    private DvObjectServiceBean dvObjectServiceBean;
    @EJB
    private GroupServiceBean groupService;
    @EJB
    private DatasetVersionServiceBean datasetVersionService;
    @EJB
    private DataFileServiceBean dataFileService;
    @Inject
    private WidgetWrapper widgetWrapper;

    private String browseModeString = "browse";
    private String searchModeString = "search";
    private String mode;
    private String query;
    private List<String> filterQueries = new ArrayList<>();
    private List<FacetCategory> facetCategoryList = new ArrayList<>();
    private List<SolrSearchResult> searchResultsList = new ArrayList<>();
    private int searchResultsCount;
    private String fq0;
    private String fq1;
    private String fq2;
    private String fq3;
    private String fq4;
    private String fq5;
    private String fq6;
    private String fq7;
    private String fq8;
    private String fq9;
    private List<String> roleFilters = new ArrayList<>();
    private String rf0;
    private String rf1;
    private String rf2;
    private String rf3;
    private String rf4;
    private String rf5;
    private Dataverse dataverse;
    private String dataversePath = null;
    private String selectedTypesString;
    private List<String> selectedTypesList = new ArrayList<>();
    private String selectedTypesHumanReadable;
    private String searchFieldType = SearchFields.TYPE;
    private String searchFieldSubtree = SearchFields.SUBTREE;

    private String searchFieldNameSort = SearchFields.NAME_SORT;
    private String searchFieldRelevance = SearchFields.RELEVANCE;

    private String searchFieldReleaseOrCreateDate = SearchFields.RELEASE_OR_CREATE_DATE;
    final private String ASCENDING = SortOrder.asc.toString();
    final private String DESCENDING = SortOrder.desc.toString();
    private String typeFilterQuery;
    Map<String, Long> previewCountbyType = new HashMap<>();
    private String sortField;
    private SortOrder sortOrder;
    private int page = 1;
    private int paginationGuiStart = 1;
    private int paginationGuiEnd = 100;
    private int paginationGuiRows = 100;
    Map<String, String> datasetfieldFriendlyNamesBySolrField = new HashMap<>();
    Map<String, String> staticSolrFieldFriendlyNamesBySolrField = new HashMap<>();
    private boolean solrIsDown = false;
    private Map<String, Integer> numberOfFacets = new HashMap<>();
    private boolean debug = false;

    List<String> filterQueriesDebug = new ArrayList<>();

    private boolean rootDv = false;
    private boolean solrErrorEncountered = false;
    private SolrQueryResponse solrQueryResponse;
    private MyDataFinder myDataFinder;
    private AuthenticatedUser authUser = null;
    private List<DataverseRole> roleList;
    private RoleTagRetriever roleTagRetriever;
    private DataverseRolePermissionHelper rolePermissionHelper;
    private String errorFromSolr;
    private String userIdentifier;
    private List<String> myRoles;
    private List<String> selectedRoles;


    /**
     * Used for capturing errors that happen during solr query
     * Added to catch exceptions when parsing the solr query string
     *
     * @return
     */
    public boolean wasSolrErrorEncountered() {

        if (this.solrErrorEncountered) {
            return true;
        }
        if (!this.hasValidFilterQueries()) {
            setSolrErrorEncountered(true);
            return true;
        }
        return solrErrorEncountered;
    }

    /**
     * Set the solrErrorEncountered flag
     *
     * @param val
     */
    public void setSolrErrorEncountered(boolean val) {
        this.solrErrorEncountered = val;
    }

    public String getBrowseModeString() {
        return browseModeString;
    }

    public String getSearchModeString() {
        return searchModeString;
    }

    public List<String> getMyRoles() {
        return myRoles;
    }

    public String getMode() {
        // enum would be prefered but we can't reference enums from JSF:
        // http://stackoverflow.com/questions/2524420/how-to-testing-for-enum-equality-in-jsf/2524901#2524901
        return mode;
    }

    public int getNumberOfFacets(String name, int defaultValue) {
        Integer numFacets = numberOfFacets.get(name);
        if (numFacets == null) {
            numberOfFacets.put(name, defaultValue);
            numFacets = defaultValue;
        }
        return numFacets;
    }

    public void incrementFacets(String name, int incrementNum) {
        Integer numFacets = numberOfFacets.get(name);
        if (numFacets == null) {
            numFacets = incrementNum;
        }
        numberOfFacets.put(name, numFacets + incrementNum);
    }

    // http://stackoverflow.com/questions/1515437/java-function-for-arrays-like-phps-join/1515548#1515548
    String combine(String[] s, String glue) {
        int k = s.length;
        if (k == 0) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        out.append(s[0]);
        for (int x = 1; x < k; ++x) {
            out.append(glue).append(s[x]);
        }
        return out.toString();
    }

    private Long findFacetCountByType(String type) {
        return previewCountbyType.get(type);
    }

    private String getCreatedOrReleasedDate(DvObject dvObject, Date date) {
        // the hedge is for https://redmine.hmdc.harvard.edu/issues/3806
        String hedge = "";
        if (dvObject instanceof Dataverse) {
            hedge = "";
        } else if (dvObject instanceof Dataset) {
            hedge = " maybe";
        } else if (dvObject instanceof DataFile) {
            hedge = " maybe";
        } else {
            hedge = " what object is this?";
        }
        if (dvObject.isReleased()) {
            return date + " released" + hedge;
        } else {
            return date + " created" + hedge;
        }
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public List<String> getFilterQueries() {
        return filterQueries;
    }

    public void setFilterQueries(List<String> filterQueries) {
        this.filterQueries = filterQueries;
    }

    public List<FacetCategory> getFacetCategoryList() {
        return facetCategoryList;
    }

    public void setFacetCategoryList(List<FacetCategory> facetCategoryList) {
        this.facetCategoryList = facetCategoryList;
    }

    public List<SolrSearchResult> getSearchResultsList() {
        return searchResultsList;
    }

    public void setSearchResultsList(List<SolrSearchResult> searchResultsList) {
        this.searchResultsList = searchResultsList;
    }

    public int getSearchResultsCount() {
        return searchResultsCount;
    }

    public void setSearchResultsCount(int searchResultsCount) {
        this.searchResultsCount = searchResultsCount;
    }

    public String getFq0() {
        return fq0;
    }

    public void setFq0(String fq0) {
        this.fq0 = fq0;
    }

    public String getFq1() {
        return fq1;
    }

    public void setFq1(String fq1) {
        this.fq1 = fq1;
    }

    public String getFq2() {
        return fq2;
    }

    public void setFq2(String fq2) {
        this.fq2 = fq2;
    }

    public String getFq3() {
        return fq3;
    }

    public void setFq3(String fq3) {
        this.fq3 = fq3;
    }

    public String getFq4() {
        return fq4;
    }

    public void setFq4(String fq4) {
        this.fq4 = fq4;
    }

    public String getFq5() {
        return fq5;
    }

    public void setFq5(String fq5) {
        this.fq5 = fq5;
    }

    public String getFq6() {
        return fq6;
    }

    public void setFq6(String fq6) {
        this.fq6 = fq6;
    }

    public String getFq7() {
        return fq7;
    }

    public void setFq7(String fq7) {
        this.fq7 = fq7;
    }

    public String getFq8() {
        return fq8;
    }

    public void setFq8(String fq8) {
        this.fq8 = fq8;
    }

    public String getFq9() {
        return fq9;
    }

    public void setFq9(String fq9) {
        this.fq9 = fq9;
    }


    public void setRf0(String rf0) {
        this.rf0 = rf0;
    }

    public void setRf1(String rf1) {
        this.rf1 = rf1;
    }

    public void setRf2(String rf2) {
        this.rf2 = rf2;
    }

    public void setRf3(String rf3) {
        this.rf3 = rf3;
    }

    public void setRf4(String rf4) {
        this.rf4 = rf4;
    }

    public void setRf5(String rf5) {
        this.rf5 = rf5;
    }

    public List<String> getRoleFilters() {
        return roleFilters;
    }

    public String getRf0() {
        return rf0;
    }

    public String getRf1() {
        return rf1;
    }

    public String getRf2() {
        return rf2;
    }

    public String getRf3() {
        return rf3;
    }

    public String getRf4() {
        return rf4;
    }

    public String getRf5() {
        return rf5;
    }


    public Dataverse getDataverse() {
        return dataverse;
    }

    public void setDataverse(Dataverse dataverse) {
        this.dataverse = dataverse;
    }

    public String getSelectedTypesString() {
        return selectedTypesString;
    }

    public void setSelectedTypesString(String selectedTypesString) {
        this.selectedTypesString = selectedTypesString;
    }

    public List<String> getSelectedTypesList() {
        return selectedTypesList;
    }

    public void setSelectedTypesList(List<String> selectedTypesList) {
        this.selectedTypesList = selectedTypesList;
    }

    public String getSelectedTypesHumanReadable() {
        return selectedTypesHumanReadable;
    }

    public void setSelectedTypesHumanReadable(String selectedTypesHumanReadable) {
        this.selectedTypesHumanReadable = selectedTypesHumanReadable;
    }

    public String getSearchFieldType() {
        return searchFieldType;
    }

    public void setSearchFieldType(String searchFieldType) {
        this.searchFieldType = searchFieldType;
    }

    public String getSearchFieldSubtree() {
        return searchFieldSubtree;
    }

    public void setSearchFieldSubtree(String searchFieldSubtree) {
        this.searchFieldSubtree = searchFieldSubtree;
    }

    public String getTypeFilterQuery() {
        return typeFilterQuery;
    }

    public void setTypeFilterQuery(String typeFilterQuery) {
        this.typeFilterQuery = typeFilterQuery;
    }

    public Long getFacetCountDatasets() {
        return findFacetCountByType("datasets");
    }

    public Long getFacetCountDataverses() {
        return findFacetCountByType("dataverses");
    }

    public Long getFacetCountFiles() {
        return findFacetCountByType("files");
    }

    public String getSearchFieldRelevance() {
        return searchFieldRelevance;
    }

    public void setSearchFieldRelevance(String searchFieldRelevance) {
        this.searchFieldRelevance = searchFieldRelevance;
    }

    public String getSearchFieldNameSort() {
        return searchFieldNameSort;
    }

    public void setSearchFieldNameSort(String searchFieldNameSort) {
        this.searchFieldNameSort = searchFieldNameSort;
    }

    public String getSearchFieldReleaseOrCreateDate() {
        return searchFieldReleaseOrCreateDate;
    }

    public String getASCENDING() {
        return ASCENDING;
    }

    public String getDESCENDING() {
        return DESCENDING;
    }

    public String getSortField() {
        return sortField;
    }

    public void setSortField(String sortField) {
        this.sortField = sortField;
    }

    public String getSortOrder() {
        if (sortOrder != null) {
            return sortOrder.toString();
        } else {
            return null;
        }
    }

    /**
     * Allow only valid values to be set.
     * <p>
     * Rather than passing in a String and converting it to an enum in this
     * method we could write a converter:
     * http://stackoverflow.com/questions/8609378/jsf-2-0-view-parameters-to-pass-objects
     */
    public void setSortOrder(String sortOrderSupplied) {
        if (sortOrderSupplied != null) {
            if (sortOrderSupplied.equals(SortOrder.asc.toString())) {
                this.sortOrder = SortOrder.asc;
            }
            if (sortOrderSupplied.equals(SortOrder.desc.toString())) {
                this.sortOrder = SortOrder.desc;
            }
        }
    }

    /**
     * @todo this method doesn't seem to be in use and can probably be deleted.
     */
    @Deprecated
    public String getCurrentSortFriendly() {
        String friendlySortField = sortField;
        String friendlySortOrder = sortOrder.toString();
        if (sortField.equals(SearchFields.NAME_SORT)) {
            friendlySortField = "Name";
            if (sortOrder.equals(ASCENDING)) {
                friendlySortOrder = " (A-Z)";
            } else if (sortOrder.equals(DESCENDING)) {
                friendlySortOrder = " (Z-A)";
            }
        } else if (sortField.equals(SearchFields.RELEVANCE)) {
            friendlySortField = "Relevance";
            friendlySortOrder = "";
        }
        return friendlySortField + friendlySortOrder;
    }

    public String getCurrentSort() {
        return sortField + ":" + sortOrder;
    }

    public boolean isSortedByNameAsc() {
        return getCurrentSort().equals(searchFieldNameSort + ":" + ASCENDING);
    }

    public boolean isSortedByNameDesc() {
        return getCurrentSort().equals(searchFieldNameSort + ":" + DESCENDING);
    }

    public boolean isSortedByReleaseDateAsc() {
        return getCurrentSort().equals(searchFieldReleaseOrCreateDate + ":" + ASCENDING);
    }

    public boolean isSortedByReleaseDateDesc() {
        return getCurrentSort().equals(searchFieldReleaseOrCreateDate + ":" + DESCENDING);
    }

    public boolean isSortedByRelevance() {
        return getCurrentSort().equals(searchFieldRelevance + ":" + DESCENDING);
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    // helper method
    public int getTotalPages() {
        return ((searchResultsCount - 1) / paginationGuiRows) + 1;
    }

    public int getPaginationGuiStart() {
        return paginationGuiStart;
    }

    public void setPaginationGuiStart(int paginationGuiStart) {
        this.paginationGuiStart = paginationGuiStart;
    }

    public int getPaginationGuiEnd() {
        return paginationGuiEnd;
    }

    public void setPaginationGuiEnd(int paginationGuiEnd) {
        this.paginationGuiEnd = paginationGuiEnd;
    }

    public int getPaginationGuiRows() {
        return paginationGuiRows;
    }

    public void setPaginationGuiRows(int paginationGuiRows) {
        this.paginationGuiRows = paginationGuiRows;
    }

    public boolean isSolrIsDown() {
        return solrIsDown;
    }

    public void setSolrIsDown(boolean solrIsDown) {
        this.solrIsDown = solrIsDown;
    }

    public boolean isRootDv() {
        return rootDv;
    }

    public void setRootDv(boolean rootDv) {
        this.rootDv = rootDv;
    }

    public boolean isDebug() {
        return (debug && session.getUser().isSuperuser())
                || settingsService.isTrue(":Debug");
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public List<String> getFilterQueriesDebug() {
        return filterQueriesDebug;
    }

    public boolean userLoggedIn() {
        return session.getUser().isAuthenticated();
    }

    public boolean publishedSelected() {
        String expected = SearchFields.PUBLICATION_STATUS + ":\"" + getPUBLISHED() + "\"";
        logger.fine("published expected: " + expected + " actual: " + selectedTypesList);
        return filterQueries.contains(SearchFields.PUBLICATION_STATUS + ":\"" + getPUBLISHED() + "\"");
    }

    public boolean unpublishedSelected() {
        String expected = SearchFields.PUBLICATION_STATUS + ":\"" + getUNPUBLISHED() + "\"";
        logger.fine("unpublished expected: " + expected + " actual: " + selectedTypesList);
        return filterQueries.contains(SearchFields.PUBLICATION_STATUS + ":\"" + getUNPUBLISHED() + "\"");
    }

    public String getPUBLISHED() {
        return IndexServiceBean.getPUBLISHED_STRING();
    }

    public String getUNPUBLISHED() {
        return IndexServiceBean.getUNPUBLISHED_STRING();
    }

    public String getDRAFT() {
        return IndexServiceBean.getDRAFT_STRING();
    }

    public String getIN_REVIEW() {
        return IndexServiceBean.getIN_REVIEW_STRING();
    }

    public String getDEACCESSIONED() {
        return IndexServiceBean.getDEACCESSIONED_STRING();
    }


    /**
     * A bit of redundant effort for error checking in the .xhtml
     * <p>
     * Specifically for searches with bad facets in query string--
     * incorrect quoting.  These searches don't always throw an explicit
     * solr error.
     * <p>
     * Note: An empty or null filterQuery array is OK
     * Values within the array that can't be split are NOT ok
     * (This is quick "downstream" fix--not necessarily efficient)
     *
     * @return
     */
    public boolean hasValidFilterQueries() {

        if (this.filterQueries.isEmpty()) {
            return true;        // empty is valid!
        }

        for (String fq : this.filterQueries) {
            if (this.getFriendlyNamesFromFilterQuery(fq) == null) {
                return false;   // not parseable is bad!
            }
        }
        return true;
    }

    public List<String> getFriendlyNamesFromFilterQuery(String filterQuery) {


        if ((filterQuery == null) ||
                (datasetfieldFriendlyNamesBySolrField == null) ||
                (staticSolrFieldFriendlyNamesBySolrField == null)) {
            return null;
        }

        String[] parts = filterQuery.split(":");
        if (parts.length != 2) {
            //logger.log(Level.INFO, "String array has {0} part(s).  Should have 2: {1}", new Object[]{parts.length, filterQuery});
            return null;
        }
        String key = parts[0];
        String value = parts[1];

        List<String> friendlyNames = new ArrayList<>();

        String datasetfieldFriendyName = datasetfieldFriendlyNamesBySolrField.get(key);
        if (datasetfieldFriendyName != null) {
            friendlyNames.add(datasetfieldFriendyName);
        } else {
            String nonDatasetSolrField = staticSolrFieldFriendlyNamesBySolrField.get(key);
            if (nonDatasetSolrField != null) {
                friendlyNames.add(nonDatasetSolrField);
            } else if (key.equals(SearchFields.PUBLICATION_STATUS)) {
                /**
                 * @todo Refactor this quick fix for
                 * https://github.com/IQSS/dataverse/issues/618 . We really need
                 * to get rid of all the reflection that's happening with
                 * solrQueryResponse.getStaticSolrFieldFriendlyNamesBySolrField()
                 * and
                 */
                friendlyNames.add("Publication Status");
            } else {
                // meh. better than nuthin'
                friendlyNames.add(key);
            }
        }
        String noLeadingQuote = value.replaceAll("^\"", "");
        String noTrailingQuote = noLeadingQuote.replaceAll("\"$", "");
        String valueWithoutQuotes = noTrailingQuote;
        friendlyNames.add(valueWithoutQuotes);
        return friendlyNames;
    }

    public String getNewSelectedTypes(String typeClicked) {
        List<String> newTypesSelected = new ArrayList<>();
        for (String selectedType : selectedTypesList) {
            if (selectedType.equals(typeClicked)) {

            } else {
                newTypesSelected.add(selectedType);
            }

        }
        if (selectedTypesList.contains(typeClicked)) {

        } else {
            newTypesSelected.add(typeClicked);
        }

        String[] arr = newTypesSelected.toArray(new String[newTypesSelected.size()]);
        return combine(arr, ":");

    }

    public String getErrorFromSolr() {
        return errorFromSolr;
    }

    public boolean isTabular(DataFile datafile) {

        if (datafile == null) {
            return false;
        }

        return datafile.isTabularData();
    }

    public String tabularDataDisplayInfo(DataFile datafile) {
        String ret = "";

        if (datafile == null) {
            return null;
        }

        if (datafile.isTabularData() && datafile.getDataTable() != null) {
            DataTable datatable = datafile.getDataTable();
            String unf = datatable.getUnf();
            Long varNumber = datatable.getVarQuantity();
            Long obsNumber = datatable.getCaseQuantity();
            if (varNumber != null && varNumber.intValue() != 0) {
                ret = ret.concat(varNumber + " Variables");
                if (obsNumber != null && obsNumber.intValue() != 0) {
                    ret = ret.concat(", " + obsNumber + " Observations");
                }
                ret = ret.concat(" - ");
            }
            if (unf != null && !unf.equals("")) {
                ret = ret.concat("UNF: " + unf);
            }
        }

        return ret;
    }

    public String dataFileSizeDisplay(DataFile datafile) {
        if (datafile == null) {
            return "";
        }

        return datafile.getFriendlySize();

    }

    public String dataFileChecksumDisplay(DataFile datafile) {
        if (datafile == null) {
            return "";
        }

        if (datafile.getChecksumValue() != null && !StringUtils.isEmpty(datafile.getChecksumValue())) {
            if (datafile.getChecksumType() != null) {
                return " " + datafile.getChecksumType() + ": " + datafile.getChecksumValue() + " ";
            }
        }

        return "";
    }

    public void setDisplayCardValues() {

        Set<Long> harvestedDatasetIds = null;
        for (SolrSearchResult result : searchResultsList) {
            //logger.info("checking DisplayImage for the search result " + i++);
            if (result.getType().equals("dataverses")) {
                /**
                 * @todo Someday we should probably revert this setImageUrl to
                 * the original meaning "image_url" to address this issue:
                 * `image_url` from Search API results no longer yields a
                 * downloadable image -
                 * https://github.com/IQSS/dataverse/issues/3616
                 */
                result.setImageUrl(thumbnailServiceWrapper.getDataverseCardImageAsBase64Url(result));
            } else if (result.getType().equals("datasets")) {
                if (result.getEntity() != null) {
                    result.setImageUrl(thumbnailServiceWrapper.getDatasetCardImageAsBase64Url(result));
                }

                if (result.isHarvested()) {
                    if (harvestedDatasetIds == null) {
                        harvestedDatasetIds = new HashSet<>();
                    }
                    harvestedDatasetIds.add(result.getEntityId());
                }
            } else if (result.getType().equals("files")) {
                result.setImageUrl(thumbnailServiceWrapper.getFileCardImageAsBase64Url(result));
                if (result.isHarvested()) {
                    if (harvestedDatasetIds == null) {
                        harvestedDatasetIds = new HashSet<>();
                    }
                    harvestedDatasetIds.add(result.getParentIdAsLong());
                }
            }
        }

        thumbnailServiceWrapper.resetObjectMaps();

        // Now, make another pass, and add the remote archive descriptions to the
        // harvested dataset and datafile cards (at the expense of one extra
        // SQL query:

        if (harvestedDatasetIds != null) {
            Map<Long, String> descriptionsForHarvestedDatasets = datasetService.getArchiveDescriptionsForHarvestedDatasets(harvestedDatasetIds);
            if (descriptionsForHarvestedDatasets != null && descriptionsForHarvestedDatasets.size() > 0) {
                for (SolrSearchResult result : searchResultsList) {
                    if (result.isHarvested()) {
                        if (result.getType().equals("files")) {
                            if (descriptionsForHarvestedDatasets.containsKey(result.getParentIdAsLong())) {
                                result.setHarvestingDescription(descriptionsForHarvestedDatasets.get(result.getParentIdAsLong()));
                            }
                        } else if (result.getType().equals("datasets")) {
                            if (descriptionsForHarvestedDatasets.containsKey(result.getEntityId())) {
                                result.setHarvestingDescription(descriptionsForHarvestedDatasets.get(result.getEntityId()));
                            }
                        }
                    }
                }
            }
            descriptionsForHarvestedDatasets = null;
            harvestedDatasetIds = null;
        }

        // determine which of the objects are linked:

        if (!this.isRootDv()) {
            // (nothing is "linked" if it's the root DV!)
            Set<Long> dvObjectParentIds = new HashSet<>();
            for (SolrSearchResult result : searchResultsList) {
                if (dataverse.getId().equals(result.getParentIdAsLong())) {
                    // definitely NOT linked:
                    result.setIsInTree(true);
                } else if (result.getParentIdAsLong() == dataverseService.findRootDataverse().getId()) {
                    // the object's parent is the root Dv; and the current
                    // Dv is NOT root... definitely linked:
                    result.setIsInTree(false);
                } else {
                    dvObjectParentIds.add(result.getParentIdAsLong());
                }
            }

            if (dvObjectParentIds.size() > 0) {
                Map<Long, String> treePathMap = dvObjectService.getObjectPathsByIds(dvObjectParentIds);
                if (treePathMap != null) {
                    for (SolrSearchResult result : searchResultsList) {
                        Long objectId = result.getParentIdAsLong();
                        if (treePathMap.containsKey(objectId)) {
                            String objectPath = treePathMap.get(objectId);
                            if (!objectPath.startsWith(dataversePath)) {
                                result.setIsInTree(false);
                            }
                        }
                    }
                }
                treePathMap = null;
            }

            dvObjectParentIds = null;
        }

    }

    public enum SortOrder {

        asc, desc
    }

    public String searchRedirect(String dataverseRedirectPage) {
        dataverseRedirectPage = StringUtils.isBlank(dataverseRedirectPage) ? "/dataverseuser.xhtml?selectTab=dataRelatedToMe" : dataverseRedirectPage;

        String qParam = "";
        if (query != null) {
            qParam = "&q=" + query;
        }

        return widgetWrapper.wrapURL(dataverseRedirectPage + "?faces-redirect=true&q=" + qParam);

    }


    public String retrieveMyData() {

        // wildcard/browse (*) unless user supplies a query
        if (this.query == null) {
            mode = browseModeString;
        } else if (this.query.isEmpty()) {
            mode = browseModeString;
        } else {
            mode = searchModeString;
        }
        String searchTerm = "*";
        if (mode.equals(browseModeString)) {
            searchTerm = "*";
            if (sortField == null) {
                sortField = searchFieldReleaseOrCreateDate;
            }
            if (sortOrder == null) {
                sortOrder = MyDataSearchFragment.SortOrder.desc;
            }
            if (selectedTypesString == null || selectedTypesString.isEmpty()) {
                selectedTypesString = "dataverses:datasets";
            }
        } else if (mode.equals(searchModeString)) {
            searchTerm = query;
            if (sortField == null) {
                sortField = searchFieldRelevance;
            }
            if (sortOrder == null) {
                sortOrder = MyDataSearchFragment.SortOrder.desc;
            }
            if (selectedTypesString == null || selectedTypesString.isEmpty()) {
                selectedTypesString = "dataverses:datasets:files";
            }
        }

        filterQueries = new ArrayList<>();
        for (String fq : Arrays.asList(fq0, fq1, fq2, fq3, fq4, fq5, fq6, fq7, fq8, fq9)) {
            if (fq != null) {
                filterQueries.add(fq);
            }
        }

        roleFilters = new ArrayList<>();
        for (String rf : Arrays.asList(rf0, rf1, rf2, rf3, rf4, rf5)) {
            if (rf != null) {
                roleFilters.add(rf);
            }
        }


        // For, superusers, the searchUser may differ from the authUser
        //
        AuthenticatedUser searchUser = null;

        if ((session.getUser() != null) && (session.getUser().isAuthenticated())) {
            authUser = (AuthenticatedUser) session.getUser();

            // If person is a superuser, see if a userIdentifier has been specified
            // and use that instead
            if ((authUser.isSuperuser()) && (userIdentifier != null) && (!userIdentifier.isEmpty())) {
                searchUser = getUserFromIdentifier(userIdentifier);
                if (searchUser != null) {
                    authUser = searchUser;
                } else {
                    return "No user found for: \"" + userIdentifier + "\"";
                }
            }
        }

        roleList = dataverseRoleService.findAll();
        rolePermissionHelper = new DataverseRolePermissionHelper(roleList);

        List<String> pub_states = new ArrayList<>();
        for(String filter : filterQueries) {
            if(filter.contains(SearchFields.PUBLICATION_STATUS)) {
                pub_states.add(filter.split(":")[1].replace("\"",""));
            }
        }
        if(pub_states.isEmpty()) {
            pub_states = MyDataFilterParams.defaultPublishedStates;
        }


        // ---------------------------------
        // (1) Initialize filterParams and check for Errors
        // ---------------------------------
        DataverseRequest dataverseRequest = createDataverseRequest(authUser);

        List<String> filterQueriesFinal = new ArrayList<>();
        selectedTypesList = new ArrayList<>();
        String[] parts = selectedTypesString.split(":");
        selectedTypesList.addAll(Arrays.asList(parts));

        List<String> filterQueriesFinalAllTypes = new ArrayList<>();
        String[] arr = selectedTypesList.toArray(new String[selectedTypesList.size()]);
        selectedTypesHumanReadable = combine(arr, " OR ");
        if (!selectedTypesHumanReadable.isEmpty()) {
            typeFilterQuery = SearchFields.TYPE + ":(" + selectedTypesHumanReadable + ")";
        }

        List<Long> rolesIds = rolePermissionHelper.getRoleIdList();
        MyDataFilterParams filterParams = new MyDataFilterParams(dataverseRequest, toMyDataFinderFormat(selectedTypesList), pub_states, rolesIds, searchTerm);
        if (filterParams.hasError()) {
            return filterParams.getErrorMessage() + filterParams.getErrorMessage();
        }

        // ---------------------------------
        // (2) Initialize MyDataFinder and check for Errors
        // ---------------------------------
        myDataFinder = new MyDataFinder(rolePermissionHelper,
                roleAssigneeSvc,
                dvObjectServiceBean,
                groupService);
        this.myDataFinder.runFindDataSteps(filterParams);
        if (myDataFinder.hasError()) {
            return myDataFinder.getErrorMessage() + myDataFinder.getErrorMessage();
        }
        List<String> filterQueries = this.myDataFinder.getSolrFilterQueries();
        if (filterQueries == null) {
            logger.fine("No ids found for this search");
            return DataRetrieverAPI.MSG_NO_RESULTS_FOUND;
        }
        for(String filter : filterQueries) {
            if (filter.contains(SearchFields.PUBLICATION_STATUS) && pub_states.size() != MyDataFilterParams.defaultPublishedStates.size()) {
                filterQueries.add(filter.replace("OR", "AND"));
                filterQueries.remove(filter);
            }
        }

        filterQueriesFinal.addAll(filterQueries);
        filterQueriesFinalAllTypes.addAll(filterQueriesFinal);
        filterQueriesFinal.add(typeFilterQuery);
        String allTypesFilterQuery = SearchFields.TYPE + ":(dataverses OR datasets OR files)";
        filterQueriesFinalAllTypes.add(allTypesFilterQuery);


        // ---------------------------------
        // (3) Make Solr Query
        // ---------------------------------
        int paginationStart = (page - 1) * paginationGuiRows;

        // Default the searchUser to the authUser.
        // The exception: for logged-in superusers, the searchUser may differ from the authUser
        //
        if (searchUser == null) {
            searchUser = authUser;
        }

        try {
            solrQueryResponse = searchService.search(
                    dataverseRequest,
                    null,
                    searchTerm,
                    filterQueriesFinal,
                    SearchFields.RELEASE_OR_CREATE_DATE,
                    SortBy.DESCENDING,
                    paginationStart,
                    true,
                    SearchConstants.NUM_SOLR_DOCS_TO_RETRIEVE * 100
            );

            if (this.solrQueryResponse.getNumResultsFound() == 0) {
                this.solrIsDown = true;
                this.errorFromSolr = solrQueryResponse.getError();
                return DataRetrieverAPI.MSG_NO_RESULTS_FOUND;
            }

        } catch (SearchException ex) {
            solrQueryResponse = null;
            logger.severe("Solr SearchException: " + ex.getMessage());
        }

        if (solrQueryResponse == null) {
            return "Sorry!  There was an error with the search service." +  " Sorry!  There was a SOLR Error";
        }
        if (!solrIsDown) {
            roleTagRetriever = new RoleTagRetriever(this.rolePermissionHelper, this.roleAssigneeSvc, this.dvObjectServiceBean);
            roleTagRetriever.loadRoles(dataverseRequest, solrQueryResponse);

            List<String> roles = new ArrayList<>();
            for(SolrSearchResult dvObjId : solrQueryResponse.getSolrSearchResults()) {
                roles.addAll(roleTagRetriever.getRolesForCard(dvObjId.getEntityId()));
            }
            myRoles = roles.stream().distinct().collect(Collectors.toList());

            List<Long> entitiesFilteredByRole = new ArrayList<>();
            if(!roleFilters.isEmpty()) {
                solrQueryResponse = solrQueryResponse.rebuildForRolesFilters(roleFilters, roleTagRetriever);
            }

            for(FacetCategory facetCat : solrQueryResponse.getFacetCategoryList()) {
                if(facetCat.getName().equals("publicationStatus")) {
                    this.facetCategoryList.add(facetCat);
                    break;
                }
            }
            this.searchResultsList = solrQueryResponse.getSolrSearchResults();
            this.searchResultsCount = solrQueryResponse.getNumResultsFound().intValue();
            this.datasetfieldFriendlyNamesBySolrField = solrQueryResponse.getDatasetfieldFriendlyNamesBySolrField();
            this.staticSolrFieldFriendlyNamesBySolrField = solrQueryResponse.getStaticSolrFieldFriendlyNamesBySolrField();
            this.filterQueriesDebug = solrQueryResponse.getFilterQueriesActual();
            this.errorFromSolr = solrQueryResponse.getError();
            paginationGuiStart = paginationStart + 1;
            paginationGuiEnd = Math.min(page * paginationGuiRows, searchResultsCount);
            List<SolrSearchResult> searchResults = solrQueryResponse.getSolrSearchResults();

            // populate preview counts: https://redmine.hmdc.harvard.edu/issues/3560
            previewCountbyType.put("dataverses", 0L);
            previewCountbyType.put("datasets", 0L);
            previewCountbyType.put("files", 0L);
            if (solrQueryResponse != null) {
                for (FacetCategory facetCategory : solrQueryResponse.getTypeFacetCategories()) {
                    for (FacetLabel facetLabel : facetCategory.getFacetLabel()) {
                        previewCountbyType.put(facetLabel.getName(), facetLabel.getCount());
                    }
                }
            }

            /**
             * @todo consider creating Java objects called DatasetCard,
             * DatasetCart, and FileCard since that's what we call them in the
             * UI. These objects' fields (affiliation, citation, etc.) would be
             * populated from Solr if possible (for performance, to avoid extra
             * database calls) or by a database call (if it's tricky or doesn't
             * make sense to get the data in and out of Solr). We would continue
             * to iterate through all the SolrSearchResult objects as we build
             * up the new card objects. Think about how we have a
             * solrSearchResult.setCitation method but only the dataset card in
             * the UI (currently) shows this "citation" field.
             */
            for (SolrSearchResult solrSearchResult : searchResults) {
                if (solrSearchResult.getEntityId() == null) {
                    // avoiding EJBException a la https://redmine.hmdc.harvard.edu/issues/3809
                    logger.warning(SearchFields.ENTITY_ID + " was null for Solr document id:" + solrSearchResult.getId() + ", skipping. Bad Solr data?");
                    break;
                }

                // going to assume that this is NOT a linked object, for now:
                solrSearchResult.setIsInTree(true);
                // (we'll review this later!)

                if (solrSearchResult.getType().equals("dataverses")) {
                    dataverseService.populateDvSearchCard(solrSearchResult);

                } else if (solrSearchResult.getType().equals("datasets")) {
                    //logger.info("XXRESULT: dataset: "+solrSearchResult.getEntityId());
                    datasetVersionService.populateDatasetSearchCard(solrSearchResult);

                    String deaccesssionReason = solrSearchResult.getDeaccessionReason();
                    if (deaccesssionReason != null) {
                        solrSearchResult.setDescriptionNoSnippet(deaccesssionReason);
                    }

                } else if (solrSearchResult.getType().equals("files")) {
                    dataFileService.populateFileSearchCard(solrSearchResult);

                    /**
                     * @todo: show DataTable variables
                     */
                }
            }
        }

        return StringUtils.EMPTY;
    }

    private List<String> toMyDataFinderFormat(List<String> selectedTypesList) {
        List<String> myDataFinderTypes = new ArrayList<>();
        for(String type : selectedTypesList) {
            if(type.equals("dataverses")) {
                myDataFinderTypes.add(DvObject.DATAVERSE_DTYPE_STRING);
            }
            if(type.equals("datasets")) {
                myDataFinderTypes.add(DvObject.DATASET_DTYPE_STRING);
            }
            if(type.equals("files")) {
                myDataFinderTypes.add(DvObject.DATAFILE_DTYPE_STRING);
            }
        }
        return myDataFinderTypes;
    }

    public boolean isSuperuser() {
        return (session.getUser() != null) && session.getUser().isSuperuser();
    }

    private AuthenticatedUser getUserFromIdentifier(String userIdentifier) {

        if ((userIdentifier == null) || (userIdentifier.isEmpty())) {
            return null;
        }
        return authenticationService.getAuthenticatedUser(userIdentifier);
    }

}
