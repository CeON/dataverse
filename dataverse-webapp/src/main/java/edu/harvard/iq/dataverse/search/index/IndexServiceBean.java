package edu.harvard.iq.dataverse.search.index;

import edu.harvard.iq.dataverse.DatasetDao;
import edu.harvard.iq.dataverse.DatasetLinkingServiceBean;
import edu.harvard.iq.dataverse.DataverseDao;
import edu.harvard.iq.dataverse.DvObjectServiceBean;
import edu.harvard.iq.dataverse.citation.CitationFactory;
import edu.harvard.iq.dataverse.common.DatasetFieldConstant;
import edu.harvard.iq.dataverse.dataaccess.DataAccess;
import edu.harvard.iq.dataverse.dataaccess.StorageIO;
import edu.harvard.iq.dataverse.dataverse.DataverseLinkingService;
import edu.harvard.iq.dataverse.persistence.DvObject;
import edu.harvard.iq.dataverse.persistence.GlobalId;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.datafile.DataFileCategory;
import edu.harvard.iq.dataverse.persistence.datafile.DataFileTag;
import edu.harvard.iq.dataverse.persistence.datafile.FileMetadata;
import edu.harvard.iq.dataverse.persistence.datafile.datavariable.DataVariable;
import edu.harvard.iq.dataverse.persistence.datafile.license.FileTermsOfUse;
import edu.harvard.iq.dataverse.persistence.datafile.license.FileTermsOfUse.TermsOfUseType;
import edu.harvard.iq.dataverse.persistence.dataset.ControlledVocabularyValue;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetField;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetFieldType;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.dataset.FieldType;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.dataverse.link.DatasetLinkingDataverse;
import edu.harvard.iq.dataverse.persistence.harvest.HarvestingClient;
import edu.harvard.iq.dataverse.search.SearchConstants;
import edu.harvard.iq.dataverse.search.SearchException;
import edu.harvard.iq.dataverse.search.SearchFields;
import edu.harvard.iq.dataverse.search.SolrField;
import edu.harvard.iq.dataverse.search.index.geobox.GeoboxIndexUtil;
import edu.harvard.iq.dataverse.search.query.SearchObjectType;
import edu.harvard.iq.dataverse.search.query.SearchPublicationStatus;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import edu.harvard.iq.dataverse.util.FileUtil;
import edu.harvard.iq.dataverse.util.StringUtil;
import edu.harvard.iq.dataverse.util.SystemConfig;
import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.SolrInputField;
import org.apache.tika.io.IOUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

import javax.ejb.AsyncResult;
import javax.ejb.Asynchronous;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static javax.ejb.TransactionAttributeType.REQUIRES_NEW;

@Stateless
public class IndexServiceBean {

    private static final Logger logger = Logger.getLogger(IndexServiceBean.class.getCanonicalName());

    public static final String solrDocIdentifierDataverse = "dataverse_";
    public static final String solrDocIdentifierFile = "datafile_";
    public static final String solrDocIdentifierDataset = "dataset_";
    public static final String draftSuffix = "_draft";
    public static final String deaccessionedSuffix = "_deaccessioned";
    public static final String discoverabilityPermissionSuffix = "_permission";
    private static final String groupPrefix = "group_";
    private static final String groupPerUserPrefix = "group_user";
    public static final String HARVESTED = "Harvested";

    private DvObjectServiceBean dvObjectService;
    private DataverseDao dataverseDao;
    private DatasetDao datasetDao;
    private SystemConfig systemConfig;
    private SolrIndexServiceBean solrIndexService;
    private DatasetLinkingServiceBean dsLinkingService;
    private DataverseLinkingService dvLinkingService;
    private SettingsServiceBean settingsService;
    private SolrClient solrServer;
    private CitationFactory citationFactory;

    private DataAccess dataAccess = DataAccess.dataAccess();
    private GeoboxIndexUtil geoboxIndexUtil = new GeoboxIndexUtil();

    // -------------------- CONSTRUCTORS --------------------

    public IndexServiceBean() { }

    @Inject
    public IndexServiceBean(DvObjectServiceBean dvObjectService, DataverseDao dataverseDao,
                            DatasetDao datasetDao, SystemConfig systemConfig,
                            SolrIndexServiceBean solrIndexService, DatasetLinkingServiceBean dsLinkingService,
                            DataverseLinkingService dvLinkingService, SettingsServiceBean settingsService,
                            SolrClient solrServer, CitationFactory citationFactory) {
        this.dvObjectService = dvObjectService;
        this.dataverseDao = dataverseDao;
        this.datasetDao = datasetDao;
        this.systemConfig = systemConfig;
        this.solrIndexService = solrIndexService;
        this.dsLinkingService = dsLinkingService;
        this.dvLinkingService = dvLinkingService;
        this.settingsService = settingsService;
        this.solrServer = solrServer;
        this.citationFactory = citationFactory;
    }

    // -------------------- LOGIC --------------------

    public Future<String> indexDvObject(DvObject objectIn){

        if (objectIn.isInstanceofDataset() ){
            return (indexDataset((Dataset)objectIn, true));
        }
        if (objectIn.isInstanceofDataverse() ){
            return (indexDataverse((Dataverse)objectIn));
        }
        return null;
    }

    @TransactionAttribute(REQUIRES_NEW)
    public Future<String> indexDataverseInNewTransaction(Dataverse dataverse) {
        return indexDataverse(dataverse);
    }

    public Future<String> indexDataverse(Dataverse dataverse) {
        logger.fine("indexDataverse called on dataverse id " + dataverse.getId() + "(" + dataverse.getAlias() + ")");
        if (dataverse.getId() == null) {
            String msg = "unable to index dataverse. id was null (alias: " + dataverse.getAlias() + ")";
            logger.info(msg);
            return new AsyncResult<>(msg);
        }

        Dataverse rootDataverse = dataverseDao.findRootDataverse();

        Collection<SolrInputDocument> docs = new ArrayList<>();
        SolrInputDocument solrInputDocument = new SolrInputDocument();
        solrInputDocument.addField(SearchFields.ID, solrDocIdentifierDataverse + dataverse.getId());
        solrInputDocument.addField(SearchFields.ENTITY_ID, dataverse.getId());
        solrInputDocument.addField(SearchFields.DATAVERSE_VERSION_INDEXED_BY, systemConfig.getVersionWithBuild());
        solrInputDocument.addField(SearchFields.IDENTIFIER, dataverse.getAlias());
        solrInputDocument.addField(SearchFields.TYPE, SearchObjectType.DATAVERSES.getSolrValue());
        solrInputDocument.addField(SearchFields.NAME, dataverse.getName());
        solrInputDocument.addField(SearchFields.NAME_SORT, dataverse.getName());
        solrInputDocument.addField(SearchFields.DATAVERSE_NAME, dataverse.getName());
        solrInputDocument.addField(SearchFields.DATAVERSE_ALIAS, dataverse.getAlias());
        solrInputDocument.addField(SearchFields.DATAVERSE_CATEGORY, dataverse.getIndexableCategoryName());
        if (dataverse.isReleased()) {
            solrInputDocument.addField(SearchFields.PUBLICATION_STATUS, SearchPublicationStatus.PUBLISHED.getSolrValue());
            solrInputDocument.addField(SearchFields.RELEASE_OR_CREATE_DATE, dataverse.getPublicationDate());
            solrInputDocument.addField(SearchFields.RELEASE_OR_CREATE_DATE_SEARCHABLE_TEXT, convertToFriendlyDate(dataverse.getPublicationDate()));
        } else {
            solrInputDocument.addField(SearchFields.PUBLICATION_STATUS, SearchPublicationStatus.UNPUBLISHED.getSolrValue());
            solrInputDocument.addField(SearchFields.RELEASE_OR_CREATE_DATE, dataverse.getCreateDate());
            solrInputDocument.addField(SearchFields.RELEASE_OR_CREATE_DATE_SEARCHABLE_TEXT, convertToFriendlyDate(dataverse.getCreateDate()));
        }
        // We don't really have harvested dataverses yet;
        solrInputDocument.addField(SearchFields.IS_HARVESTED, false);
        solrInputDocument.addField(SearchFields.METADATA_SOURCE, rootDataverse.getName());

        addDataverseReleaseDateToSolrDoc(solrInputDocument, dataverse);

        solrInputDocument.addField(SearchFields.DESCRIPTION, StringUtil.html2text(dataverse.getDescription()));
        solrInputDocument.addField(SearchFields.DATAVERSE_DESCRIPTION, StringUtil.html2text(dataverse.getDescription()));
        solrInputDocument.addField(SearchFields.DATAVERSE_EXTRA_DESCRIPTION, dataverse.getAdditionalDescription());

        if (StringUtils.isNotEmpty(dataverse.getAffiliation())) {
            solrInputDocument.addField(SearchFields.AFFILIATION, dataverse.getAffiliation());
            solrInputDocument.addField(SearchFields.DATAVERSE_AFFILIATION, dataverse.getAffiliation());
        }
        for (ControlledVocabularyValue dataverseSubject : dataverse.getDataverseSubjects()) {
            String subject = dataverseSubject.getStrValue();
            if (!subject.equals(DatasetField.NA_VALUE)) {
                solrInputDocument.addField(SearchFields.DATAVERSE_SUBJECT, subject);
                // collapse into shared "subject" field used as a facet
                solrInputDocument.addField(SearchFields.SUBJECT, subject);
            }
        }

        if (dataverse.getOwner() != null) {
            solrInputDocument.addField(SearchFields.PARENT_ID, dataverse.getOwner().getId());
            solrInputDocument.addField(SearchFields.PARENT_NAME, dataverse.getOwner().getName());
        }
        List<String> dataverseSegments = findPathSegments(dataverse);
        List<String> dataversePaths = getDataversePathsFromSegments(dataverseSegments);
        if (dataversePaths.size() > 0) {
            // don't show yourself while indexing or in search results:
            // https://redmine.hmdc.harvard.edu/issues/3613
            dataversePaths.remove(dataversePaths.size() - 1);
        }
        // Add paths for linking dataverses
        for (Dataverse linkingDataverse : dvLinkingService.findLinkingDataverses(dataverse.getId())) {
            List<String> linkingdataverseSegments = findPathSegments(linkingDataverse);
            List<String> linkingDataversePaths = getDataversePathsFromSegments(linkingdataverseSegments);
            dataversePaths.addAll(linkingDataversePaths);
        }
        solrInputDocument.addField(SearchFields.SUBTREE, dataversePaths);
        docs.add(solrInputDocument);

        String status;
        try {
            solrServer.add(docs);
            solrServer.commit();
        } catch (SolrServerException | IOException ex) {
            status = ex.toString();
            logger.info(status);
            return new AsyncResult<>(status);
        }

        if (!systemConfig.isReadonlyMode()) {
            dvObjectService.updateContentIndexTime(dataverse);
        }
        IndexResponse indexResponse = solrIndexService.indexPermissionsForOneDvObject(dataverse);
        String msg = "indexed dataverse " + dataverse.getId() + ":" + dataverse.getAlias() + ". Response from permission indexing: " + indexResponse.getMessage();
        return new AsyncResult<>(msg);

    }

