package edu.harvard.iq.dataverse.search.index;

import com.google.common.collect.Lists;
import edu.harvard.iq.dataverse.DvObjectServiceBean;
import edu.harvard.iq.dataverse.persistence.DvObject;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.datafile.FileMetadata;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion.VersionState;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;

import javax.ejb.Stateless;
import javax.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@Stateless
public class PermissionsSolrDocFactory {

    private static final Logger logger = Logger.getLogger(SolrIndexServiceBean.class.getCanonicalName());

    private SolrPermissionsFinder solrPermissionsFinder;
    private DvObjectServiceBean dvObjectService;

    // -------------------- CONSTRUCTORS --------------------

    @Deprecated
    public PermissionsSolrDocFactory() {
        // JEE requirement
    }

    @Inject
    public PermissionsSolrDocFactory(SolrPermissionsFinder solrPermissionsFinder,
                                     DvObjectServiceBean dvObjectService) {
        this.solrPermissionsFinder = solrPermissionsFinder;
        this.dvObjectService = dvObjectService;
    }

    // -------------------- LOGIC --------------------

    /**
     * Returns {@link PermissionsSolrDoc}s for all dvobjects
     * currently in database.
     */
    public List<PermissionsSolrDoc> determinePermissionsDocsOnAll() {
        List<PermissionsSolrDoc> definitionPoints = new ArrayList<>();
        List<DvObject> all = dvObjectService.findAll();

        for (DvObject dvObject : all) {
            logger.info("determining definition points for dvobject id " + dvObject.getId());
            if (dvObject.isInstanceofDataFile()) {
                definitionPoints.addAll(constructDatafileSolrDocsFromDataset((Dataset) dvObject.getOwner()));
            } else {
                definitionPoints.addAll(determinePermissionsDocsOnSelfOnly(dvObject));
            }
        }

        return definitionPoints;
    }

    /**
     * Returns {@link PermissionsSolrDoc}s for dataset and datafiles attached to that dataset
     */
    public List<PermissionsSolrDoc> determinePermissionsDocsForDatasetWithDataFiles(Dataset dataset) {
        List<PermissionsSolrDoc> definitionPoints = new ArrayList<>();
        definitionPoints.addAll(determinePermissionsDocsOnSelfOnly(dataset));
        definitionPoints.addAll(constructDatafileSolrDocsFromDataset(dataset));
        return definitionPoints;
    }

    /**
     * Returns {@link PermissionsSolrDoc}s for single dvobject
     */
    public List<PermissionsSolrDoc> determinePermissionsDocsOnSelfOnly(DvObject dvObject) {
        if (dvObject == null) {
            return new ArrayList<>();
        }

        if (dvObject.isInstanceofDataverse()) {
            return Lists.newArrayList(constructDataverseSolrDoc((Dataverse) dvObject));
        } else if (dvObject.isInstanceofDataset()) {
            return constructDatasetSolrDocs((Dataset) dvObject);
        } else if (dvObject.isInstanceofDataFile()) {
            return constructDatafileSolrDocs((DataFile) dvObject);
        } else {
            logger.info("Unexpected DvObject: " + dvObject.getClass().getName());
            return new ArrayList<>();
        }
    }

    // -------------------- PRIVATE --------------------

    /**
     * @todo should this method return a List? The equivalent methods for
     * datasets and files return lists.
     */
    private PermissionsSolrDoc constructDataverseSolrDoc(Dataverse dataverse) {
        SolrPermissions permissions = solrPermissionsFinder.findDataversePerms(dataverse);

        Long noDatasetVersionForDataverses = null;
        PermissionsSolrDoc dvDoc = new PermissionsSolrDoc(dataverse.getId(), IndexServiceBean.solrDocIdentifierDataverse + dataverse.getId(),
                noDatasetVersionForDataverses, dataverse.getName(), permissions);
        return dvDoc;
    }

    private List<PermissionsSolrDoc> constructDatasetSolrDocs(Dataset dataset) {
        List<PermissionsSolrDoc> solrDocs = new ArrayList<>();

        for (DatasetVersion version : solrPermissionsFinder.extractVersionsForPermissionIndexing(dataset)) {

            PermissionsSolrDoc datasetSolrDoc = makeDatasetSolrDoc(version);
            solrDocs.add(datasetSolrDoc);
        }
        return solrDocs;
    }

