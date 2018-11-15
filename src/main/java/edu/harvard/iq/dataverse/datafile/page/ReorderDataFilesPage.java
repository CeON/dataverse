package edu.harvard.iq.dataverse.datafile.page;

import edu.harvard.iq.dataverse.Dataset;
import edu.harvard.iq.dataverse.DatasetServiceBean;
import edu.harvard.iq.dataverse.DatasetVersionServiceBean;
import edu.harvard.iq.dataverse.FileMetadata;
import edu.harvard.iq.dataverse.PermissionServiceBean;
import edu.harvard.iq.dataverse.PermissionsWrapper;
import edu.harvard.iq.dataverse.authorization.Permission;

import javax.ejb.EJB;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;
import java.util.Optional;

@ViewScoped
@Named("ReorderDataFilesPage")
public class ReorderDataFilesPage implements java.io.Serializable {

    @EJB
    private DatasetServiceBean datasetService;
    @EJB
    private DatasetVersionServiceBean datasetVersionService;
    @EJB
    private PermissionServiceBean permissionService;
    @Inject
    private PermissionsWrapper permissionsWrapper;

    private Dataset dataset = new Dataset();
    private List<FileMetadata> fileMetadatas;
    private FileMetadataOrder fileMetadatasCopy;

    /**
     * Initializes all properties requested by frontend.
     * Like files for dataset with specific id.
     *
     * @return error if something goes wrong or null if success.
     */
    public String init() {

        Optional<Dataset> fetchedDataset = fetchDataset(dataset.getId());

        if (!fetchedDataset.isPresent() || fetchedDataset.get().isHarvested()) {
            return permissionsWrapper.notFound();
        }

        fileMetadatas = fetchedDataset.get().getLatestVersion().getFileMetadatasSorted();

        // for some reason the original fileMetadatas is causing null if used anywhere else. For
        fileMetadatasCopy = new FileMetadataOrder(fileMetadatas);

        if (!permissionService.on(dataset).has(Permission.EditDataset)) {
            return permissionsWrapper.notAuthorized();
        }

        return null;
    }

    /**
     * Reorders files display order if any were reordered, saves the changes to the database
     * and returns to the previous page.
     *
     * @return uri to previous page
     */
    public String saveFileOrder() {

        datasetVersionService.saveFileMetadata(fileMetadatasCopy.changes());

        return returnToPreviousPage();
    }

    /**
     * Method responsible for retrieving dataset from database.
     *
     * @param id
     * @return optional
     */
    private Optional<Dataset> fetchDataset(Long id) {
        return Optional.ofNullable(id)
                .map(datasetId -> this.dataset = datasetService.find(datasetId));
    }

    /**
     * returns you to the dataset page.
     *
     * @return uri
     */
    public String returnToPreviousPage() {
        return "/dataset.xhtml?persistentId=" + dataset.getGlobalId().asString() + "&version=DRAFT&faces-redirect=true";
    }

    public Dataset getDataset() {
        return dataset;
    }

    public List<FileMetadata> getFileMetadatas() {
        return fileMetadatas;
    }

    public void setDataset(Dataset dataset) {
        this.dataset = dataset;
    }

    public void setFileMetadatas(List<FileMetadata> fileMetadatas) {
        this.fileMetadatas = fileMetadatas;
    }
}
