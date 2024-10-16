package edu.harvard.iq.dataverse.api;

import edu.harvard.iq.dataverse.DataFileServiceBean;
import edu.harvard.iq.dataverse.DatasetDao;
import edu.harvard.iq.dataverse.DatasetFieldServiceBean;
import edu.harvard.iq.dataverse.DataverseDao;
import edu.harvard.iq.dataverse.DataverseRoleServiceBean;
import edu.harvard.iq.dataverse.DvObjectServiceBean;
import edu.harvard.iq.dataverse.api.annotations.ApiWriteOperation;
import edu.harvard.iq.dataverse.common.NullSafeJsonBuilder;
import edu.harvard.iq.dataverse.dataset.datasetversion.DatasetVersionServiceBean;
import edu.harvard.iq.dataverse.dataset.datasetversion.DatasetVersionServiceBean.RetrieveDatasetVersionResponse;
import edu.harvard.iq.dataverse.notification.NotificationObjectResolver;
import edu.harvard.iq.dataverse.notification.NotificationObjectSearchLabelVisitor;
import edu.harvard.iq.dataverse.persistence.DvObject;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.datafile.FileMetadata;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetFieldType;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.persistence.user.GuestUser;
import edu.harvard.iq.dataverse.persistence.user.RoleAssignment;
import edu.harvard.iq.dataverse.persistence.user.User;
import edu.harvard.iq.dataverse.persistence.user.UserNotification;
import edu.harvard.iq.dataverse.persistence.user.UserNotificationQuery;
import edu.harvard.iq.dataverse.persistence.user.UserNotificationQueryResult;
import edu.harvard.iq.dataverse.persistence.user.UserNotificationRepository;
import edu.harvard.iq.dataverse.search.FileView;
import edu.harvard.iq.dataverse.search.SearchException;
import edu.harvard.iq.dataverse.search.SearchFields;
import edu.harvard.iq.dataverse.search.SearchFilesServiceBean;
import edu.harvard.iq.dataverse.search.SearchServiceBean;
import edu.harvard.iq.dataverse.search.SearchServiceBean.SortOrder;
import edu.harvard.iq.dataverse.search.SearchUtil;
import edu.harvard.iq.dataverse.search.SolrField;
import edu.harvard.iq.dataverse.search.index.IndexBatchServiceBean;
import edu.harvard.iq.dataverse.search.index.IndexResponse;
import edu.harvard.iq.dataverse.search.index.IndexServiceBean;
import edu.harvard.iq.dataverse.search.index.PermissionsSolrDoc;
import edu.harvard.iq.dataverse.search.index.PermissionsSolrDocFactory;
import edu.harvard.iq.dataverse.search.index.SolrIndexServiceBean;
import edu.harvard.iq.dataverse.search.query.SearchForTypes;
import edu.harvard.iq.dataverse.search.response.FacetCategory;
import edu.harvard.iq.dataverse.search.response.SolrQueryResponse;
import edu.harvard.iq.dataverse.search.response.SolrSearchResult;
import edu.harvard.iq.dataverse.util.FileSortFieldAndOrder;
import org.apache.solr.client.solrj.SolrServerException;

import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import static edu.harvard.iq.dataverse.common.NullSafeJsonBuilder.jsonObjectBuilder;

@Path("admin/index")
public class Index extends AbstractApiBean {

    private static final Logger logger = Logger.getLogger(Index.class.getCanonicalName());

    @EJB
    IndexServiceBean indexService;
    @EJB
    IndexBatchServiceBean indexAllService;
    @EJB
    SolrIndexServiceBean solrIndexService;
    @EJB
    DataverseDao dataverseDao;
    @EJB
    DatasetDao datasetDao;
    @EJB
    DatasetVersionServiceBean datasetVersionService;
    @EJB
    DataFileServiceBean dataFileService;
    @EJB
    DvObjectServiceBean dvObjectService;
    @EJB
    SolrIndexServiceBean SolrIndexService;
    @EJB
    SearchServiceBean searchService;
    @EJB
    DatasetFieldServiceBean datasetFieldService;
    @EJB
    SearchFilesServiceBean searchFilesService;
    @EJB
    private PermissionsSolrDocFactory solrDocFactory;
    @Inject
    private DataverseRoleServiceBean rolesSvc;
    @Inject
    private NotificationObjectResolver notificationObjectResolver;
    @Inject
    private UserNotificationRepository userNotificationRepository;

