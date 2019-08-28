/**
 * @todo Shouldn't this be in the "edu.harvard.iq.dataverse.api" package? Is the only one that isn't.
 */
package edu.harvard.iq.dataverse.mydata;

import edu.harvard.iq.dataverse.DataverseRoleServiceBean;
import edu.harvard.iq.dataverse.DataverseServiceBean;
import edu.harvard.iq.dataverse.DataverseSession;
import edu.harvard.iq.dataverse.DvObjectServiceBean;
import edu.harvard.iq.dataverse.RoleAssigneeServiceBean;
import edu.harvard.iq.dataverse.api.AbstractApiBean;
import edu.harvard.iq.dataverse.authorization.AuthenticationServiceBean;
import edu.harvard.iq.dataverse.authorization.DataverseRolePermissionHelper;
import edu.harvard.iq.dataverse.authorization.groups.GroupServiceBean;
import edu.harvard.iq.dataverse.common.BundleUtil;
import edu.harvard.iq.dataverse.engine.command.DataverseRequest;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.persistence.user.DataverseRole;
import edu.harvard.iq.dataverse.search.SearchConstants;
import edu.harvard.iq.dataverse.search.SearchException;
import edu.harvard.iq.dataverse.search.SearchFields;
import edu.harvard.iq.dataverse.search.SearchServiceBean;
import edu.harvard.iq.dataverse.search.SolrQueryResponse;
import edu.harvard.iq.dataverse.search.SolrSearchResult;
import edu.harvard.iq.dataverse.search.SortBy;
import org.apache.commons.lang.StringUtils;

import javax.ejb.EJB;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;



@ViewScoped
@Named("MyDataRetriever")
public class MyDataRetriever extends AbstractApiBean implements Serializable {

    private static final Logger logger = Logger.getLogger(DataRetrieverAPI.class.getCanonicalName());

    @Inject
    DataverseSession session;

    @EJB
    DataverseRoleServiceBean dataverseRoleService;
    @EJB
    RoleAssigneeServiceBean roleAssigneeService;
    @EJB
    DvObjectServiceBean dvObjectServiceBean;
    @EJB
    SearchServiceBean searchService;
    @EJB
    AuthenticationServiceBean authenticationService;
    @EJB
    DataverseServiceBean dataverseService;
    @EJB
    GroupServiceBean groupService;

    private List<DataverseRole> roleList;
    private DataverseRolePermissionHelper rolePermissionHelper;
    private List<String> defaultDvObjectTypes = MyDataFilterParams.defaultDvObjectTypes;
    private MyDataFinder myDataFinder;
    private SolrQueryResponse solrQueryResponse;
    private AuthenticatedUser authUser = null;

    private boolean solrIsDown = false;

    public static final String JSON_SUCCESS_FIELD_NAME = "success";
    public static final String JSON_ERROR_MSG_FIELD_NAME = "error_message";
    public static final String JSON_DATA_FIELD_NAME = "data";
    /**
     * Constructor
     */
    public MyDataRetriever() {

    }

    public boolean isSolrIsDown() {
        return solrIsDown;
    }

