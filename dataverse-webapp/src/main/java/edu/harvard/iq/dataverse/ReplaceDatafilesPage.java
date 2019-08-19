package edu.harvard.iq.dataverse;

import edu.harvard.iq.dataverse.common.BundleUtil;
import edu.harvard.iq.dataverse.datasetutility.AddReplaceFileHelper;
import edu.harvard.iq.dataverse.datasetutility.FileReplaceException;
import edu.harvard.iq.dataverse.datasetutility.FileReplacePageHelper;
import edu.harvard.iq.dataverse.ingest.IngestServiceBean;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.user.Permission;
import edu.harvard.iq.dataverse.util.JsfHelper;
import org.apache.commons.lang.StringUtils;

import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.faces.view.ViewScoped;
import javax.inject.Named;
import java.io.Serializable;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@ViewScoped
@Named("ReplaceDatafilesPage")
public class ReplaceDatafilesPage implements Serializable {

    private static final Logger logger = Logger.getLogger(ReplaceDatafilesPage.class.getCanonicalName());

    private PermissionsWrapper permissionsWrapper;
    private PermissionServiceBean permissionService;
    private DataFileServiceBean datafileService;
    private IngestServiceBean ingestService;
    private EjbDataverseEngine commandEngine;
    private DataverseRequestServiceBean dvRequestService;

    private Dataset dataset;
    private DataFile fileToBeReplaced;
    private FileReplacePageHelper fileReplacePageHelper;

    // -------------------- GETTERS --------------------

    public Dataset getDataset() {
        return dataset;
    }

    public FileReplacePageHelper getFileReplacePageHelper() {
        return fileReplacePageHelper;
    }

    // -------------------- LOGIC --------------------

    public String init() {
        String permissionError = checkPermissions();

        if (!permissionError.isEmpty()) {
            return permissionError;
        }

        DataFile fileToReplace = loadFileToReplace();
        if (fileToReplace == null) {
            return permissionsWrapper.notFound();
        }


        AddReplaceFileHelper addReplaceFileHelper = new AddReplaceFileHelper(dvRequestService.getDataverseRequest(),
                                                                             ingestService,
                                                                             datafileService,
                                                                             permissionService,
                                                                             commandEngine);

        fileReplacePageHelper = new FileReplacePageHelper(addReplaceFileHelper,
                                                          dataset,
                                                          fileToReplace);

        fileToBeReplaced = fileToReplace;

        return StringUtils.EMPTY;
    }

    public String saveReplacement() {
        try {
            return saveReplacementFile();
        } catch (FileReplaceException ex) {
            String errMsg = ex.getMessage();
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, BundleUtil.getStringFromBundle("dataset.save.fail"), errMsg));
            logger.log(Level.SEVERE, "Dataset save failed for replace operation: {0}", errMsg);
            return StringUtils.EMPTY;
        }
    }

    /**
     * Save for File Replace operations
     *
     * @return
     * @throws FileReplaceException
     */
    public String saveReplacementFile() throws FileReplaceException {

        if (!fileReplacePageHelper.wasPhase1Successful()) {
            throw new FileReplaceException("Save should only be called when a replacement file has been chosen.  (Phase 1 has to have completed)");

        }

        if (fileReplacePageHelper.runSaveReplacementFile_Phase2()) {
            JsfHelper.addFlashSuccessMessage(BundleUtil.getStringFromBundle("file.message.replaceSuccess"));

            return returnToFileLandingPageAfterReplace(fileReplacePageHelper.getFirstNewlyAddedFile());
        } else {

            String errMsg = fileReplacePageHelper.getErrorMessages();

            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, BundleUtil.getStringFromBundle("dataset.save.fail"), errMsg));
            logger.severe("Dataset save failed for replace operation: " + errMsg);
            return StringUtils.EMPTY;
        }

    }

    // -------------------- PRIVATE --------------------

    private String checkPermissions() {

        if (dataset == null || dataset.isHarvested()) {
            return permissionsWrapper.notFound();
        }

        DatasetVersion workingVersion = dataset.getEditVersion();

        if (!workingVersion.isDraft()) {
            return permissionsWrapper.notFound();
        }

        if (!permissionService.on(dataset).has(Permission.EditDataset)) {
            return permissionsWrapper.notAuthorized();
        }

        return StringUtils.EMPTY;
    }

    private String returnToFileLandingPageAfterReplace(DataFile newFile) {

        if (newFile == null) {
            throw new NullPointerException("newFile cannot be null!");
        }
        //Long datasetVersionId = newFile.getOwner().getLatestVersion().getId();
        return "/file.xhtml?fileId=" + newFile.getId() + "&version=DRAFT&faces-redirect=true";
    }


    private DataFile loadFileToReplace() {

        Map<String, String> params = FacesContext.getCurrentInstance().
                getExternalContext().getRequestParameterMap();

        String fid = params.get("fid");

        if (StringUtils.isNumeric(fid)) {
            return datafileService.find(Long.parseLong(fid));
        }
        return null;

    }

    // -------------------- SETTERS --------------------

    public void setDataset(Dataset dataset) {
        this.dataset = dataset;
    }
}