    public static String contentChanged = "contentChanged";
    public static String contentIndexed = "contentIndexed";
    public static String permsChanged = "permsChanged";
    public static String permsIndexed = "permsIndexed";

    @GET
    public Response indexAllOrSubset(@QueryParam("numPartitions") Long numPartitionsSelected, @QueryParam("partitionIdToProcess") Long partitionIdToProcess, @QueryParam("previewOnly") boolean previewOnly) {
        return indexAllOrSubset(numPartitionsSelected, partitionIdToProcess, false, previewOnly);
    }

    @GET
    @Path("continue")
    public Response indexAllOrSubsetContinue(@QueryParam("numPartitions") Long numPartitionsSelected, @QueryParam("partitionIdToProcess") Long partitionIdToProcess, @QueryParam("previewOnly") boolean previewOnly) {
        return indexAllOrSubset(numPartitionsSelected, partitionIdToProcess, true, previewOnly);
    }

    private Response indexAllOrSubset(Long numPartitionsSelected, Long partitionIdToProcess, boolean skipIndexed, boolean previewOnly) {
        try {
            long numPartitions = 1;
            if (numPartitionsSelected != null) {
                if (numPartitionsSelected < 1) {
                    return error(Status.BAD_REQUEST, "numPartitions must be 1 or higher but was " + numPartitionsSelected);
                } else {
                    numPartitions = numPartitionsSelected;
                }
            }
            List<Long> availablePartitionIds = new ArrayList<>();
            for (long i = 0; i < numPartitions; i++) {
                availablePartitionIds.add(i);
            }

            Response invalidParitionIdSelection = error(Status.BAD_REQUEST, "You specified " + numPartitions + " partition(s) and your selected partitionId was " + partitionIdToProcess + " but you must select from these availableParitionIds: " + availablePartitionIds);
            if (partitionIdToProcess != null) {
                long selected = partitionIdToProcess;
                if (!availablePartitionIds.contains(selected)) {
                    return invalidParitionIdSelection;
                }
            } else if (numPartitionsSelected == null) {
                /**
                 * The user has not specified a partitionId and hasn't specified
                 * the number of partitions. Run "index all", the whole thing.
                 */
                partitionIdToProcess = 0l;
            } else {
                return invalidParitionIdSelection;

            }

            JsonObjectBuilder args = Json.createObjectBuilder();
            args.add("numPartitions", numPartitions);
            args.add("partitionIdToProcess", partitionIdToProcess);
            JsonArrayBuilder availablePartitionIdsBuilder = Json.createArrayBuilder();
            for (long i : availablePartitionIds) {
                availablePartitionIdsBuilder.add(i);
            }

            JsonObjectBuilder preview = indexAllService.indexAllOrSubsetPreview(numPartitions, partitionIdToProcess, skipIndexed);
            if (previewOnly) {
                preview.add("args", args);
                preview.add("availablePartitionIds", availablePartitionIdsBuilder);
                return ok(preview);
            }

            JsonObjectBuilder response = Json.createObjectBuilder();
            response.add("availablePartitionIds", availablePartitionIdsBuilder);
            response.add("args", args);
            /**
             * @todo How can we expose the String returned from "index all" via
             * the API?
             */
            Future<JsonObjectBuilder> indexAllFuture = indexAllService.indexAllOrSubsetAsync(numPartitions, partitionIdToProcess, skipIndexed);
            JsonObject workloadPreview = preview.build().getJsonObject("previewOfPartitionWorkload");
            int dataverseCount = workloadPreview.getInt("dataverseCount");
            int datasetCount = workloadPreview.getInt("datasetCount");
            String status = "indexAllOrSubset has begun of " + dataverseCount + " dataverses and " + datasetCount + " datasets.";
            response.add("message", status);
            return ok(response);
        } catch (EJBException ex) {
            Throwable cause = ex;
            StringBuilder sb = new StringBuilder();
            sb.append(ex + " ");
            while (cause.getCause() != null) {
                cause = cause.getCause();
                sb.append(cause.getClass().getCanonicalName() + " ");
                sb.append(cause.getMessage()).append(" ");
                if (cause instanceof ConstraintViolationException) {
                    ConstraintViolationException constraintViolationException = (ConstraintViolationException) cause;
                    for (ConstraintViolation<?> violation : constraintViolationException.getConstraintViolations()) {
                        sb.append("(invalid value: <<<").append(violation.getInvalidValue()).append(">>> for ").append(violation.getPropertyPath()).append(" at ").append(violation.getLeafBean()).append(" - ").append(violation.getMessage()).append(")");
                    }
                } else if (cause instanceof NullPointerException) {
                    for (int i = 0; i < 2; i++) {
                        StackTraceElement stacktrace = cause.getStackTrace()[i];
                        if (stacktrace != null) {
                            String classCanonicalName = stacktrace.getClass().getCanonicalName();
                            String methodName = stacktrace.getMethodName();
                            int lineNumber = stacktrace.getLineNumber();
                            String error = "at " + stacktrace.getClassName() + "." + stacktrace.getMethodName() + "(" + stacktrace.getFileName() + ":" + lineNumber + ") ";
                            sb.append(error);
                        }
                    }
                }
            }
            if (sb.toString().equals("javax.ejb.EJBException: Transaction aborted javax.transaction.RollbackException java.lang.IllegalStateException ")) {
                return ok("indexing went as well as can be expected... got java.lang.IllegalStateException but some indexing may have happened anyway");
            } else {
                return error(Status.INTERNAL_SERVER_ERROR, sb.toString());
            }
        }
    }