    @TransactionAttribute(REQUIRES_NEW)
    public Future<String> indexDatasetInNewTransaction(Long datasetId) {
        boolean doNormalSolrDocCleanUp = false;
        Dataset dataset = datasetDao.find(datasetId);
        return indexDataset(dataset, doNormalSolrDocCleanUp);
    }

    @Asynchronous
    public Future<String> asyncIndexDataset(Dataset dataset, boolean doNormalSolrDocCleanUp) {
        return indexDataset(dataset, doNormalSolrDocCleanUp);
    }

    @Asynchronous
    public void asyncIndexDatasetList(List<Dataset> datasets, boolean doNormalSolrDocCleanUp) {
        for (Dataset dataset : datasets) {
            indexDataset(dataset, true);
        }
    }

    public Future<String> indexDataset(Dataset dataset, boolean doNormalSolrDocCleanUp) {
        logger.fine("indexing dataset " + dataset.getId());
        /**
         * @todo should we use solrDocIdentifierDataset or
         * IndexableObject.IndexableTypes.DATASET.getName() + "_" ?
         */
        // String solrIdPublished = solrDocIdentifierDataset + dataset.getId();
        String solrIdPublished = determinePublishedDatasetSolrDocId(dataset);
        String solrIdDraftDataset = IndexableObject.IndexableTypes.DATASET.getName() + "_" + dataset.getId() + IndexableDataset.DatasetState.WORKING_COPY.getSuffix();
        // String solrIdDeaccessioned = IndexableObject.IndexableTypes.DATASET.getName()
        // + "_" + dataset.getId() +
        // IndexableDataset.DatasetState.DEACCESSIONED.getSuffix();
        String solrIdDeaccessioned = determineDeaccessionedDatasetId(dataset);
        StringBuilder debug = new StringBuilder();
        debug.append("\ndebug:\n");
        int numPublishedVersions = 0;
        List<DatasetVersion> versions = dataset.getVersions();
        List<String> solrIdsOfFilesToDelete = new ArrayList<>();
        for (DatasetVersion datasetVersion : versions) {
            Long versionDatabaseId = datasetVersion.getId();
            String versionTitle = datasetVersion.getParsedTitle();
            String semanticVersion = datasetVersion.getSemanticVersion();
            DatasetVersion.VersionState versionState = datasetVersion.getVersionState();
            if (versionState.equals(DatasetVersion.VersionState.RELEASED)) {
                numPublishedVersions += 1;
            }
            debug.append("version found with database id " + versionDatabaseId + "\n");
            debug.append("- title: " + versionTitle + "\n");
            debug.append("- semanticVersion-VersionState: " + semanticVersion + "-" + versionState + "\n");
            List<FileMetadata> fileMetadatas = datasetVersion.getFileMetadatas();
            List<String> fileInfo = new ArrayList<>();
            try {
                /*
                  Preemptively delete *all* Solr documents for files associated
                  with the dataset based on a Solr query.

                  We must query Solr for this information because the file has
                  been deleted from the database ( perhaps when Solr was down,
                  as reported in https://github.com/IQSS/dataverse/issues/2086
                  ) so the database doesn't even know about the file. It's an
                  orphan.

                  @todo We should also delete the corresponding Solr
                 * "permission" documents for the files.
                 */
                List<String> allFilesForDataset = findFilesOfParentDataset(dataset.getId());
                solrIdsOfFilesToDelete.addAll(allFilesForDataset);
            } catch (SearchException | NullPointerException ex) {
                logger.fine("could not run search of files to delete: " + ex);
            }
            int numFiles = 0;
            if (fileMetadatas != null) {
                numFiles = fileMetadatas.size();
            }
            debug.append("- files: ").append(numFiles).append(" ").append(fileInfo.toString()).append("\n");
        }
        debug.append("numPublishedVersions: ").append(numPublishedVersions).append("\n");
        if (doNormalSolrDocCleanUp) {
            IndexResponse resultOfAttemptToPremptivelyDeletePublishedFiles = solrIndexService.deleteMultipleSolrIds(solrIdsOfFilesToDelete);
            debug.append("result of attempt to premptively deleted published files before reindexing: ")
                    .append(resultOfAttemptToPremptivelyDeletePublishedFiles).append("\n");
        }
        DatasetVersion latestVersion = dataset.getLatestVersion();
        String latestVersionStateString = latestVersion.getVersionState().name();
        DatasetVersion.VersionState latestVersionState = latestVersion.getVersionState();
        DatasetVersion releasedVersion = dataset.getReleasedVersion();
        boolean atLeastOnePublishedVersion;
        atLeastOnePublishedVersion = releasedVersion != null;
        Map<DatasetVersion.VersionState, Boolean> desiredCards = new LinkedHashMap<>();
        /*
          @todo refactor all of this below and have a single method that takes
         * the map of desired cards (which correspond to Solr documents) as one
         * of the arguments and does all the operations necessary to achieve the
         * desired state.
         */
        StringBuilder results = new StringBuilder();
        if (!atLeastOnePublishedVersion) {
            results.append("No published version, nothing will be indexed as ")
                    .append(solrIdPublished).append("\n");
            if (latestVersionState.equals(DatasetVersion.VersionState.DRAFT)) {

                desiredCards.put(DatasetVersion.VersionState.DRAFT, true);
                IndexableDataset indexableDraftVersion = new IndexableDataset(latestVersion);
                String indexDraftResult = addOrUpdateDataset(indexableDraftVersion);
                results.append("The latest version is a working copy (latestVersionState: ")
                        .append(latestVersionStateString).append(") and indexing was attempted for ")
                        .append(solrIdDraftDataset).append(" (limited discoverability). Result: ")
                        .append(indexDraftResult).append("\n");

                desiredCards.put(DatasetVersion.VersionState.DEACCESSIONED, false);
                if (doNormalSolrDocCleanUp) {
                    String deleteDeaccessionedResult = removeDeaccessioned(dataset);
                    results.append("Draft exists, no need for deaccessioned version. Deletion attempted for ")
                            .append(solrIdDeaccessioned).append(" (and files). Result: ")
                            .append(deleteDeaccessionedResult).append("\n");
                }

                desiredCards.put(DatasetVersion.VersionState.RELEASED, false);
                if (doNormalSolrDocCleanUp) {
                    String deletePublishedResults = removePublished(dataset);
                    results.append("No published version. Attempting to delete traces of published version from index. Result: ")
                            .append(deletePublishedResults).append("\n");
                }

                /*
                  Desired state for existence of cards: {DRAFT=true,
                  DEACCESSIONED=false, RELEASED=false}

                  No published version, nothing will be indexed as dataset_17

                  The latest version is a working copy (latestVersionState:
                  DRAFT) and indexing was attempted for dataset_17_draft
                  (limited discoverability). Result: indexed dataset 17 as
                  dataset_17_draft. filesIndexed: [datafile_18_draft]

                  Draft exists, no need for deaccessioned version. Deletion
                  attempted for dataset_17_deaccessioned (and files). Result:
                  Attempted to delete dataset_17_deaccessioned from Solr index.
                  updateReponse was:
                  {responseHeader={status=0,QTime=1}}Attempted to delete
                  datafile_18_deaccessioned from Solr index. updateReponse was:
                  {responseHeader={status=0,QTime=1}}

                  No published version. Attempting to delete traces of
                  published version from index. Result: Attempted to delete
                  dataset_17 from Solr index. updateReponse was:
                  {responseHeader={status=0,QTime=1}}Attempted to delete
                  datafile_18 from Solr index. updateReponse was:
                  {responseHeader={status=0,QTime=0}}
                 */
                String result = getDesiredCardState(desiredCards) + results.toString() + debug.toString();
                logger.fine(result);
                solrIndexService.indexPermissionsForDatasetWithDataFiles(dataset);
                return new AsyncResult<>(result);
            } else if (latestVersionState.equals(DatasetVersion.VersionState.DEACCESSIONED)) {

                desiredCards.put(DatasetVersion.VersionState.DEACCESSIONED, true);
                IndexableDataset indexableDeaccessionedVersion = new IndexableDataset(latestVersion);
                String indexDeaccessionedVersionResult = addOrUpdateDataset(indexableDeaccessionedVersion);
                results.append("No draft version. Attempting to index as deaccessioned. Result: ").append(indexDeaccessionedVersionResult).append("\n");

                desiredCards.put(DatasetVersion.VersionState.RELEASED, false);
                if (doNormalSolrDocCleanUp) {
                    String deletePublishedResults = removePublished(dataset);
                    results.append("No published version. Attempting to delete traces of published version from index. Result: ").append(deletePublishedResults).append("\n");
                }

                desiredCards.put(DatasetVersion.VersionState.DRAFT, false);
                if (doNormalSolrDocCleanUp) {
                    List<String> solrDocIdsForDraftFilesToDelete = findSolrDocIdsForDraftFilesToDelete(dataset);
                    String deleteDraftDatasetVersionResult = removeSolrDocFromIndex(solrIdDraftDataset);
                    String deleteDraftFilesResults = deleteDraftFiles(solrDocIdsForDraftFilesToDelete);
                    results.append("Attempting to delete traces of drafts. Result: ")
                            .append(deleteDraftDatasetVersionResult).append(deleteDraftFilesResults).append("\n");
                }

                /*
                  Desired state for existence of cards: {DEACCESSIONED=true,
                  RELEASED=false, DRAFT=false}

                  No published version, nothing will be indexed as dataset_17

                  No draft version. Attempting to index as deaccessioned.
                  Result: indexed dataset 17 as dataset_17_deaccessioned.
                  filesIndexed: []

                  No published version. Attempting to delete traces of
                  published version from index. Result: Attempted to delete
                  dataset_17 from Solr index. updateReponse was:
                  {responseHeader={status=0,QTime=0}}Attempted to delete
                  datafile_18 from Solr index. updateReponse was:
                  {responseHeader={status=0,QTime=3}}

                  Attempting to delete traces of drafts. Result: Attempted to
                  delete dataset_17_draft from Solr index. updateReponse was:
                  {responseHeader={status=0,QTime=1}}
                 */
                String result = getDesiredCardState(desiredCards) + results.toString() + debug.toString();
                logger.fine(result);
                solrIndexService.indexPermissionsForDatasetWithDataFiles(dataset);
                return new AsyncResult<>(result);
            } else {
                String result = "No-op. Unexpected condition reached: No released version and latest version is neither draft nor deaccessioned";
                logger.fine(result);
                return new AsyncResult<>(result);
            }
        } else {
            results.append("Published versions found. ")
                    .append("Will attempt to index as ").append(solrIdPublished).append(" (discoverable by anonymous)\n");
            if (latestVersionState.equals(DatasetVersion.VersionState.RELEASED)
                    || latestVersionState.equals(DatasetVersion.VersionState.DEACCESSIONED)) {

                desiredCards.put(DatasetVersion.VersionState.RELEASED, true);
                IndexableDataset indexableReleasedVersion = new IndexableDataset(releasedVersion);
                String indexReleasedVersionResult = addOrUpdateDataset(indexableReleasedVersion);
                results.append("Attempted to index ").append(solrIdPublished).append(". Result: ").append(indexReleasedVersionResult).append("\n");

                desiredCards.put(DatasetVersion.VersionState.DRAFT, false);
                if (doNormalSolrDocCleanUp) {
                    List<String> solrDocIdsForDraftFilesToDelete = findSolrDocIdsForDraftFilesToDelete(dataset);
                    String deleteDraftDatasetVersionResult = removeSolrDocFromIndex(solrIdDraftDataset);
                    String deleteDraftFilesResults = deleteDraftFiles(solrDocIdsForDraftFilesToDelete);
                    results.append("The latest version is published. Attempting to delete drafts. Result: ")
                            .append(deleteDraftDatasetVersionResult).append(deleteDraftFilesResults).append("\n");
                }

                desiredCards.put(DatasetVersion.VersionState.DEACCESSIONED, false);
                if (doNormalSolrDocCleanUp) {
                    String deleteDeaccessionedResult = removeDeaccessioned(dataset);
                    results.append("No need for deaccessioned version. Deletion attempted for ")
                            .append(solrIdDeaccessioned).append(". Result: ").append(deleteDeaccessionedResult);
                }

                /*
                  Desired state for existence of cards: {RELEASED=true,
                  DRAFT=false, DEACCESSIONED=false}

                  Released versions found: 1. Will attempt to index as
                  dataset_17 (discoverable by anonymous)

                  Attempted to index dataset_17. Result: indexed dataset 17 as
                  dataset_17. filesIndexed: [datafile_18]

                  The latest version is published. Attempting to delete drafts.
                  Result: Attempted to delete dataset_17_draft from Solr index.
                  updateReponse was: {responseHeader={status=0,QTime=1}}

                  No need for deaccessioned version. Deletion attempted for
                  dataset_17_deaccessioned. Result: Attempted to delete
                  dataset_17_deaccessioned from Solr index. updateReponse was:
                  {responseHeader={status=0,QTime=1}}Attempted to delete
                  datafile_18_deaccessioned from Solr index. updateReponse was:
                  {responseHeader={status=0,QTime=0}}
                 */
                String result = getDesiredCardState(desiredCards) + results.toString() + debug.toString();
                logger.fine(result);
                solrIndexService.indexPermissionsForDatasetWithDataFiles(dataset);
                return new AsyncResult<>(result);
            } else if (latestVersionState.equals(DatasetVersion.VersionState.DRAFT)) {

                IndexableDataset indexableDraftVersion = new IndexableDataset(latestVersion);
                desiredCards.put(DatasetVersion.VersionState.DRAFT, true);
                String indexDraftResult = addOrUpdateDataset(indexableDraftVersion);
                results.append("The latest version is a working copy (latestVersionState: ")
                        .append(latestVersionStateString).append(") and will be indexed as ")
                        .append(solrIdDraftDataset).append(" (limited visibility). Result: ").append(indexDraftResult).append("\n");

                desiredCards.put(DatasetVersion.VersionState.RELEASED, true);
                IndexableDataset indexableReleasedVersion = new IndexableDataset(releasedVersion);
                String indexReleasedVersionResult = addOrUpdateDataset(indexableReleasedVersion);
                results.append("There is a published version we will attempt to index. Result: ").append(indexReleasedVersionResult).append("\n");

                desiredCards.put(DatasetVersion.VersionState.DEACCESSIONED, false);
                if (doNormalSolrDocCleanUp) {
                    String deleteDeaccessionedResult = removeDeaccessioned(dataset);
                    results.append("No need for deaccessioned version. Deletion attempted for ")
                            .append(solrIdDeaccessioned).append(". Result: ").append(deleteDeaccessionedResult);
                }

                /*
                  Desired state for existence of cards: {DRAFT=true,
                  RELEASED=true, DEACCESSIONED=false}

                  Released versions found: 1. Will attempt to index as
                  dataset_17 (discoverable by anonymous)

                  The latest version is a working copy (latestVersionState:
                  DRAFT) and will be indexed as dataset_17_draft (limited
                  visibility). Result: indexed dataset 17 as dataset_17_draft.
                  filesIndexed: [datafile_18_draft]

                  There is a published version we will attempt to index.
                  Result: indexed dataset 17 as dataset_17. filesIndexed:
                  [datafile_18]

                  No need for deaccessioned version. Deletion attempted for
                  dataset_17_deaccessioned. Result: Attempted to delete
                  dataset_17_deaccessioned from Solr index. updateReponse was:
                  {responseHeader={status=0,QTime=1}}Attempted to delete
                  datafile_18_deaccessioned from Solr index. updateReponse was:
                  {responseHeader={status=0,QTime=0}}
                 */
                String result = getDesiredCardState(desiredCards) + results.toString() + debug.toString();
                logger.fine(result);
                solrIndexService.indexPermissionsForDatasetWithDataFiles(dataset);
                return new AsyncResult<>(result);
            } else {
                String result = "No-op. Unexpected condition reached: There is at least one published version but the latest version is neither published nor draft";
                logger.fine(result);
                return new AsyncResult<>(result);
            }
        }
    }