    private List<PermissionsSolrDoc> constructDatafileSolrDocs(DataFile dataFile) {
        List<PermissionsSolrDoc> datafileSolrDocs = new ArrayList<>();

        for (DatasetVersion datasetVersionFileIsAttachedTo : solrPermissionsFinder.extractVersionsForPermissionIndexing(dataFile.getOwner())) {

            String solrId = buildDatafilePermissionSolrId(dataFile.getId(), datasetVersionFileIsAttachedTo.getVersionState());
            SolrPermissions perms = solrPermissionsFinder.findFileMetadataPermsFromDatasetVersion(datasetVersionFileIsAttachedTo);

            PermissionsSolrDoc dataFileSolrDoc = new PermissionsSolrDoc(dataFile.getId(), solrId,
                    datasetVersionFileIsAttachedTo.getId(), dataFile.getDisplayName(), perms);
            datafileSolrDocs.add(dataFileSolrDoc);
        }

        return datafileSolrDocs;
    }

    private List<PermissionsSolrDoc> constructDatafileSolrDocsFromDataset(Dataset dataset) {
        List<PermissionsSolrDoc> datafileSolrDocs = new ArrayList<>();
        for (DatasetVersion datasetVersionFileIsAttachedTo : solrPermissionsFinder.extractVersionsForPermissionIndexing(dataset)) {

            SolrPermissions permissions = solrPermissionsFinder.findFileMetadataPermsFromDatasetVersion(datasetVersionFileIsAttachedTo);

            for (FileMetadata fileMetadata : datasetVersionFileIsAttachedTo.getFileMetadatas()) {
                Long fileId = fileMetadata.getDataFile().getId();
                String solrId = buildDatafilePermissionSolrId(fileId, datasetVersionFileIsAttachedTo.getVersionState());

                PermissionsSolrDoc dataFileSolrDoc = new PermissionsSolrDoc(fileId, solrId, datasetVersionFileIsAttachedTo.getId(),
                        fileMetadata.getLabel(), permissions);
                logger.fine("adding fileid " + fileId);
                datafileSolrDocs.add(dataFileSolrDoc);
            }
        }
        return datafileSolrDocs;
    }


    private PermissionsSolrDoc makeDatasetSolrDoc(DatasetVersion version) {
        String solrId = buildDatasetPermissionSolrId(version);
        String name = version.getParsedTitle();
        SolrPermissions permissions = solrPermissionsFinder.findDatasetVersionPerms(version);

        return new PermissionsSolrDoc(version.getDataset().getId(), solrId, version.getId(), name, permissions);
    }

    private String buildDatasetPermissionSolrId(DatasetVersion version) {
        String solrIdStart = IndexServiceBean.solrDocIdentifierDataset + version.getDataset().getId().toString();
        String solrIdEnd = getDatasetOrDataFileSolrEnding(version.getVersionState());
        String solrId = solrIdStart + solrIdEnd;
        return solrId;
    }

    private String buildDatafilePermissionSolrId(long datafileId, VersionState datasetVersionStateFileIsAttachedTo) {
        String solrIdStart = IndexServiceBean.solrDocIdentifierFile + datafileId;
        String solrIdEnd = getDatasetOrDataFileSolrEnding(datasetVersionStateFileIsAttachedTo);
        String solrId = solrIdStart + solrIdEnd;
        return solrId;
    }

    private String getDatasetOrDataFileSolrEnding(DatasetVersion.VersionState versionState) {
        if (versionState.equals(DatasetVersion.VersionState.RELEASED)) {
            return "";
        } else if (versionState.equals(DatasetVersion.VersionState.DRAFT)) {
            return IndexServiceBean.draftSuffix;
        } else if (versionState.equals(DatasetVersion.VersionState.DEACCESSIONED)) {
            return IndexServiceBean.deaccessionedSuffix;
        } else {
            return "_unexpectedDatasetVersion";
        }
    }
}