    @GET
    @Path("clear")
    public Response clearSolrIndex() {
        try {
            JsonObjectBuilder response = SolrIndexService.deleteAllFromSolrAndResetIndexTimes();
            return ok(response);
        } catch (SolrServerException | IOException ex) {
            return error(Status.INTERNAL_SERVER_ERROR, ex.getLocalizedMessage());
        }
    }

    @GET
    @Path("{type}/{id}")
    public Response indexTypeById(@PathParam("type") String type, @PathParam("id") Long id) {
        try {
            if (type.equals("dataverses")) {
                Dataverse dataverse = dataverseDao.find(id);
                if (dataverse != null) {
                    /**
                     * @todo Can we display the result of indexing to the user?
                     */
                    Future<String> indexDataverseFuture = indexService.indexDataverse(dataverse);
                    return ok("starting reindex of dataverse " + id);
                } else {
                    String response = indexService.removeSolrDocFromIndex(IndexServiceBean.solrDocIdentifierDataverse + id);
                    return notFound("Could not find dataverse with id of " + id + ". Result from deletion attempt: " + response);
                }
            } else if (type.equals("datasets")) {
                Dataset dataset = datasetDao.find(id);
                if (dataset != null) {
                    boolean doNormalSolrDocCleanUp = true;
                    Future<String> indexDatasetFuture = indexService.indexDataset(dataset, doNormalSolrDocCleanUp);
                    return ok("starting reindex of dataset " + id);
                } else {
                    /**
                     * @todo what about published, deaccessioned, etc.? Need
                     * method to target those, not just drafts!
                     */
                    String response = indexService.removeSolrDocFromIndex(IndexServiceBean.solrDocIdentifierDataset + id + IndexServiceBean.draftSuffix);
                    return notFound("Could not find dataset with id of " + id + ". Result from deletion attempt: " + response);
                }
            } else if (type.equals("files")) {
                DataFile dataFile = dataFileService.find(id);
                Dataset datasetThatOwnsTheFile = datasetDao.find(dataFile.getOwner().getId());
                /**
                 * @todo How can we display the result to the user?
                 */
                boolean doNormalSolrDocCleanUp = true;
                Future<String> indexDatasetFuture = indexService.indexDataset(datasetThatOwnsTheFile, doNormalSolrDocCleanUp);
                return ok("started reindexing " + type + "/" + id);
            } else {
                return error(Status.BAD_REQUEST, "illegal type: " + type);
            }
        } catch (EJBException ex) {
            Throwable cause = ex;
            StringBuilder sb = new StringBuilder();
            sb.append("Problem indexing ").append(type).append("/").append(id).append(": ");
            sb.append(ex).append(" ");
            while (cause.getCause() != null) {
                cause = cause.getCause();
                sb.append(cause.getClass().getCanonicalName()).append(" ");
                sb.append(cause.getMessage()).append(" ");
                if (cause instanceof ConstraintViolationException) {
                    ConstraintViolationException constraintViolationException = (ConstraintViolationException) cause;
                    for (ConstraintViolation<?> violation : constraintViolationException.getConstraintViolations()) {
                        sb.append("(invalid value: <<<").append(violation.getInvalidValue()).append(">>> for ").append(violation.getPropertyPath()).append(" at ").append(violation.getLeafBean()).append(" - ").append(violation.getMessage()).append(")");
                    }
                } else if (cause instanceof NullPointerException) {
                    for (int i = 0; i < 2; i++) {
                        StackTraceElement stacktrace = cause.getStackTrace()[i];
                        if (stacktrace != null) {
                            String classCanonicalName = stacktrace.getClass().getCanonicalName();
                            String methodName = stacktrace.getMethodName();
                            int lineNumber = stacktrace.getLineNumber();
                            String error = "at " + stacktrace.getClassName() + "." + stacktrace.getMethodName() + "(" + stacktrace.getFileName() + ":" + lineNumber + ") ";
                            sb.append(error);
                        }
                    }
                }
            }
            return error(Status.INTERNAL_SERVER_ERROR, sb.toString());
        }
    }

