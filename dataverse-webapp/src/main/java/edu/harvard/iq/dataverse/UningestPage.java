package edu.harvard.iq.dataverse;

import edu.harvard.iq.dataverse.common.BundleUtil;
import edu.harvard.iq.dataverse.ingest.UningestInfoService;
import edu.harvard.iq.dataverse.ingest.UningestService;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetRepository;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.util.FileUtil;
import edu.harvard.iq.dataverse.util.SystemConfig;
import org.apache.commons.lang.StringUtils;
import org.omnifaces.cdi.ViewScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@ViewScoped
@Named("UningestPage")
public class UningestPage implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(UningestPage.class);
    private UningestService uningestService;
    private DatasetRepository datasetRepository;
    private DataverseSession dataverseSession;
    private DataverseRequestServiceBean dataverseRequestService;
    private PermissionsWrapper permissionsWrapper;
    private PermissionServiceBean permissionService;
    private SystemConfig systemConfig;
    private UningestInfoService uningestInfoService;

    private List<UningestableItem> uningestableFiles = new ArrayList<>();
    private List<UningestableItem> selectedFiles = new ArrayList<>();

    private Long datasetId;
    private Dataset dataset;

    // -------------------- GETTERS --------------------

    public Long getDatasetId() {
        return datasetId;
    }

    public Dataset getDataset() {
        return dataset;
    }

    public List<UningestableItem> getUningestableFiles() {
        return uningestableFiles;
    }

    public List<UningestableItem> getSelectedFiles() {
        return selectedFiles;
    }

    // -------------------- CONSTRUCTORS --------------------

    public UningestPage() { }

    @Inject
    public UningestPage(UningestService uningestService, DatasetRepository datasetRepository,
                        DataverseSession dataverseSession, PermissionsWrapper permissionsWrapper,
                        SystemConfig systemConfig, UningestInfoService uningestInfoService,
                        PermissionServiceBean permissionServiceBean,
                        DataverseRequestServiceBean dataverseRequestService) {
        this.uningestService = uningestService;
        this.datasetRepository = datasetRepository;
        this.dataverseSession = dataverseSession;
        this.permissionsWrapper = permissionsWrapper;
        this.systemConfig = systemConfig;
        this.uningestInfoService = uningestInfoService;
        this.permissionService = permissionServiceBean;
        this.dataverseRequestService = dataverseRequestService;
    }

    // -------------------- LOGIC --------------------

    public String init() {
        if (datasetId == null) {
            return permissionsWrapper.notFound();
        }
        this.dataset = datasetRepository.getById(datasetId);
        if (!permissionsWrapper.canCurrentUserUpdateDataset(dataset)
                || systemConfig.isReadonlyMode()) {
            return permissionsWrapper.notAuthorized();
        }
        if (permissionService.checkEditDatasetLockNonThrowing(dataset, dataverseRequestService.getDataverseRequest())) {
            return permissionsWrapper.notAuthorized();
        }
        
        DatasetVersion version = dataset.getLatestVersion();
        if (!version.isDraft()) {
            return permissionsWrapper.notFound();
        }
        uningestableFiles.addAll(prepareItemList());
        selectedFiles.clear();
        return StringUtils.EMPTY;
    }

    public void uningest() {
        if (selectedFiles.isEmpty() || !dataverseSession.getUser().isAuthenticated()) {
            return;
        }

        AuthenticatedUser user = (AuthenticatedUser) dataverseSession.getUser();
        List<String> uningestFailedFileNames = new ArrayList<>();
        selectedFiles.forEach(toUningest -> {
            try {
                uningestService.uningest(toUningest.getDataFile(), user);
            } catch (Exception e) {
                log.error("Could not uningest data file: {}", toUningest.getDataFile().getId(), e);
                uningestFailedFileNames.add(toUningest.getFileName());
            }
        });
        uningestableFiles = prepareItemList();
        selectedFiles.clear();

        if (!uningestFailedFileNames.isEmpty()) {
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    BundleUtil.getStringFromBundle("uningest.error"),
                    uningestFailedFileNames.stream().collect(Collectors.joining(", ", "[", "]. "))));
        }
    }

    public String cancel() {
        return "/dataset.xhtml?persistentId=" + dataset.getGlobalId().asString() + "&version=DRAFT&faces-redirect=true";
    }

    // -------------------- PRIVATE --------------------

    private List<UningestableItem> prepareItemList() {
        return uningestInfoService.listUningestableFiles(dataset).stream()
                .map(UningestableItem::fromDatafile)
                .collect(Collectors.toList());
    }

    // -------------------- SETTERS --------------------

    public void setDatasetId(Long datasetId) {
        this.datasetId = datasetId;
    }

    public void setSelectedFiles(List<UningestableItem> selectedFiles) {
        this.selectedFiles = selectedFiles;
    }

    // -------------------- INNER CLASSES --------------------

    public static class UningestableItem {
        private DataFile dataFile;
        private String fileName;
        private String originalFormat;
        private String md5;
        private String unf;

        // -------------------- GETTERS --------------------

        public DataFile getDataFile() {
            return dataFile;
        }

        public String getFileName() {
            return fileName;
        }

        public String getOriginalFormat() {
            return originalFormat;
        }

        public String getMd5() {
            return md5;
        }

        public String getUnf() {
            return unf;
        }

        // -------------------- LOGIC --------------------

        public static UningestableItem fromDatafile(DataFile file) {
            UningestableItem item = new UningestableItem();
            item.dataFile = file;
            item.fileName = file.getFileMetadata().getLabel();
            item.originalFormat = extractAndFormatExtension(file);
            item.md5 = file.getChecksumType() == DataFile.ChecksumType.MD5
                    ? file.getChecksumValue() : StringUtils.EMPTY;
            item.unf = file.getUnf();
            return item;
        }

        // -------------------- PRIVATE --------------------

        public static String extractAndFormatExtension(DataFile file) {
            String extension = FileUtil.generateOriginalExtension(file.isTabularData()
                    ? file.getDataTable().getOriginalFileFormat()
                    : file.getContentType());
            return extension.replaceFirst("\\.", StringUtils.EMPTY).toUpperCase();
        }
    }
}