    public List<String> findPathSegments(Dataverse dataverse) {
        List<String> dataversePathIds = new ArrayList<>();
        dataversePathIds.add(dataverse.getId().toString());

        while (dataverse.getOwner() != null) {
            dataverse = dataverse.getOwner();
            dataversePathIds.add(dataverse.getId().toString());
        }
        Collections.reverse(dataversePathIds);

        return dataversePathIds;
    }

    List<String> getDataversePathsFromSegments(List<String> dataversePathSegments) {
        List<String> subtrees = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (String segment : dataversePathSegments) {
            sb.append("/").append(segment);
            subtrees.add(sb.toString());
        }
        return subtrees;
    }

    public List<Long> findDataversesInSolrOnly() throws SearchException {
        return findDvObjectInSolrOnly("dataverses");
    }

    public List<Long> findDatasetsInSolrOnly() throws SearchException {
        return findDvObjectInSolrOnly("datasets");
    }

    public List<Long> findFilesInSolrOnly() throws SearchException {
        return findDvObjectInSolrOnly("files");
    }
    public static String getGroupPrefix() {
        return groupPrefix;
    }

    public static String getGroupPerUserPrefix() {
        return groupPerUserPrefix;
    }

    public String delete(Dataverse doomed) {
        logger.fine("deleting Solr document for dataverse " + doomed.getId());
        UpdateResponse updateResponse;
        try {
            updateResponse = solrServer.deleteById(solrDocIdentifierDataverse + doomed.getId());
            solrServer.commit();
        } catch (SolrServerException | IOException ex) {
            return ex.toString();
        }
        String response = "Successfully deleted dataverse " + doomed.getId() + " from Solr index. updateReponse was: " + updateResponse.toString();
        logger.fine(response);
        return response;
    }