    public Long getDvObjectsCount(String objType) {
        return solrQueryResponse.getDvObjectCounts().get(objType);
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


    private String getJSONErrorString(String jsonMsg, String optionalLoggerMsg) {

        if (jsonMsg == null) {
            throw new NullPointerException("jsonMsg cannot be null");
        }
        if (optionalLoggerMsg != null) {
            logger.severe(optionalLoggerMsg);
        }
        JsonObjectBuilder jsonData = Json.createObjectBuilder();

        jsonData.add(DataRetrieverAPI.JSON_SUCCESS_FIELD_NAME, false);
        jsonData.add(DataRetrieverAPI.JSON_ERROR_MSG_FIELD_NAME, jsonMsg);

        return jsonData.build().toString();

    }

    public String retrieveMyData(List<String> dvobject_types,
                                             List<String> published_states,
                                             Integer selectedPage,
                                             String searchTerm,
                                             List<Long> roleIds,
                                             String userIdentifier,
                                             String apiToken) {

        boolean DEBUG_MODE = false;
        boolean OTHER_USER = false;


        // For, superusers, the searchUser may differ from the authUser
        //
        AuthenticatedUser searchUser = null;

        if (DEBUG_MODE == true) {      // DEBUG: use userIdentifier
            authUser = getUserFromIdentifier(userIdentifier);
            if (authUser == null) {
                return this.getJSONErrorString("Requires authentication", "retrieveMyDataAsJsonString. User not found!  Shouldn't be using this anyway");
            }
        } else if ((session.getUser() != null) && (session.getUser().isAuthenticated())) {
            authUser = (AuthenticatedUser) session.getUser();

            // If person is a superuser, see if a userIdentifier has been specified
            // and use that instead
            if ((authUser.isSuperuser()) && (userIdentifier != null) && (!userIdentifier.isEmpty())) {
                searchUser = getUserFromIdentifier(userIdentifier);
                if (searchUser != null) {
                    authUser = searchUser;
                    OTHER_USER = true;
                } else {
                    return "No user found for: \"" + userIdentifier + "\"";
                }
            }
        } else if (apiToken != null) {      // Is this being accessed by an API Token?

            authUser = findUserByApiToken(apiToken);
            if (authUser == null) {
                return this.getJSONErrorString("Requires authentication.  Please login.", "retrieveMyDataAsJsonString. User not found!  Shouldn't be using this anyway");
            } else {
                // If person is a superuser, see if a userIdentifier has been specified 
                // and use that instead
                if ((authUser.isSuperuser()) && (userIdentifier != null) && (!userIdentifier.isEmpty())) {
                    searchUser = getUserFromIdentifier(userIdentifier);
                    if (searchUser != null) {
                        authUser = searchUser;
                        OTHER_USER = true;
                    } else {
                        return this.getJSONErrorString("No user found for: \"" + userIdentifier + "\"", null);
                    }
                }

            }

        } else {
            return this.getJSONErrorString("Requires authentication.  Please login.", "retrieveMyDataAsJsonString. User not found!  Shouldn't be using this anyway");
        }

        roleList = dataverseRoleService.findAll();
        rolePermissionHelper = new DataverseRolePermissionHelper(roleList);


        List<String> dtypes;
        if (dvobject_types != null) {
            dtypes = dvobject_types;
        } else {
            dtypes = MyDataFilterParams.defaultDvObjectTypes;
        }
        List<String> pub_states = null;
        if (published_states != null) {
            pub_states = published_states;
        }

        // ---------------------------------
        // (1) Initialize filterParams and check for Errors 
        // ---------------------------------
        DataverseRequest dataverseRequest = createDataverseRequest(authUser);

        List<Long> rolesIds = rolePermissionHelper.getRoleIdList();
        MyDataFilterParams filterParams = new MyDataFilterParams(dataverseRequest, dtypes, pub_states, rolesIds, searchTerm);
        if (filterParams.hasError()) {
            return this.getJSONErrorString(filterParams.getErrorMessage(), filterParams.getErrorMessage());
        }

        // ---------------------------------
        // (2) Initialize MyDataFinder and check for Errors 
        // ---------------------------------
        myDataFinder = new MyDataFinder(rolePermissionHelper,
                roleAssigneeService,
                dvObjectServiceBean,
                groupService);
        this.myDataFinder.runFindDataSteps(filterParams);
        if (myDataFinder.hasError()) {
            return this.getJSONErrorString(myDataFinder.getErrorMessage(), myDataFinder.getErrorMessage());
        }

        // ---------------------------------
        // (3) Make Solr Query
        // ---------------------------------
        int paginationStart = 1;
        if (selectedPage != null) {
            paginationStart = selectedPage;
        }
        int solrCardStart = (paginationStart - 1) * SearchConstants.NUM_SOLR_DOCS_TO_RETRIEVE;

        // Default the searchUser to the authUser.
        // The exception: for logged-in superusers, the searchUser may differ from the authUser
        //
        if (searchUser == null) {
            searchUser = authUser;
        }


        List<String> filterQueries = this.myDataFinder.getSolrFilterQueries();
        if (filterQueries == null) {
            logger.fine("No ids found for this search");
            return this.getJSONErrorString(DataRetrieverAPI.MSG_NO_RESULTS_FOUND, null);
        }

        try {
            solrQueryResponse = searchService.search(
                    dataverseRequest,
                    null,
                    filterParams.getSearchTerm(),
                    filterQueries,
                    SearchFields.RELEASE_OR_CREATE_DATE, SortBy.DESCENDING,
                    solrCardStart,
                    true,
                    SearchConstants.NUM_SOLR_DOCS_TO_RETRIEVE
            );

            if (this.solrQueryResponse.getNumResultsFound() == 0) {
                this.solrIsDown = true;
                return this.getJSONErrorString(DataRetrieverAPI.MSG_NO_RESULTS_FOUND, null);
            }

        } catch (SearchException ex) {
            solrQueryResponse = null;
            logger.severe("Solr SearchException: " + ex.getMessage());
        }

        if (solrQueryResponse == null) {
            return this.getJSONErrorString("Sorry!  There was an error with the search service.", "Sorry!  There was a SOLR Error");
        }


        Pager pager = new Pager(solrQueryResponse.getNumResultsFound().intValue(),
                SearchConstants.NUM_SOLR_DOCS_TO_RETRIEVE,
                paginationStart);

        RoleTagRetriever roleTagRetriever = new RoleTagRetriever(this.rolePermissionHelper, this.roleAssigneeSvc, this.dvObjectServiceBean);
        roleTagRetriever.loadRoles(dataverseRequest, solrQueryResponse);

        return StringUtils.EMPTY;
    }
}        