    @GET
    @Path("dataset")
    public Response indexDatasetByPersistentId(@QueryParam("persistentId") String persistentId) {
        if (persistentId == null) {
            return error(Status.BAD_REQUEST, "No persistent id given.");
        }
        Dataset dataset = null;
        try {
            dataset = datasetDao.findByGlobalId(persistentId);
        } catch (Exception ex) {
            return error(Status.BAD_REQUEST, "Problem looking up dataset with persistent id \"" + persistentId + "\". Error: " + ex.getMessage());
        }
        if (dataset != null) {
            boolean doNormalSolrDocCleanUp = true;
            Future<String> indexDatasetFuture = indexService.indexDataset(dataset, doNormalSolrDocCleanUp);
            JsonObjectBuilder data = Json.createObjectBuilder();
            data.add("message", "Reindexed dataset " + persistentId);
            data.add("id", dataset.getId());
            data.add("persistentId", dataset.getGlobalIdString());
            JsonArrayBuilder versions = Json.createArrayBuilder();
            for (DatasetVersion version : dataset.getVersions()) {
                JsonObjectBuilder versionObject = Json.createObjectBuilder();
                versionObject.add("semanticVersion", version.getSemanticVersion());
                versionObject.add("id", version.getId());
                versions.add(versionObject);
            }
            data.add("versions", versions);
            return ok(data);
        } else {
            return error(Status.BAD_REQUEST, "Could not find dataset with persistent id " + persistentId);
        }
    }

    @GET
    @Path("perms")
    public Response indexAllPermissions() {
        IndexResponse indexResponse = solrIndexService.indexAllPermissions();
        return ok(indexResponse.getMessage());
    }

    @GET
    @Path("perms/{id}")
    public Response indexPermissions(@PathParam("id") Long id) {
        DvObject dvObject = dvObjectService.findDvObject(id);
        if (dvObject == null) {
            return error(Status.BAD_REQUEST, "Could not find DvObject based on id " + id);
        } else {
            IndexResponse indexResponse = solrIndexService.indexPermissionsForOneDvObject(dvObject);
            return ok(indexResponse.getMessage());
        }
    }

    @GET
    @Path("status")
    public Response indexStatus() {
        JsonObjectBuilder contentInDatabaseButStaleInOrMissingFromSolr = getContentInDatabaseButStaleInOrMissingFromSolr();

        JsonObjectBuilder contentInSolrButNotDatabase;
        try {
            contentInSolrButNotDatabase = getContentInSolrButNotDatabase();
        } catch (SearchException ex) {
            return error(Response.Status.INTERNAL_SERVER_ERROR, "Can not determine index status. " + ex.getLocalizedMessage() + ". Is Solr down? Exception: " + ex.getCause().getLocalizedMessage());
        }

        JsonObjectBuilder permissionsInDatabaseButStaleInOrMissingFromSolr = getPermissionsInDatabaseButStaleInOrMissingFromSolr();

        JsonObjectBuilder data = Json.createObjectBuilder()
                .add("contentInDatabaseButStaleInOrMissingFromIndex", contentInDatabaseButStaleInOrMissingFromSolr)
                .add("contentInIndexButNotDatabase", contentInSolrButNotDatabase)
                .add("permissionsInDatabaseButStaleInOrMissingFromIndex", permissionsInDatabaseButStaleInOrMissingFromSolr);

        return ok(data);
    }