    /**
     * @todo call this in fewer places, favoring
     * SolrIndexServiceBeans.deleteMultipleSolrIds instead to operate in batches
     * <p>
     * https://github.com/IQSS/dataverse/issues/142
     */
    public String removeSolrDocFromIndex(String doomed) {

        logger.fine("deleting Solr document: " + doomed);
        UpdateResponse updateResponse;
        try {
            updateResponse = solrServer.deleteById(doomed);
            solrServer.commit();
        } catch (SolrServerException | IOException ex) {
            return ex.toString();
        }
        String response = "Attempted to delete " + doomed + " from Solr index. updateReponse was: " + updateResponse.toString();
        logger.fine(response);
        return response;
    }

    public String convertToFriendlyDate(Date dateAsDate) {
        if (dateAsDate == null) {
            dateAsDate = new Date();
        }
        // using DateFormat for May 5, 2014 to match what's in DVN 3.x
        DateFormat dateFormatter = new SimpleDateFormat("MMM d, yyyy", Locale.US);
        return dateFormatter.format(dateAsDate);
    }

    /**
     * @return Dataverses that should be reindexed either because they have
     * never been indexed or their index time is before their modification time.
     */
    public List<Dataverse> findStaleOrMissingDataverses() {
        List<Dataverse> staleDataverses = new ArrayList<>();
        for (Dataverse dataverse : dataverseDao.findAll()) {
            if (dataverse.isRoot()) {
                continue;
            }
            if (stale(dataverse)) {
                staleDataverses.add(dataverse);
            }
        }
        return staleDataverses;
    }

    /**
     * @return Datasets that should be reindexed either because they have never
     * been indexed or their index time is before their modification time.
     */
    public List<Dataset> findStaleOrMissingDatasets() {
        List<Dataset> staleDatasets = new ArrayList<>();
        for (Dataset dataset : datasetDao.findAll()) {
            if (stale(dataset)) {
                staleDatasets.add(dataset);
            }
        }
        return staleDatasets;
    }

    // This is a convenience method for deleting all the SOLR documents
    // (Datasets and DataFiles) harvested by a specific HarvestingClient.
    // The delete logic is a bit simpler, than when deleting "real", local
    // datasets and files - for example, harvested datasets are never Drafts, etc.
    // We are also less concerned with the diagnostics; if any of it fails,
    // we don't need to treat it as a fatal condition.
    public void deleteHarvestedDocuments(HarvestingClient harvestingClient) {
        List<String> solrIdsOfDatasetsToDelete = new ArrayList<>();

        // I am going to make multiple solrIndexService.deleteMultipleSolrIds() calls;
        // one call for the list of datafiles in each dataset; then one more call to
        // delete all the dataset documents.
        // I'm *assuming* this is safer than to try and make one complete list of
        // all the documents (datasets and datafiles), and then attempt to delete
        // them all at once... (is there a limit??) The list can be huge - if the
        // harvested archive is on the scale of Odum or ICPSR, with thousands of
        // datasets and tens of thousands of files.
        //
        for (Dataset harvestedDataset : harvestingClient.getHarvestedDatasets()) {
            solrIdsOfDatasetsToDelete.add(solrDocIdentifierDataset + harvestedDataset.getId());

            List<String> solrIdsOfDatafilesToDelete = new ArrayList<>();
            for (DataFile datafile : harvestedDataset.getFiles()) {
                solrIdsOfDatafilesToDelete.add(solrDocIdentifierFile + datafile.getId());
            }
            logger.fine("attempting to delete the following datafiles from the index: " + StringUtils.join(solrIdsOfDatafilesToDelete, ","));
            IndexResponse resultOfAttemptToDeleteFiles = solrIndexService.deleteMultipleSolrIds(solrIdsOfDatafilesToDelete);
            logger.fine("result of an attempted delete of the harvested files associated with the dataset " + harvestedDataset.getId() + ": " + resultOfAttemptToDeleteFiles);

        }

        logger.fine("attempting to delete the following datasets from the index: " + StringUtils.join(solrIdsOfDatasetsToDelete, ","));
        IndexResponse resultOfAttemptToDeleteDatasets = solrIndexService.deleteMultipleSolrIds(solrIdsOfDatasetsToDelete);
        logger.fine("result of attempt to delete harvested datasets associated with the client: " + resultOfAttemptToDeleteDatasets + "\n");

    }

    // Another convenience method, for deleting all the SOLR documents (dataset_
    // and datafile_s) associated with a harveste dataset. The comments for the
    // method above apply here too.
    public void deleteHarvestedDocuments(Dataset harvestedDataset) {
        List<String> solrIdsOfDocumentsToDelete = new ArrayList<>();
        solrIdsOfDocumentsToDelete.add(solrDocIdentifierDataset + harvestedDataset.getId());

        for (DataFile datafile : harvestedDataset.getFiles()) {
            solrIdsOfDocumentsToDelete.add(solrDocIdentifierFile + datafile.getId());
        }

        logger.fine("attempting to delete the following documents from the index: " + StringUtils.join(solrIdsOfDocumentsToDelete, ","));
        IndexResponse resultOfAttemptToDeleteDocuments = solrIndexService.deleteMultipleSolrIds(solrIdsOfDocumentsToDelete);
        logger.fine("result of attempt to delete harvested documents: " + resultOfAttemptToDeleteDocuments + "\n");
    }

    // -------------------- PRIVATE --------------------

    private String deleteDraftFiles(List<String> solrDocIdsForDraftFilesToDelete) {
        IndexResponse indexResponse = solrIndexService.deleteMultipleSolrIds(solrDocIdsForDraftFilesToDelete);
        return indexResponse.toString();
    }

    private String addOrUpdateDataset(IndexableDataset indexableDataset) {
        IndexableDataset.DatasetState state = indexableDataset.getDatasetState();
        Dataset dataset = indexableDataset.getDatasetVersion().getDataset();
        logger.fine("adding or updating Solr document for dataset id " + dataset.getId());
        Collection<SolrInputDocument> docs = new ArrayList<>();
        List<String> dataverseSegments = new ArrayList<>();
        Dataverse rootDataverse = dataverseDao.findRootDataverse();
        String rootDataverseName = rootDataverse.getName();
        try {
            dataverseSegments = findPathSegments(dataset.getOwner());
        } catch (Exception ex) {
            logger.info("failed to find dataverseSegments for dataversePaths for " + SearchFields.SUBTREE + ": " + ex);
        }
        List<String> dataversePaths = getDataversePathsFromSegments(dataverseSegments);
        // Add Paths for linking dataverses
        List<DatasetLinkingDataverse> dsLinkingDv = dsLinkingService.findDatasetLinkingDataverses(dataset.getId());
        for (DatasetLinkingDataverse datasetLinkingDataverse : dsLinkingDv) {
            Dataverse linkingDataverse = datasetLinkingDataverse.getLinkingDataverse();
            List<String> linkingdataverseSegments = findPathSegments(linkingDataverse);
            List<String> linkingDataversePaths = getDataversePathsFromSegments(linkingdataverseSegments);
            dataversePaths.addAll(linkingDataversePaths);
        }
        SolrInputDocument solrInputDocument = new SolrInputDocument();
        String datasetSolrDocId = indexableDataset.getSolrDocId();
        solrInputDocument.addField(SearchFields.ID, datasetSolrDocId);
        solrInputDocument.addField(SearchFields.ENTITY_ID, dataset.getId());
        String dataverseVersion = systemConfig.getVersionWithBuild();
        solrInputDocument.addField(SearchFields.DATAVERSE_VERSION_INDEXED_BY, dataverseVersion);
        solrInputDocument.addField(SearchFields.IDENTIFIER, dataset.getGlobalId().toString());
        solrInputDocument.addField(SearchFields.DATASET_PERSISTENT_ID, dataset.getGlobalId().toString());
        solrInputDocument.addField(SearchFields.PERSISTENT_URL, dataset.getPersistentURL());
        solrInputDocument.addField(SearchFields.TYPE, SearchObjectType.DATASETS.getSolrValue());

        //This only grabs the immediate parent dataverse's category. We do the same for dataverses themselves.
        solrInputDocument.addField(SearchFields.CATEGORY_OF_DATAVERSE, dataset.getDataverseContext().getIndexableCategoryName());
        solrInputDocument.addField(SearchFields.IDENTIFIER_OF_DATAVERSE, dataset.getDataverseContext().getAlias());
        solrInputDocument.addField(SearchFields.DATAVERSE_NAME, dataset.getDataverseContext().getDisplayName());

        Date datasetSortByDate;
        Date majorVersionReleaseDate = getMostRecentMajorVersionReleaseDate(dataset);
        if (majorVersionReleaseDate != null) {
            String msg = "major release date found: " + majorVersionReleaseDate.toString();
            logger.fine(msg);
            datasetSortByDate = majorVersionReleaseDate;
        } else {
            if (indexableDataset.getDatasetState().equals(IndexableDataset.DatasetState.WORKING_COPY)) {
                solrInputDocument.addField(SearchFields.PUBLICATION_STATUS, SearchPublicationStatus.UNPUBLISHED.getSolrValue());
            } else if (indexableDataset.getDatasetState().equals(IndexableDataset.DatasetState.DEACCESSIONED)) {
                solrInputDocument.addField(SearchFields.PUBLICATION_STATUS, SearchPublicationStatus.DEACCESSIONED.getSolrValue());
            }
            Date createDate = dataset.getCreateDate();
            logger.fine("can't find major release date " +
                    (createDate != null ? "using create date: " + createDate : "or create date, using \"now\""));
            datasetSortByDate = createDate != null ? createDate : new Date();
        }
        solrInputDocument.addField(SearchFields.RELEASE_OR_CREATE_DATE, datasetSortByDate);
        solrInputDocument.addField(SearchFields.RELEASE_OR_CREATE_DATE_SEARCHABLE_TEXT, convertToFriendlyDate(datasetSortByDate));

        if (state.equals(IndexableDataset.DatasetState.PUBLISHED)) {
            solrInputDocument.addField(SearchFields.PUBLICATION_STATUS, SearchPublicationStatus.PUBLISHED.getSolrValue());
            SolrInputField field = solrInputDocument.getField(SearchFields.PUBLICATION_STATUS);
        } else if (state.equals(IndexableDataset.DatasetState.WORKING_COPY)) {
            solrInputDocument.addField(SearchFields.PUBLICATION_STATUS, SearchPublicationStatus.DRAFT.getSolrValue());
        }

        addDatasetReleaseDateToSolrDoc(solrInputDocument, dataset);

        if (dataset.isHarvested()) {
            solrInputDocument.addField(SearchFields.IS_HARVESTED, true);
            solrInputDocument.addField(SearchFields.METADATA_SOURCE, HARVESTED);
        } else {
            solrInputDocument.addField(SearchFields.IS_HARVESTED, false);
            solrInputDocument.addField(SearchFields.METADATA_SOURCE, rootDataverseName);
        }

        DatasetVersion datasetVersion = indexableDataset.getDatasetVersion();
        String parentDatasetTitle = "TBD";
        Set<Locale> configuredLocales = getConfiguredLocales();
        if (datasetVersion != null) {
            solrInputDocument.addField(SearchFields.DATASET_VERSION_ID, datasetVersion.getId());
            for (Locale locale : configuredLocales) {
                String suffix = "_" + locale.getLanguage();
                solrInputDocument.addField(SearchFields.DATASET_CITATION + suffix, citationFactory.create(datasetVersion, locale).toString(false));
                solrInputDocument.addField(SearchFields.DATASET_CITATION_HTML + suffix, citationFactory.create(datasetVersion, locale).toString(true));
            }
            if (datasetVersion.isInReview()) {
                solrInputDocument.addField(SearchFields.PUBLICATION_STATUS, SearchPublicationStatus.IN_REVIEW.getSolrValue());
            }

            for (DatasetField dsf : datasetVersion.getFlatDatasetFields()) {

                DatasetFieldType dsfType = dsf.getDatasetFieldType();
                SolrField dsfSolrField = SolrField.of(dsfType.getName(), dsfType.getFieldType(),
                        dsfType.isThisOrParentAllowsMultipleValues(), dsfType.isFacetable());
                String solrFieldSearchable = dsfSolrField.getNameSearchable();
                String solrFieldFacetable = dsfSolrField.getNameFacetable();

                if (dsf.getValues() != null && !dsf.getValues().isEmpty()
                        && dsf.getValues().get(0) != null && solrFieldSearchable != null) {

                    logger.fine("indexing " + dsf.getDatasetFieldType().getName() + ":" + dsf.getValues() + " into " + solrFieldSearchable + " and maybe " + solrFieldFacetable);
                    // if (dsfType.getSolrField().getSolrType().equals(SolrField.SolrType.INTEGER))
                    // {
                    if (SolrField.SolrType.EMAIL.equals(dsfSolrField.getSolrType())) {
                        // no-op. we want to keep email address out of Solr per
                        // https://github.com/IQSS/dataverse/issues/759
                    } else if (SolrField.SolrType.DATE.equals(dsfSolrField.getSolrType())) {
                        String dateAsString = dsf.getValues_nondisplay().get(0);
                        logger.fine("date as string: " + dateAsString);
                        if (dateAsString != null && !dateAsString.isEmpty()) {
                            SimpleDateFormat inputDateyyyy = new SimpleDateFormat("yyyy", Locale.ENGLISH);
                            try {
                                /*
                                  @todo when bean validation is working we
                                 * won't have to convert strings into dates
                                 */
                                logger.fine("Trying to convert " + dateAsString + " to a YYYY date from dataset " + dataset.getId());
                                Date dateAsDate = inputDateyyyy.parse(dateAsString);
                                SimpleDateFormat yearOnly = new SimpleDateFormat("yyyy");
                                String datasetFieldFlaggedAsDate = yearOnly.format(dateAsDate);
                                logger.fine("YYYY only: " + datasetFieldFlaggedAsDate);
                                // solrInputDocument.addField(solrFieldSearchable,
                                // Integer.parseInt(datasetFieldFlaggedAsDate));
                                solrInputDocument.addField(solrFieldSearchable, dateAsString);
                                if (dsfSolrField.isFacetable()) {
                                    // solrInputDocument.addField(solrFieldFacetable,
                                    // Integer.parseInt(datasetFieldFlaggedAsDate));
                                    solrInputDocument.addField(solrFieldFacetable, datasetFieldFlaggedAsDate);
                                }
                            } catch (Exception ex) {
                                logger.info("unable to convert " + dateAsString + " into YYYY format and couldn't index it (" + dsfType.getName() + ")");
                            }
                        }
                    } else {
                        // _s (dynamic string) and all other Solr fields

                        if ("authorAffiliation".equals(dsf.getDatasetFieldType().getName())) {
                            /*
                              @todo think about how to tie the fact that this
                             * needs to be multivalued (_ss) because a
                             * multivalued facet (authorAffilition_ss) is being
                             * collapsed into here at index time. The business
                             * logic to determine if a data-driven metadata
                             * field should be indexed into Solr as a single or
                             * multiple value lives in the getSolrField() method
                             * of DatasetField.java
                             */
                            solrInputDocument.addField(SearchFields.AFFILIATION, dsf.getValuesWithoutNaValues());
                        } else if (dsf.getDatasetFieldType().getName().equals("title")) {
                            // datasets have titles not names but index title under name as well so we can
                            // sort datasets by name along dataverses and files
                            List<String> possibleTitles = dsf.getValues();
                            String firstTitle = possibleTitles.get(0);
                            if (firstTitle != null) {
                                parentDatasetTitle = firstTitle;
                            }
                            solrInputDocument.addField(SearchFields.NAME_SORT, dsf.getValues());
                        }
                        if (dsfType.isControlledVocabulary()) {
                            for (ControlledVocabularyValue controlledVocabularyValue : dsf.getControlledVocabularyValues()) {
                                if (controlledVocabularyValue.getStrValue().equals(DatasetField.NA_VALUE)) {
                                    continue;
                                }
                                solrInputDocument.addField(solrFieldSearchable, controlledVocabularyValue.getStrValue());
                                if (dsfSolrField.isFacetable()) {
                                    solrInputDocument.addField(solrFieldFacetable, controlledVocabularyValue.getStrValue());
                                }
                            }
                        } else if (FieldType.TEXTBOX.equals(dsfType.getFieldType())) {
                            // strip HTML
                            List<String> htmlFreeText = StringUtil.htmlArray2textArray(dsf.getValuesWithoutNaValues());
                            solrInputDocument.addField(solrFieldSearchable, htmlFreeText);
                            if (dsfSolrField.isFacetable()) {
                                solrInputDocument.addField(solrFieldFacetable, htmlFreeText);
                            }
                        } else {
                            // do not strip HTML
                            solrInputDocument.addField(solrFieldSearchable, dsf.getValuesWithoutNaValues());
                            if (dsfSolrField.isFacetable()) {
                                if (dsf.getDatasetFieldType().getName().equals(DatasetFieldConstant.topicClassValue)) {
                                    String topicClassificationTerm = getTopicClassificationTermOrTermAndVocabulary(dsf);
                                    if (topicClassificationTerm != null) {
                                        logger.fine(solrFieldFacetable + " gets " + topicClassificationTerm);
                                        solrInputDocument.addField(solrFieldFacetable, topicClassificationTerm);
                                    }
                                } else {
                                    solrInputDocument.addField(solrFieldFacetable, dsf.getValuesWithoutNaValues());
                                }
                            }
                        }
                    }
                }
                if (dsfType.getFieldType() == FieldType.GEOBOX && geoboxIndexUtil.isIndexable(dsf)) {
                    solrInputDocument.addField(solrFieldSearchable, geoboxIndexUtil.geoboxFieldToSolr(dsf));
                }
            }
        }

        solrInputDocument.addField(SearchFields.SUBTREE, dataversePaths);
        solrInputDocument.addField(SearchFields.PARENT_ID, dataset.getOwner().getId());
        solrInputDocument.addField(SearchFields.PARENT_NAME, dataset.getOwner().getName());

        if (state.equals(IndexableDataset.DatasetState.DEACCESSIONED)) {
            String deaccessionNote = datasetVersion.getVersionNote();
            if (deaccessionNote != null) {
                solrInputDocument.addField(SearchFields.DATASET_DEACCESSION_REASON, deaccessionNote);
            }
        }

        /*
          File Indexing
         */
        boolean doFullTextIndexing = settingsService.isTrueForKey(SettingsServiceBean.Key.SolrFullTextIndexing);
        Long maxFTIndexingSize = settingsService.getValueForKeyAsLong(SettingsServiceBean.Key.SolrMaxFileSizeForFullTextIndexing);
        long maxSize = (maxFTIndexingSize == 0) ? maxFTIndexingSize : Long.MAX_VALUE;

        List<String> filesIndexed = new ArrayList<>();
        Set<String> licensesIndexed = new HashSet<String>();
        if (datasetVersion != null) {
            List<FileMetadata> fileMetadatas = datasetVersion.getFileMetadatas();
            boolean checkForDuplicateMetadata = false;
            if (datasetVersion.isDraft() && dataset.isReleased() && dataset.getReleasedVersion() != null) {
                checkForDuplicateMetadata = true;
                logger.fine(
                        "We are indexing a draft version of a dataset that has a released version. We'll be checking file metadatas if they are exact clones of the released versions.");
            }

            List<SolrInputDocument> filesToIndex = new ArrayList<>();
            FileTermsOfUse firstFileTermsOfUse = null;
            boolean sameLicenseForAllFiles = true;
            for (FileMetadata fileMetadata : fileMetadatas) {
                boolean indexThisMetadata = true;
                if (checkForDuplicateMetadata) {
                    logger.fine("Checking if this file metadata is a duplicate.");
                    for (FileMetadata releasedFileMetadata : dataset.getReleasedVersion().getFileMetadatas()) {
                        if (fileMetadata.getDataFile() != null && fileMetadata.getDataFile().equals(releasedFileMetadata.getDataFile())) {
                            /*
                             * Duplicate if metadata matches and, for full text indexing and the
                             * SearchFields.ACCESS field, if the restricted status of the file hasn't
                             * changed. To address the case where full text indexing was on when a file was
                             * not restricted and it is now restricted and full text indexing has been shut
                             * off, we need to check for the change in restricted status regardless of
                             * whether full text indexing is on now.
                             */
                            if ((fileMetadata.getTermsOfUse().getRestrictType() == releasedFileMetadata.getTermsOfUse().getRestrictType())) {
                                if (fileMetadata.contentEquals(releasedFileMetadata)) {
                                    indexThisMetadata = false;
                                    logger.fine("This file metadata hasn't changed since the released version; skipping indexing.");
                                } else {
                                    logger.fine("This file metadata has changed since the released version; we want to index it!");
                                }
                            } else {
                                logger.fine("This file's restricted status has changed since the released version; we want to index it!");
                            }
                            break;
                        }
                    }
                }
                if (indexThisMetadata) {
                    SolrInputDocument datafileSolrInputDocument = new SolrInputDocument();
                    Long fileEntityId = fileMetadata.getDataFile().getId();
                    datafileSolrInputDocument.addField(SearchFields.ENTITY_ID, fileEntityId);
                    datafileSolrInputDocument.addField(SearchFields.DATAVERSE_VERSION_INDEXED_BY, dataverseVersion);
                    GlobalId globalIdentifier = fileMetadata.getDataFile().getGlobalId();
                    datafileSolrInputDocument.addField(SearchFields.IDENTIFIER, globalIdentifier != null && globalIdentifier.isComplete()
                            ? globalIdentifier.asString() : null);
                    datafileSolrInputDocument.addField(SearchFields.PERSISTENT_URL, dataset.getPersistentURL());
                    datafileSolrInputDocument.addField(SearchFields.TYPE, SearchObjectType.FILES.getSolrValue());
                    datafileSolrInputDocument.addField(SearchFields.CATEGORY_OF_DATAVERSE, dataset.getDataverseContext().getIndexableCategoryName());
                    datafileSolrInputDocument.addField(SearchFields.ACCESS,
                                                       fileMetadata.getTermsOfUse().getTermsOfUseType() == TermsOfUseType.RESTRICTED ? SearchConstants.RESTRICTED : SearchConstants.PUBLIC);

                    if (!dataset.hasActiveEmbargo()) {
                        FileTermsOfUse fileTermsOfUse = fileMetadata.getTermsOfUse();
                        if (fileTermsOfUse.getTermsOfUseType() == TermsOfUseType.LICENSE_BASED && fileTermsOfUse.getLicense() != null) {
                            datafileSolrInputDocument.addField(SearchFields.LICENSE, fileTermsOfUse.getLicense().getName());
                        } else if (fileTermsOfUse.getTermsOfUseType() == TermsOfUseType.ALL_RIGHTS_RESERVED) {
                            datafileSolrInputDocument.addField(SearchFields.LICENSE, IndexedTermOfUse.ALL_RIGHTS_RESERVED.getName());
                        } else if (fileTermsOfUse.getTermsOfUseType() == TermsOfUseType.RESTRICTED) {
                            datafileSolrInputDocument.addField(SearchFields.LICENSE, IndexedTermOfUse.RESTRICTED.getName());
                        }
                    }

                    /* Full-text indexing using Apache Tika */
                    if (doFullTextIndexing) {
                        if (!dataset.isHarvested() && fileMetadata.getTermsOfUse().getTermsOfUseType() != TermsOfUseType.RESTRICTED
                                && !fileMetadata.getDataFile().isFilePackage()) {
                            StorageIO<DataFile> accessObject;
                            InputStream instream = null;
                            ContentHandler textHandler;
                            try {
                                accessObject = dataAccess.getStorageIO(fileMetadata.getDataFile());
                                if (accessObject != null) {
                                    accessObject.open();
                                    // If the size is >max, we don't use the stream. However, for S3, the stream is
                                    // currently opened in the call above (see
                                    // https://github.com/IQSS/dataverse/issues/5165), so we want to get a handle so
                                    // we can close it below.
                                    instream = accessObject.getInputStream();
                                    if (accessObject.getSize() <= maxSize) {
                                        AutoDetectParser autoParser = new AutoDetectParser();
                                        textHandler = new BodyContentHandler(-1);
                                        Metadata metadata = new Metadata();
                                        ParseContext context = new ParseContext();
                                        /*
                                         * Try parsing the file. Note that, other than by limiting size, there's been no
                                         * check see whether this file is a good candidate for text extraction (e.g.
                                         * based on type).
                                         */
                                        autoParser.parse(instream, textHandler, metadata, context);
                                        datafileSolrInputDocument.addField(SearchFields.FULL_TEXT,
                                                                           textHandler.toString());
                                    }
                                }
                            } catch (Exception e) {
                                // Needs better logging of what went wrong in order to
                                // track down "bad" documents.
                                logger.warning(String.format("Full-text indexing for %s failed",
                                                             fileMetadata.getDataFile().getDisplayName()));
                                e.printStackTrace();
                                continue;
                            } catch (OutOfMemoryError e) {
                                textHandler = null;
                                logger.warning(String.format("Full-text indexing for %s failed due to OutOfMemoryError",
                                                             fileMetadata.getDataFile().getDisplayName()));
                                continue;
                            } finally {
                                IOUtils.closeQuietly(instream);
                            }
                        }
                    }

                    String filenameCompleteFinal = "";
                        String filenameComplete = fileMetadata.getLabel();
                        if (filenameComplete != null) {
                            int i = filenameComplete.lastIndexOf('.');
                            if (i > 0) {
                                try {
                                    String fileExtension = filenameComplete.substring(i + 1);
                                    datafileSolrInputDocument.addField(SearchFields.FILE_EXTENSION, fileExtension);
                                } catch (IndexOutOfBoundsException ex) {
                                    logger.fine("problem with filename '" + filenameComplete + "': no extension");
                                }

                                try {
                                    String filenameWithoutExtension = filenameComplete.substring(0, i);
                                    datafileSolrInputDocument.addField(SearchFields.FILENAME_WITHOUT_EXTENSION, filenameWithoutExtension);
                                    datafileSolrInputDocument.addField(SearchFields.FILE_NAME, filenameWithoutExtension);
                                } catch (IndexOutOfBoundsException ex) {
                                    logger.fine("problem with filename '" + filenameComplete + "': not valid filename");
                                }
                            } else {
                                logger.fine("problem with filename '" + filenameComplete + "': no extension? empty string as filename?");
                            }
                            filenameCompleteFinal = filenameComplete;
                        }
                        for (DataFileCategory tag : fileMetadata.getCategories()) {
                            datafileSolrInputDocument.addField(SearchFields.FILE_TAG, tag.getName());
                            datafileSolrInputDocument.addField(SearchFields.FILE_TAG_SEARCHABLE, tag.getName());
                        }
                    datafileSolrInputDocument.addField(SearchFields.NAME, filenameCompleteFinal);
                    datafileSolrInputDocument.addField(SearchFields.NAME_SORT, filenameCompleteFinal);
                    datafileSolrInputDocument.addField(SearchFields.FILE_NAME, filenameCompleteFinal);

                    datafileSolrInputDocument.addField(SearchFields.DATASET_VERSION_ID, datasetVersion.getId());

                    /*
                      for rules on sorting files see
                      https://docs.google.com/a/harvard.edu/document/d/1DWsEqT8KfheKZmMB3n_VhJpl9nIxiUjai_AIQPAjiyA/edit?usp=sharing
                      via https://redmine.hmdc.harvard.edu/issues/3701
                     */
                    Date fileSortByDate = new Date();
                    DataFile datafile = fileMetadata.getDataFile();
                    if (datafile != null) {
                        boolean fileHasBeenReleased = datafile.isReleased();
                        if (fileHasBeenReleased) {
                            logger.fine("indexing file with filePublicationTimestamp. " + fileMetadata.getId() + " (file id " + datafile.getId() + ")");
                            Timestamp filePublicationTimestamp = datafile.getPublicationDate();
                            if (filePublicationTimestamp != null) {
                                fileSortByDate = filePublicationTimestamp;
                            } else {
                                String msg = "filePublicationTimestamp was null for fileMetadata id " + fileMetadata.getId() + " (file id " + datafile.getId() + ")";
                                logger.info(msg);
                            }
                        } else {
                            logger.fine("indexing file with fileCreateTimestamp. " + fileMetadata.getId() + " (file id " + datafile.getId() + ")");
                            Timestamp fileCreateTimestamp = datafile.getCreateDate();
                            if (fileCreateTimestamp != null) {
                                fileSortByDate = fileCreateTimestamp;
                            } else {
                                String msg = "fileCreateTimestamp was null for fileMetadata id " + fileMetadata.getId() + " (file id " + datafile.getId() + ")";
                                logger.info(msg);
                            }
                        }
                        if (datafile.isHarvested()) {
                            datafileSolrInputDocument.addField(SearchFields.IS_HARVESTED, true);
                            datafileSolrInputDocument.addField(SearchFields.METADATA_SOURCE, HARVESTED);
                        } else {
                            datafileSolrInputDocument.addField(SearchFields.IS_HARVESTED, false);
                            datafileSolrInputDocument.addField(SearchFields.METADATA_SOURCE, rootDataverseName);
                        }
                    }
                    datafileSolrInputDocument.addField(SearchFields.RELEASE_OR_CREATE_DATE, fileSortByDate);
                    datafileSolrInputDocument.addField(SearchFields.RELEASE_OR_CREATE_DATE_SEARCHABLE_TEXT, convertToFriendlyDate(fileSortByDate));

                    if (majorVersionReleaseDate == null && !datafile.isHarvested()) {
                        datafileSolrInputDocument.addField(SearchFields.PUBLICATION_STATUS, SearchPublicationStatus.UNPUBLISHED.getSolrValue());
                    }

                    if (datasetVersion.isInReview()) {
                        datafileSolrInputDocument.addField(SearchFields.PUBLICATION_STATUS, SearchPublicationStatus.IN_REVIEW.getSolrValue());
                    }

                    String fileSolrDocId = solrDocIdentifierFile + fileEntityId;
                    if (indexableDataset.getDatasetState().equals(IndexableDataset.DatasetState.PUBLISHED)) {
                        fileSolrDocId = solrDocIdentifierFile + fileEntityId;
                        datafileSolrInputDocument.addField(SearchFields.PUBLICATION_STATUS, SearchPublicationStatus.PUBLISHED.getSolrValue());
                        // datafileSolrInputDocument.addField(SearchFields.PERMS, publicGroupString);
                        addDatasetReleaseDateToSolrDoc(datafileSolrInputDocument, dataset);
                    } else if (indexableDataset.getDatasetState().equals(IndexableDataset.DatasetState.WORKING_COPY)) {
                        fileSolrDocId = solrDocIdentifierFile + fileEntityId + indexableDataset.getDatasetState().getSuffix();
                        datafileSolrInputDocument.addField(SearchFields.PUBLICATION_STATUS, SearchPublicationStatus.DRAFT.getSolrValue());
                    }
                    datafileSolrInputDocument.addField(SearchFields.ID, fileSolrDocId);

                    datafileSolrInputDocument.addField(SearchFields.FILE_TYPE_FRIENDLY, fileMetadata.getDataFile().getFriendlyTypeForIndex(Locale.ENGLISH));
                    datafileSolrInputDocument.addField(SearchFields.FILE_CONTENT_TYPE, fileMetadata.getDataFile().getContentType());
                    datafileSolrInputDocument.addField(SearchFields.FILE_TYPE_SEARCHABLE, fileMetadata.getDataFile().getFriendlyTypeForIndex(Locale.ENGLISH));
                    // For the file type facets, we have a property file that maps mime types
                    // to facet-friendly names; "application/fits" should become "FITS", etc.:
                    datafileSolrInputDocument.addField(SearchFields.FILE_TYPE, FileUtil.getFacetFileTypeForIndex(fileMetadata.getDataFile(), Locale.ENGLISH));
                    datafileSolrInputDocument.addField(SearchFields.FILE_TYPE_SEARCHABLE, FileUtil.getFacetFileTypeForIndex(fileMetadata.getDataFile(), Locale.ENGLISH));
                    datafileSolrInputDocument.addField(SearchFields.FILE_SIZE_IN_BYTES, fileMetadata.getDataFile().getFilesize());
                    if (DataFile.ChecksumType.MD5.equals(fileMetadata.getDataFile().getChecksumType())) {
                        /*
                          @todo Someday we should probably deprecate this
                         * FILE_MD5 in favor of a combination of
                         * FILE_CHECKSUM_TYPE and FILE_CHECKSUM_VALUE.
                         */
                        datafileSolrInputDocument.addField(SearchFields.FILE_MD5, fileMetadata.getDataFile().getChecksumValue());
                    }
                    datafileSolrInputDocument.addField(SearchFields.FILE_CHECKSUM_TYPE, fileMetadata.getDataFile().getChecksumType().toString());
                    datafileSolrInputDocument.addField(SearchFields.FILE_CHECKSUM_VALUE, fileMetadata.getDataFile().getChecksumValue());
                    datafileSolrInputDocument.addField(SearchFields.DESCRIPTION, fileMetadata.getDescription());
                    datafileSolrInputDocument.addField(SearchFields.FILE_DESCRIPTION, fileMetadata.getDescription());
                    datafileSolrInputDocument.addField(SearchFields.FILE_PERSISTENT_ID, fileMetadata.getDataFile().getGlobalId().toString());
                    datafileSolrInputDocument.addField(SearchFields.UNF, fileMetadata.getDataFile().getUnf());
                    datafileSolrInputDocument.addField(SearchFields.SUBTREE, dataversePaths);
                    // datafileSolrInputDocument.addField(SearchFields.HOST_DATAVERSE,
                    // dataFile.getOwner().getOwner().getName());
                    // datafileSolrInputDocument.addField(SearchFields.PARENT_NAME,
                    // dataFile.getDataset().getTitle());
                    datafileSolrInputDocument.addField(SearchFields.PARENT_ID, fileMetadata.getDataFile().getOwner().getId());
                    datafileSolrInputDocument.addField(SearchFields.PARENT_IDENTIFIER, fileMetadata.getDataFile().getOwner().getGlobalId().toString());
                    for (Locale locale : configuredLocales) {
                        datafileSolrInputDocument.addField(SearchFields.PARENT_CITATION + "_" + locale.getLanguage(),
                                citationFactory.create(fileMetadata.getDatasetVersion(), locale)
                                        .toString(false));
                    }
                    datafileSolrInputDocument.addField(SearchFields.PARENT_NAME, parentDatasetTitle);

                    // If this is a tabular data file -- i.e., if there are data
                    // variables associated with this file, we index the variable
                    // names and labels:
                    if (fileMetadata.getDataFile().isTabularData()) {
                        List<DataVariable> variables = fileMetadata.getDataFile().getDataTable().getDataVariables();
                        for (DataVariable var : variables) {
                            // Hard-coded search fields, for now:
                            // TODO: eventually: review, decide how datavariables should
                            // be handled for indexing purposes. (should it be a fixed
                            // setup, defined in the code? should it be flexible? unlikely
                            // that this needs to be domain-specific... since these data
                            // variables are quite specific to tabular data, which in turn
                            // is something social science-specific...
                            // anyway -- needs to be reviewed. -- L.A. 4.0alpha1

                            if (var.getName() != null && !var.getName().equals("")) {
                                datafileSolrInputDocument.addField(SearchFields.VARIABLE_NAME, var.getName());
                            }
                            if (var.getLabel() != null && !var.getLabel().equals("")) {
                                datafileSolrInputDocument.addField(SearchFields.VARIABLE_LABEL, var.getLabel());
                            }
                        }
                        // TABULAR DATA TAGS:
                        // (not to be confused with the file categories, indexed above!)
                        for (DataFileTag tag : fileMetadata.getDataFile().getTags()) {
                            String tagLabel = tag.getTypeLabel();
                            datafileSolrInputDocument.addField(SearchFields.TABDATA_TAG, tagLabel);
                        }
                    }

                    if (indexableDataset.isFilesShouldBeIndexed()) {
                        filesIndexed.add(fileSolrDocId);
                        filesToIndex.add(datafileSolrInputDocument);
                    }
                }

                FileTermsOfUse fileTermsOfUse = fileMetadata.getTermsOfUse();
                if (firstFileTermsOfUse == null) {
                    firstFileTermsOfUse = fileTermsOfUse;
                } else if (sameLicenseForAllFiles) {
                    TermsOfUseType firstFileTermsOfUseType = firstFileTermsOfUse.getTermsOfUseType();
                    if (firstFileTermsOfUseType != fileTermsOfUse.getTermsOfUseType()) {
                        sameLicenseForAllFiles = false;
                    }
                    if (firstFileTermsOfUseType == TermsOfUseType.LICENSE_BASED) {
                        sameLicenseForAllFiles = (firstFileTermsOfUse.getLicense() != null && fileTermsOfUse.getLicense() != null)
                                                 ? firstFileTermsOfUse.getLicense().getId().equals(fileTermsOfUse.getLicense().getId()) : false;
                    }
                    if (firstFileTermsOfUseType == TermsOfUseType.RESTRICTED) {
                        sameLicenseForAllFiles = firstFileTermsOfUse.getRestrictType() == fileTermsOfUse.getRestrictType() &&
                                StringUtils.equals(firstFileTermsOfUse.getRestrictCustomText(), fileTermsOfUse.getRestrictCustomText());
                    }
                    if (firstFileTermsOfUseType == TermsOfUseType.ALL_RIGHTS_RESERVED) {
                        sameLicenseForAllFiles = firstFileTermsOfUse.getRestrictType() == fileTermsOfUse.getRestrictType();
                    }
                }
            }

            if (!dataset.hasActiveEmbargo() && firstFileTermsOfUse != null) {
                if (sameLicenseForAllFiles) {
                    TermsOfUseType firstFileTermsOfUseType = firstFileTermsOfUse.getTermsOfUseType();
                    if (firstFileTermsOfUseType == TermsOfUseType.LICENSE_BASED && firstFileTermsOfUse.getLicense() != null) {
                        solrInputDocument.addField(SearchFields.LICENSE, firstFileTermsOfUse.getLicense().getName());
                    } else if (firstFileTermsOfUseType == TermsOfUseType.ALL_RIGHTS_RESERVED) {
                        solrInputDocument.addField(SearchFields.LICENSE, IndexedTermOfUse.ALL_RIGHTS_RESERVED.getName());
                    } else if (firstFileTermsOfUseType == TermsOfUseType.RESTRICTED) {
                        solrInputDocument.addField(SearchFields.LICENSE, IndexedTermOfUse.RESTRICTED.getName());
                    }
                } else {
                    solrInputDocument.addField(SearchFields.LICENSE, IndexedTermOfUse.MULTIPLE.getName());
                }
            }

            docs.addAll(filesToIndex);
        }

        docs.add(solrInputDocument);

        try {
            solrServer.add(docs);
            solrServer.commit();
        } catch (SolrServerException | IOException ex) {
            return ex.toString();
        }

        Long dsId = dataset.getId();
        if (!systemConfig.isReadonlyMode()) {
            dvObjectService.updateContentIndexTime(dataset);
        }

        // return "indexed dataset " + dataset.getId() + " as " + solrDocId +
        // "\nindexFilesResults for " + solrDocId + ":" + fileInfo.toString();
        return "indexed dataset " + dsId + " as " + datasetSolrDocId + ". filesIndexed: " + filesIndexed;
    }