    private JsonObjectBuilder getContentInDatabaseButStaleInOrMissingFromSolr() {
        List<Dataverse> stateOrMissingDataverses = indexService.findStaleOrMissingDataverses();
        List<Dataset> staleOrMissingDatasets = indexService.findStaleOrMissingDatasets();
        JsonArrayBuilder jsonStateOrMissingDataverses = Json.createArrayBuilder();
        for (Dataverse dataverse : stateOrMissingDataverses) {
            jsonStateOrMissingDataverses.add(dataverse.getId());
        }
        JsonArrayBuilder datasetsInDatabaseButNotSolr = Json.createArrayBuilder();
        for (Dataset dataset : staleOrMissingDatasets) {
            datasetsInDatabaseButNotSolr.add(dataset.getId());
        }
        JsonObjectBuilder contentInDatabaseButStaleInOrMissingFromSolr = Json.createObjectBuilder()
                /**
                 * @todo What about files? Currently files are always indexed
                 * along with their parent dataset
                 */
                .add("dataverses", jsonStateOrMissingDataverses.build().size())
                .add("datasets", datasetsInDatabaseButNotSolr.build().size());
        return contentInDatabaseButStaleInOrMissingFromSolr;
    }

    private JsonObjectBuilder getContentInSolrButNotDatabase() throws SearchException {
        List<Long> dataversesInSolrOnly = indexService.findDataversesInSolrOnly();
        List<Long> datasetsInSolrOnly = indexService.findDatasetsInSolrOnly();
        List<Long> filesInSolrOnly = indexService.findFilesInSolrOnly();
        JsonArrayBuilder dataversesInSolrButNotDatabase = Json.createArrayBuilder();
        for (Long dataverseId : dataversesInSolrOnly) {
            dataversesInSolrButNotDatabase.add(dataverseId);
        }
        JsonArrayBuilder datasetsInSolrButNotDatabase = Json.createArrayBuilder();
        for (Long datasetId : datasetsInSolrOnly) {
            datasetsInSolrButNotDatabase.add(datasetId);
        }
        JsonArrayBuilder filesInSolrButNotDatabase = Json.createArrayBuilder();
        for (Long fileId : filesInSolrOnly) {
            filesInSolrButNotDatabase.add(fileId);
        }
        JsonObjectBuilder contentInSolrButNotDatabase = Json.createObjectBuilder()
                /**
                 * @todo What about files? Currently files are always indexed
                 * along with their parent dataset
                 */
                .add("dataverses", dataversesInSolrButNotDatabase.build().size())
                .add("datasets", datasetsInSolrButNotDatabase.build().size())
                .add("files", filesInSolrButNotDatabase.build().size());
        return contentInSolrButNotDatabase;
    }

    private JsonObjectBuilder getPermissionsInDatabaseButStaleInOrMissingFromSolr() {
        List<Long> staleOrMissingPermissions;
        staleOrMissingPermissions = solrIndexService.findPermissionsInDatabaseButStaleInOrMissingFromSolr();
        JsonArrayBuilder stalePermissionList = Json.createArrayBuilder();
        for (Long dvObjectId : staleOrMissingPermissions) {
            stalePermissionList.add(dvObjectId);
        }
        return Json.createObjectBuilder()
                .add("dvobjects", stalePermissionList.build().size());
    }