    private Date getMostRecentMajorVersionReleaseDate(Dataset dataset) {
        if (dataset.isHarvested()) {
            return dataset.getVersions().get(0).getReleaseTime();
        }
        for (DatasetVersion version : dataset.getVersions()) {
            if (version.getReleaseTime() != null && version.getMinorVersionNumber().equals(0L)) {
                return version.getReleaseTime();
            }
        }
        return null;
    }

    /**
     * If the "Topic Classification" has a "Vocabulary", return both the "Term"
     * and the "Vocabulary" with the latter in parentheses. For example, the
     * Murray Research Archive uses "1 (Generations)" and "yes (Follow-up
     * permitted)".
     */
    private String getTopicClassificationTermOrTermAndVocabulary(DatasetField topicClassDatasetField) { //
        String finalValue = null;
        String topicClassVocab = null;
        String topicClassValue = null;
        for (DatasetField sibling : topicClassDatasetField.getDatasetFieldParent().
                getOrElseThrow(() -> new IllegalStateException("There was no parent dataset field present."))
                .getDatasetFieldsChildren()) {
            DatasetFieldType datasetFieldType = sibling.getDatasetFieldType();
            String name = datasetFieldType.getName();
            if (name.equals(DatasetFieldConstant.topicClassVocab)) {
                topicClassVocab = sibling.getDisplayValue();
            } else if (name.equals(DatasetFieldConstant.topicClassValue)) {
                topicClassValue = sibling.getDisplayValue();
            }
            if (topicClassValue != null) {
                if (topicClassVocab != null) {
                    finalValue = topicClassValue + " (" + topicClassVocab + ")";
                } else {
                    finalValue = topicClassValue;
                }
            }
        }
        return finalValue;
    }