    /**
     * We use the output of this method to generate our Solr schema.xml
     *
     * @todo Someday we do want to have this return a Response rather than a
     * String per https://github.com/IQSS/dataverse/issues/298 but not yet while
     * we are trying to ship Dataverse 4.0.
     */
    @GET
    @Path("solr/schema")
    public String getSolrSchema() {

        StringBuilder sb = new StringBuilder();

        for (DatasetFieldType datasetFieldType : datasetFieldService.findAllOrderedByName()) {
            SolrField dsfSolrField = SolrField.of(datasetFieldType);
            String nameSearchable = dsfSolrField.getNameSearchable();

            SolrField.SolrType solrType = dsfSolrField.getSolrType();
            String type = solrType.getType();
            if (solrType.equals(SolrField.SolrType.EMAIL)) {
                /**
                 * @todo should we also remove all "email" field types (e.g.
                 * datasetContact) from schema.xml? We are explicitly not
                 * indexing them for
                 * https://github.com/IQSS/dataverse/issues/759
                 *
                 * "The list of potential collaborators should be searchable"
                 * according to https://github.com/IQSS/dataverse/issues/747 but
                 * it's not clear yet if this means a Solr or database search.
                 * For now we'll keep schema.xml as it is to avoid people having
                 * to update it. If anything, we can remove the email field type
                 * when we do a big schema.xml update for
                 * https://github.com/IQSS/dataverse/issues/754
                 */
                logger.info("email type detected (" + nameSearchable + ") See also https://github.com/IQSS/dataverse/issues/759");
            }
            String multivalued = dsfSolrField.isAllowedToBeMultivalued().toString();
            // <field name="datasetId" type="text_general" multiValued="false" stored="true" indexed="true"/>
            sb.append("    <field name=\"" + nameSearchable + "\" type=\"" + type + "\" multiValued=\"" + multivalued + "\" stored=\"true\" indexed=\"true\"/>\n");
        }

        List<String> listOfStaticFields = new ArrayList<>();
        Object searchFieldsObject = new SearchFields();
        Field[] staticSearchFields = searchFieldsObject.getClass().getDeclaredFields();
        for (Field fieldObject : staticSearchFields) {
            String name = fieldObject.getName();
            String staticSearchField = null;
            try {
                staticSearchField = (String) fieldObject.get(searchFieldsObject);
            } catch (IllegalArgumentException | IllegalAccessException ex) {
                logger.log(Level.WARNING, "Exception encountered", ex);
            }

            /**
             * @todo: if you search for "pdf" should you get all pdfs? do we
             * need a copyField source="filetypemime_s" to the catchall?
             */
            if (listOfStaticFields.contains(staticSearchField)) {
                return error("static search field defined twice: " + staticSearchField);
            }
            listOfStaticFields.add(staticSearchField);
        }

        sb.append("---\n");

        for (DatasetFieldType datasetFieldType : datasetFieldService.findAllOrderedByName()) {
            SolrField dsfSolrField = SolrField.of(datasetFieldType);
            String nameSearchable = dsfSolrField.getNameSearchable();

            String nameFacetable = dsfSolrField.getNameFacetable();

            if (listOfStaticFields.contains(nameSearchable)) {
                if (nameSearchable.equals(SearchFields.DATASET_DESCRIPTION)) {
                    // Skip, expected conflct.
                } else {
                    return error("searchable dataset metadata field conflict detected with static field: " + nameSearchable);
                }
            }

            if (listOfStaticFields.contains(nameFacetable)) {
                if (nameFacetable.equals(SearchFields.SUBJECT)) {
                    // Skip, expected conflct.
                } else {
                    return error("facetable dataset metadata field conflict detected with static field: " + nameFacetable);
                }
            }

            // <copyField source="*_i" dest="_text_" maxChars="3000"/>
            sb.append("    <copyField source=\"").append(nameSearchable).append("\" dest=\"" + SearchFields.FULL_TEXT + "\" maxChars=\"3000\"/>\n");
        }

        return sb.toString();
    }

    static String error(String message) {
        JsonObjectBuilder response = Json.createObjectBuilder();
        response.add("status", "ERROR");
        response.add("message", message);

        return "{\n\t\"status\":\"ERROR\"\n\t\"message\":\"" + message.replaceAll("\"", "\\\\\"").replaceAll("\n", "\\\\n") + "\"\n}";
    }

    /**
     * This method is for integration tests of search.
     */
    @GET
    @Path("test")
    public Response searchDebug(
            @QueryParam("key") String apiToken,
            @QueryParam("q") String query,
            @QueryParam("fq") final List<String> filterQueries) {

        User user = findUserByApiToken(apiToken);
        if (user == null) {
            return error(Response.Status.UNAUTHORIZED, "Invalid apikey ");
        }

        Dataverse subtreeScope = dataverseDao.findRootDataverse();

        String sortField = SearchFields.ID;
        int paginationStart = 0;
        int numResultsPerPage = Integer.MAX_VALUE;
        SolrQueryResponse solrQueryResponse;
        List<Dataverse> dataverses = new ArrayList<>();
        dataverses.add(subtreeScope);
        try {
            solrQueryResponse = searchService.search(createDataverseRequest(user), dataverses, query, SearchForTypes.all(), filterQueries,
                    sortField, SortOrder.asc, paginationStart, numResultsPerPage, false);
        } catch (SearchException ex) {
            return error(Response.Status.INTERNAL_SERVER_ERROR, ex.getLocalizedMessage() + ": " + ex.getCause().getLocalizedMessage());
        }

        JsonArrayBuilder itemsArrayBuilder = Json.createArrayBuilder();
        List<SolrSearchResult> solrSearchResults = solrQueryResponse.getSolrSearchResults();
        for (SolrSearchResult solrSearchResult : solrSearchResults) {
            itemsArrayBuilder.add(solrSearchResult.getType().getSolrValue() + ":" + solrSearchResult.getNameSort());
        }

        return ok(itemsArrayBuilder);
    }

    /**
     * This method is for integration tests of search.
     */
    @GET
    @Path("permsDebug")
    public Response searchPermsDebug(
            @QueryParam("key") String apiToken,
            @QueryParam("id") Long dvObjectId) {

        User user = findUserByApiToken(apiToken);
        if (user == null) {
            return error(Response.Status.UNAUTHORIZED, "Invalid apikey");
        }

        DvObject dvObjectToLookUp = dvObjectService.findDvObject(dvObjectId);
        if (dvObjectToLookUp == null) {
            return error(Status.BAD_REQUEST, "Could not find DvObject based on id " + dvObjectId);
        }
        List<PermissionsSolrDoc> solrDocs = solrDocFactory.determinePermissionsDocsOnSelfOnly(dvObjectToLookUp);

        JsonObjectBuilder data = Json.createObjectBuilder();

        JsonArrayBuilder permissionsData = Json.createArrayBuilder();

        for (PermissionsSolrDoc solrDoc : solrDocs) {
            JsonObjectBuilder dataDoc = Json.createObjectBuilder();
            dataDoc.add(SearchFields.ID, solrDoc.getSolrId());
            dataDoc.add(SearchFields.NAME_SORT, solrDoc.getNameOrTitle());
            JsonArrayBuilder perms = Json.createArrayBuilder();
            for (String perm : solrDoc.getSearchPermissions().getPermissions()) {
                perms.add(perm);
            }
            dataDoc.add(SearchFields.DISCOVERABLE_BY, perms);
            dataDoc.add(SearchFields.DISCOVERABLE_BY_PUBLIC_FROM, solrDoc.getSearchPermissions().getPublicFrom().toString());
            permissionsData.add(dataDoc);
        }
        data.add("perms", permissionsData);

        DvObject dvObject = dvObjectService.findDvObject(dvObjectId);
        NullSafeJsonBuilder timestamps = jsonObjectBuilder();
        timestamps.add(contentChanged, SearchUtil.getTimestampOrNull(dvObject.getModificationTime()));
        timestamps.add(contentIndexed, SearchUtil.getTimestampOrNull(dvObject.getIndexTime()));
        timestamps.add(permsChanged, SearchUtil.getTimestampOrNull(dvObject.getPermissionModificationTime()));
        timestamps.add(permsIndexed, SearchUtil.getTimestampOrNull(dvObject.getPermissionIndexTime()));
        Set<RoleAssignment> roleAssignments = rolesSvc.rolesAssignments(dvObject);
        JsonArrayBuilder roleAssignmentsData = Json.createArrayBuilder();
        for (RoleAssignment roleAssignment : roleAssignments) {
            roleAssignmentsData.add(roleAssignment.getRole() + " has been granted to " + roleAssignment.getAssigneeIdentifier() + " on " + roleAssignment.getDefinitionPoint());
        }
        data.add("timestamps", timestamps);
        data.add("roleAssignments", roleAssignmentsData);

        return ok(data);
    }

    @DELETE
    @ApiWriteOperation
    @Path("timestamps")
    public Response deleteAllTimestamps() {
        int numItemsCleared = dvObjectService.clearAllIndexTimes();
        return ok("cleared: " + numItemsCleared);
    }

    @DELETE
    @ApiWriteOperation
    @Path("timestamps/{dvObjectId}")
    public Response deleteTimestamp(@PathParam("dvObjectId") long dvObjectId) {
        int numItemsCleared = dvObjectService.clearIndexTimes(dvObjectId);
        return ok("cleared: " + numItemsCleared);
    }