    private void addDataverseReleaseDateToSolrDoc(SolrInputDocument solrInputDocument, Dataverse dataverse) {
        if (dataverse.getPublicationDate() != null) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(dataverse.getPublicationDate().getTime());
            int YYYY = calendar.get(Calendar.YEAR);
            solrInputDocument.addField(SearchFields.PUBLICATION_YEAR, YYYY);
        }
    }

    private void addDatasetReleaseDateToSolrDoc(SolrInputDocument solrInputDocument, Dataset dataset) {
        if (dataset.getPublicationDate() != null) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(dataset.getPublicationDate().getTime());
            int YYYY = calendar.get(Calendar.YEAR);
            solrInputDocument.addField(SearchFields.PUBLICATION_YEAR, YYYY);
            solrInputDocument.addField(SearchFields.DATASET_PUBLICATION_DATE, YYYY);
        }
    }

    private List<String> findSolrDocIdsForDraftFilesToDelete(Dataset datasetWithDraftFilesToDelete) {
        List<String> solrIdsOfFilesToDelete = new ArrayList<>();
        for (DatasetVersion datasetVersion : datasetWithDraftFilesToDelete.getVersions()) {
            for (FileMetadata fileMetadata : datasetVersion.getFileMetadatas()) {
                DataFile datafile = fileMetadata.getDataFile();
                if (datafile != null) {
                    solrIdsOfFilesToDelete.add(solrDocIdentifierFile + datafile.getId() + draftSuffix);
                }
            }

        }
        return solrIdsOfFilesToDelete;
    }

    private List<String> findSolrDocIdsForFilesToDelete(Dataset dataset, IndexableDataset.DatasetState state) {
        List<String> solrIdsOfFilesToDelete = new ArrayList<>();
        for (DataFile file : dataset.getFiles()) {
            solrIdsOfFilesToDelete.add(solrDocIdentifierFile + file.getId() + state.getSuffix());
        }
        return solrIdsOfFilesToDelete;
    }

    private String removeMultipleSolrDocs(List<String> docIds) {
        IndexResponse indexResponse = solrIndexService.deleteMultipleSolrIds(docIds);
        return indexResponse.toString();
    }

    private String determinePublishedDatasetSolrDocId(Dataset dataset) {
        return IndexableObject.IndexableTypes.DATASET.getName() + "_" + dataset.getId() + IndexableDataset.DatasetState.PUBLISHED.getSuffix();
    }

    private String determineDeaccessionedDatasetId(Dataset dataset) {
        return IndexableObject.IndexableTypes.DATASET.getName() + "_" + dataset.getId() + IndexableDataset.DatasetState.DEACCESSIONED.getSuffix();
    }

    private String removeDeaccessioned(Dataset dataset) {
        StringBuilder result = new StringBuilder();
        String deleteDeaccessionedResult = removeSolrDocFromIndex(determineDeaccessionedDatasetId(dataset));
        result.append(deleteDeaccessionedResult);
        List<String> docIds = findSolrDocIdsForFilesToDelete(dataset, IndexableDataset.DatasetState.DEACCESSIONED);
        String deleteFilesResult = removeMultipleSolrDocs(docIds);
        result.append(deleteFilesResult);
        return result.toString();
    }

    private String removePublished(Dataset dataset) {
        StringBuilder result = new StringBuilder();
        String deletePublishedResult = removeSolrDocFromIndex(determinePublishedDatasetSolrDocId(dataset));
        result.append(deletePublishedResult);
        List<String> docIds = findSolrDocIdsForFilesToDelete(dataset, IndexableDataset.DatasetState.PUBLISHED);
        String deleteFilesResult = removeMultipleSolrDocs(docIds);
        result.append(deleteFilesResult);
        return result.toString();
    }

    private String getDesiredCardState(Map<DatasetVersion.VersionState, Boolean> desiredCards) {
        /*
          @todo make a JVM option to enforce sanity checks? Call it dev=true?
         */
        boolean sanityCheck = true;
        if (sanityCheck) {
            Set<DatasetVersion.VersionState> expected = new HashSet<>();
            expected.add(DatasetVersion.VersionState.DRAFT);
            expected.add(DatasetVersion.VersionState.RELEASED);
            expected.add(DatasetVersion.VersionState.DEACCESSIONED);
            if (!desiredCards.keySet().equals(expected)) {
                throw new RuntimeException("Mismatch between expected version states (" + expected + ") and version states passed in (" + desiredCards.keySet() + ")");
            }
        }
        return "Desired state for existence of cards: " + desiredCards + "\n";
    }

    private boolean stale(DvObject dvObject) {
        Timestamp indexTime = dvObject.getIndexTime();
        Timestamp modificationTime = dvObject.getModificationTime();
        if (indexTime == null) {
            return true;
        } else {
            return indexTime.before(modificationTime);
        }
    }

    private List<Long> findDvObjectInSolrOnly(String type) throws SearchException {
        SolrQuery solrQuery = new SolrQuery();
        solrQuery.setQuery("*");
        solrQuery.setRows(Integer.MAX_VALUE);
        solrQuery.addFilterQuery(SearchFields.TYPE + ":" + type);
        List<Long> dvObjectInSolrOnly = new ArrayList<>();
        QueryResponse queryResponse;
        try {
            queryResponse = solrServer.query(solrQuery);
        } catch (SolrServerException | IOException ex) {
            throw new SearchException("Error searching Solr for " + type, ex);
        }
        SolrDocumentList results = queryResponse.getResults();
        for (SolrDocument solrDocument : results) {
            Object idObject = solrDocument.getFieldValue(SearchFields.ENTITY_ID);
            if (idObject != null) {
                try {
                    long id = (Long) idObject;
                    DvObject dvobject = dvObjectService.findDvObject(id);
                    if (dvobject == null) {
                        dvObjectInSolrOnly.add(id);
                    }
                } catch (ClassCastException ex) {
                    throw new SearchException("Found " + SearchFields.ENTITY_ID + " but error casting " + idObject + " to long", ex);
                }
            }
        }
        return dvObjectInSolrOnly;
    }

    private List<String> findFilesOfParentDataset(long parentDatasetId) throws SearchException {
        SolrQuery solrQuery = new SolrQuery();
        solrQuery.setQuery("*");
        solrQuery.setFields(SearchFields.ID);
        solrQuery.setRows(Integer.MAX_VALUE);
        solrQuery.addFilterQuery(SearchFields.PARENT_ID + ":" + parentDatasetId);
        //  todo "files" should be a constant
        solrQuery.addFilterQuery(SearchFields.TYPE + ":" + SearchObjectType.FILES.getSolrValue());
        List<String> dvObjectInSolrOnly = new ArrayList<>();
        QueryResponse queryResponse;
        try {
            queryResponse = solrServer.query(solrQuery);
        } catch (SolrServerException | IOException ex) {
            throw new SearchException("Error searching Solr for dataset parent id " + parentDatasetId, ex);
        }
        SolrDocumentList results = queryResponse.getResults();
        for (SolrDocument solrDocument : results) {
            Object idObject = solrDocument.getFieldValue(SearchFields.ID);
            if (idObject != null) {
                String id = (String) idObject;
                dvObjectInSolrOnly.add(id);
            }
        }
        return dvObjectInSolrOnly;
    }

    private Set<Locale> getConfiguredLocales() {
        return systemConfig.getConfiguredLocales().keySet().stream()
                .map(Locale::forLanguageTag)
                .collect(Collectors.toSet());
    }
}