    @GET
    @Path("filesearch")
    public Response filesearch(@QueryParam("persistentId") String persistentId, @QueryParam("semanticVersion") String semanticVersion, @QueryParam("q") String userSuppliedQuery) {
        Dataset dataset = datasetDao.findByGlobalId(persistentId);
        if (dataset == null) {
            return error(Status.BAD_REQUEST, "Could not find dataset with persistent id " + persistentId);
        }
        User user = GuestUser.get();
        try {
            AuthenticatedUser authenticatedUser = findAuthenticatedUserOrDie();
            if (authenticatedUser != null) {
                user = authenticatedUser;
            }
        } catch (WrappedResponse ex) {
        }
        RetrieveDatasetVersionResponse datasetVersionResponse = datasetVersionService.retrieveDatasetVersionByPersistentId(persistentId, semanticVersion);
        if (datasetVersionResponse == null) {
            return error(Status.BAD_REQUEST, "Problem searching for files. Could not find dataset version based on " + persistentId + " and " + semanticVersion);
        }
        DatasetVersion datasetVersion = datasetVersionResponse.getDatasetVersion();
        FileView fileView = searchFilesService.getFileView(datasetVersion, user, userSuppliedQuery);
        if (fileView == null) {
            return error(Status.BAD_REQUEST, "Problem searching for files. Null returned from getFileView.");
        }
        JsonArrayBuilder filesFound = Json.createArrayBuilder();
        JsonArrayBuilder cards = Json.createArrayBuilder();
        JsonArrayBuilder fileIds = Json.createArrayBuilder();
        for (SolrSearchResult result : fileView.getSolrSearchResults()) {
            cards.add(result.getNameSort());
            fileIds.add(result.getEntityId());
            JsonObjectBuilder fileFound = Json.createObjectBuilder();
            fileFound.add("name", result.getNameSort());
            fileFound.add("entityId", result.getEntityId().toString());
            fileFound.add("datasetVersionId", result.getDatasetVersionId());
            fileFound.add("datasetId", result.getParent().getId());
            filesFound.add(fileFound);
        }
        JsonArrayBuilder facets = Json.createArrayBuilder();
        for (FacetCategory facetCategory : fileView.getFacetCategoryList()) {
            facets.add(facetCategory.getFriendlyName());
        }
        JsonArrayBuilder filterQueries = Json.createArrayBuilder();
        for (String filterQuery : fileView.getFilterQueries()) {
            filterQueries.add(filterQuery);
        }
        JsonArrayBuilder allDatasetVersionIds = Json.createArrayBuilder();
        for (DatasetVersion dsVersion : dataset.getVersions()) {
            allDatasetVersionIds.add(dsVersion.getId());
        }
        JsonObjectBuilder data = Json.createObjectBuilder();
        data.add("filesFound", filesFound);
        data.add("cards", cards);
        data.add("fileIds", fileIds);
        data.add("facets", facets);
        data.add("user", user.getIdentifier());
        data.add("persistentID", persistentId);
        data.add("query", fileView.getQuery());
        data.add("filterQueries", filterQueries);
        data.add("allDataverVersionIds", allDatasetVersionIds);
        data.add("semanticVersion", datasetVersion.getSemanticVersion());
        return ok(data);
    }

    @GET
    @Path("filemetadata/{dataset_id}")
    public Response getFileMetadataByDatasetId(
            @PathParam("dataset_id") long datasetIdToLookUp,
            @QueryParam("maxResults") int maxResults,
            @QueryParam("sort") String sortField,
            @QueryParam("order") SortOrder sortOrder
    ) {
        JsonArrayBuilder data = Json.createArrayBuilder();
        List<FileMetadata> fileMetadatasFound = new ArrayList<>();
        try {
            fileMetadatasFound = dataFileService.findFileMetadataByDatasetVersionId(datasetIdToLookUp, maxResults, new FileSortFieldAndOrder(sortField, sortOrder));
        } catch (Exception ex) {
            return error(Status.BAD_REQUEST, "error: " + ex.getCause().getMessage() + ex);
        }
        for (FileMetadata fileMetadata : fileMetadatasFound) {
            data.add(fileMetadata.getLabel());
        }
        return ok(data);
    }

    @GET
    @Path("usernotifications")
    public Response indexUserNotifications(@QueryParam("page") @DefaultValue("1") Integer page,
                                           @QueryParam("pageSize") @DefaultValue("100") Integer pageSize) {
        try {
            int offset = (page - 1) * pageSize;
            UserNotificationQueryResult toIndex = userNotificationRepository.query(UserNotificationQuery.newQuery()
                    .withOffset(offset)
                    .withResultLimit(pageSize));
            if (toIndex.getResult().isEmpty()) {
                return notFound("No notifications to index.");
            }

            for (UserNotification notification : toIndex.getResult()) {
                notificationObjectResolver.resolve(NotificationObjectSearchLabelVisitor.onNotification(notification));
                userNotificationRepository.saveFlushAndClear(notification);
            }
            return ok("Processed " + toIndex.getResult().size() + " of " + toIndex.getTotalCount()
                    + " notification(s). firstId:" + toIndex.getResult().get(0).getId()
                    + " lastId:" + toIndex.getResult().get(toIndex.getResult().size() - 1).getId());
        } catch (Exception ex) {
            return error(Status.BAD_REQUEST, "error: " + ex.getCause().getMessage() + ex);
        }
    }
}
