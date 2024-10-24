package edu.harvard.iq.dataverse.persistence.dataset;

import edu.harvard.iq.dataverse.common.BundleUtil;
import edu.harvard.iq.dataverse.persistence.AlternativePersistentIdentifier;
import edu.harvard.iq.dataverse.persistence.DvObject;
import edu.harvard.iq.dataverse.persistence.DvObjectContainer;
import edu.harvard.iq.dataverse.persistence.GlobalId;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.datafile.DataFileCategory;
import edu.harvard.iq.dataverse.persistence.datafile.FileMetadata;
import edu.harvard.iq.dataverse.persistence.datafile.license.FileTermsOfUse;
import edu.harvard.iq.dataverse.persistence.dataverse.link.DatasetLinkingDataverse;
import edu.harvard.iq.dataverse.persistence.guestbook.Guestbook;
import edu.harvard.iq.dataverse.persistence.harvest.HarvestStyle;
import edu.harvard.iq.dataverse.persistence.harvest.HarvestingClient;
import io.vavr.control.Option;
import org.apache.commons.lang3.StringUtils;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.NamedStoredProcedureQuery;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.OrderBy;
import javax.persistence.ParameterMode;
import javax.persistence.StoredProcedureParameter;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author skraffmiller
 */
@NamedQueries({
        @NamedQuery(name = "Dataset.findByIdentifier",
                query = "SELECT d FROM Dataset d WHERE d.identifier=:identifier"),
        @NamedQuery(name = "Dataset.findByIdentifierAuthorityProtocol",
                query = "SELECT d FROM Dataset d WHERE d.identifier=:identifier AND d.protocol=:protocol AND d.authority=:authority"),
})

/*
    Below is the stored procedure for getting a numeric value from a database
    sequence. Used when the Dataverse is (optionally) configured to use
    incremental numeric values for dataset ids, instead of the default
    random strings.

    Unfortunately, there's no standard EJB way of handling sequences. So in the
    past we would simply use a NativeQuery to call a proprietary Postgres
    sequence query. A better way of handling this however is to define any
    proprietary SQL functionality outside of the application, in the database,
    and call it using the standard JPA @StoredProcedureQuery.

    The identifier sequence and the stored procedure for accessing it are currently
    implemented with PostgresQL "CREATE SEQUENCE ..." and "CREATE FUNCTION ...";
    (we explain how to create these in the installation documentation and supply
    a script). If necessary, it can be implemented using other SQL flavors -
    without having to modify the application code.
            -- L.A. 4.6.2
*/
@NamedStoredProcedureQuery(
        name = "Dataset.generateIdentifierAsSequentialNumber",
        procedureName = "generateIdentifierAsSequentialNumber",
        parameters = {
                @StoredProcedureParameter(mode = ParameterMode.OUT, type = Integer.class, name = "identifier")
        }
)
@Entity
@Table(indexes = {
        @Index(columnList = "guestbook_id"),
        @Index(columnList = "thumbnailfile_id")})
public class Dataset extends DvObjectContainer {

    public static final String TARGET_URL = "/citation?persistentId=";
    private static final long serialVersionUID = 1L;

    /**
     * DataFile tags presented by default
     */
    private enum BuiltInTag {
        CODE ("code"),
        DOCUMENTATION ("documentation"),
        DATA ("data"),
        CODEBOOK ("codebook"),
        QUESTIONNAIRE ("questionnaire"),
        DESCRIPTIVE_STATISTICS ("descriptiveStats");

        private String name;

        BuiltInTag(String name) {
            this.name = name;
        }

        public String getLocaleName() {
            return BundleUtil.getStringFromBundle("dataset.category." + StringUtils.trim(name));
        }

        public static Stream<BuiltInTag> stream() {
            return Stream.of(BuiltInTag.values());
        }
    }

    @OneToMany(mappedBy = "owner", cascade = {CascadeType.MERGE, CascadeType.PERSIST})
    @OrderBy("id")
    private List<DataFile> files = new ArrayList<>();

    @OneToMany(mappedBy = "dataset", orphanRemoval = true, cascade = {CascadeType.REMOVE, CascadeType.MERGE, CascadeType.PERSIST})
    @OrderBy("versionNumber DESC, minorVersionNumber DESC")
    private List<DatasetVersion> versions = new ArrayList<>();

    @OneToMany(mappedBy = "dataset", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<DatasetLock> datasetLocks;

    @OneToOne(cascade = {CascadeType.MERGE, CascadeType.PERSIST})
    @JoinColumn(name = "thumbnailfile_id")
    private DataFile thumbnailFile;

    /**
     * By default, Dataverse will attempt to show unique thumbnails for datasets
     * based on images that have been uploaded to them. Setting this to true
     * will result in a generic dataset thumbnail appearing instead.
     */
    private boolean useGenericThumbnail;

    @OneToOne(cascade = {CascadeType.MERGE, CascadeType.PERSIST})
    @JoinColumn(name = "guestbook_id", unique = false, nullable = true, insertable = true, updatable = true)
    private Guestbook guestbook;

    @Temporal(TemporalType.TIMESTAMP)
    private Date lastChangeForExporterTime;

    @Temporal(TemporalType.TIMESTAMP)
    private Date embargoDate;

    @OneToMany(mappedBy = "dataset", cascade = {CascadeType.REMOVE, CascadeType.MERGE, CascadeType.PERSIST})
    private List<DatasetLinkingDataverse> datasetLinkingDataverses;

    public List<DatasetLinkingDataverse> getDatasetLinkingDataverses() {
        return datasetLinkingDataverses;
    }

    public void setDatasetLinkingDataverses(List<DatasetLinkingDataverse> datasetLinkingDataverses) {
        this.datasetLinkingDataverses = datasetLinkingDataverses;
    }

    @OneToMany(mappedBy = "dataset", orphanRemoval = true, cascade = {CascadeType.REMOVE, CascadeType.MERGE, CascadeType.PERSIST})
    private List<DataFileCategory> dataFileCategories = null;

    @ManyToOne
    @JoinColumn(name = "citationDateDatasetFieldType_id")
    private DatasetFieldType citationDateDatasetFieldType;

    public DatasetFieldType getCitationDateDatasetFieldType() {
        return citationDateDatasetFieldType;
    }

    public void setCitationDateDatasetFieldType(DatasetFieldType citationDateDatasetFieldType) {
        this.citationDateDatasetFieldType = citationDateDatasetFieldType;
    }

    public Dataset() {
        DatasetVersion datasetVersion = new DatasetVersion();
        datasetVersion.setDataset(this);
        datasetVersion.setVersionState(DatasetVersion.VersionState.DRAFT);
        datasetVersion.setFileMetadatas(new ArrayList<>());
        datasetVersion.setVersionNumber(1L);
        datasetVersion.setMinorVersionNumber(0L);
        versions.add(datasetVersion);
    }

    /**
     * Checks whether {@code this} dataset is locked for a given reason.
     *
     * @param reason the reason we test for.
     * @return {@code true} if the data set is locked for {@code reason}.
     */
    public boolean isLockedFor(DatasetLock.Reason reason) {
        for (DatasetLock lock : getLocks()) {
            if (lock.getReason() == reason) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether {@code this} dataset is locked for a given reason (given as String value).
     *
     * @param reason the reason we test for.
     * @return {@code true} if the data set is locked for {@code reason}.
     */
    public boolean isLockedFor(String reason) {
        for (DatasetLock lock : getLocks()) {
            if (lock.getReason().name().equals(reason)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Retrieves the dataset lock for the passed reason.
     *
     * @return the dataset lock, or {@code null}.
     */
    public DatasetLock getLockFor(DatasetLock.Reason reason) {
        for (DatasetLock lock : getLocks()) {
            if (lock.getReason() == reason) {
                return lock;
            }
        }
        return null;
    }

    public Set<DatasetLock> getLocks() {
        // lazy set creation
        if (datasetLocks == null) {
            datasetLocks = new HashSet<>();
        }
        return datasetLocks;
    }

    /**
     * JPA use only!
     */
    void setLocks(Set<DatasetLock> datasetLocks) {
        this.datasetLocks = datasetLocks;
    }

    public void addLock(DatasetLock datasetLock) {
        getLocks().add(datasetLock);
    }

    public void removeLock(DatasetLock aDatasetLock) {
        getLocks().remove(aDatasetLock);
    }

    public boolean isLocked() {
        return !getLocks().isEmpty();
    }

    public Guestbook getGuestbook() {
        return guestbook;
    }

    public void setGuestbook(Guestbook guestbook) {
        this.guestbook = guestbook;
    }

    /**
     * Time of last changes that could affect exporters results, but are not related to specific dataset version.
     * Example: Guestbook assigning and embargo date change can happen after dataset
     * was published and without generating new dataset version.
     * This operations can affect the final result of some exporters which
     * should be reflected in OAI incremental imports.
     */
    public Option<Date> getLastChangeForExporterTime() {
        return Option.of(lastChangeForExporterTime);
    }

    public void setLastChangeForExporterTime(Date lastchangeforexportertime) {
        this.lastChangeForExporterTime = lastchangeforexportertime;
    }

    public Option<Date> getEmbargoDate() {
        return Option.of(embargoDate);
    }

    public void setEmbargoDate(Date embargoDate) {
        this.embargoDate = embargoDate;
    }

    public String getPersistentURL() {
        return new GlobalId(this).toURL().toString();
    }

    public List<DataFile> getFiles() {
        return files;
    }

    public void setFiles(List<DataFile> files) {
        this.files = files;
    }

    /**
     * Returns true if dataset is deaccessioned.
     * Dataset is considered as deaccessioned if all of it's versions are deaccessioned.
     */
    public boolean isDeaccessioned() {
        return versions.stream().allMatch(DatasetVersion::isDeaccessioned);
    }

    public boolean hasActiveEmbargo() {
        return getEmbargoDate().isDefined() && Instant.now().isBefore(getEmbargoDate().get().toInstant());
    }

    public boolean hasEverBeenPublished() {
        return getVersions().size() > 1 || getLatestVersion().getVersionState() != DatasetVersion.VersionState.DRAFT;
    }

    public DatasetVersion getLatestVersion() {
        return getVersions().get(0);
    }

    public DatasetVersion getLatestVersionForCopy() {
        for (DatasetVersion testDsv : getVersions()) {
            if (testDsv.isReleased() || testDsv.isArchived()) {
                return testDsv;
            }
        }
        return getVersions().get(0);
    }

    public List<DatasetVersion> getVersions() {
        return versions;
    }

    public void setVersions(List<DatasetVersion> versions) {
        this.versions = versions;
    }

    private DatasetVersion createNewDatasetVersion() {
        DatasetVersion dsv = new DatasetVersion();
        dsv.setVersionState(DatasetVersion.VersionState.DRAFT);
        dsv.setFileMetadatas(new ArrayList<>());
        dsv.setDataset(this);
        DatasetVersion latestVersion;

        // if the latest version has values get them copied over
        latestVersion = getLatestVersionForCopy();

        if (latestVersion.getUNF() != null) {
            dsv.setUNF(latestVersion.getUNF());
        }

        if (latestVersion.getDatasetFields() != null && !latestVersion.getDatasetFields().isEmpty()) {
            dsv.setDatasetFields(DatasetFieldUtil.copyDatasetFields(latestVersion.getDatasetFields()));
        }

        for (FileMetadata fm : latestVersion.getFileMetadatas()) {
            FileMetadata newFm = new FileMetadata();
            newFm.setCategories(new LinkedList<>(fm.getCategories()));
            newFm.setDescription(fm.getDescription());
            newFm.setLabel(fm.getLabel());
            newFm.setDirectoryLabel(fm.getDirectoryLabel());
            newFm.setDataFile(fm.getDataFile());
            newFm.setDatasetVersion(dsv);
            newFm.setProvFreeForm(fm.getProvFreeForm());
            newFm.setDisplayOrder(fm.getDisplayOrder());

            FileTermsOfUse termsOfUse = fm.getTermsOfUse();
            FileTermsOfUse clonedTermsOfUse = termsOfUse.createCopy();
            newFm.setTermsOfUse(clonedTermsOfUse);

            dsv.getFileMetadatas().add(newFm);
        }
        getVersions().add(0, dsv);
        return dsv;
    }

    /**
     * The "edit version" is the most recent *draft* of a dataset, and if the
     * latest version of a dataset is published, a new draft will be created.
     *
     * @return The edit version {@code this}.
     */
    public DatasetVersion getEditVersion() {
        DatasetVersion latestVersion = getLatestVersion();
        return latestVersion.isWorkingCopy() ? latestVersion : createNewDatasetVersion();
    }

    public Date getMostRecentMajorVersionReleaseDate() {
        if (isHarvested()) {
            return getVersions().get(0).getReleaseTime();
        }
        for (DatasetVersion version : getVersions()) {
            if (version.isReleased() && version.getMinorVersionNumber().equals((long) 0)) {
                return version.getReleaseTime();
            }
        }
        return null;
    }

    /**
     * Returns true if dataset contains any version with released state.
     */
    public boolean containsReleasedVersion() {
        return isReleased() && getReleasedVersion() != null;
    }

    public DatasetVersion getReleasedVersion() {
        for (DatasetVersion version : getVersions()) {
            if (version.isReleased()) {
                return version;
            }
        }
        return null;
    }

    public List<DataFileCategory> getCategories() {
        return dataFileCategories;
    }

    public void setFileCategories(List<DataFileCategory> categories) {
        this.dataFileCategories = categories;
    }

    public void addFileCategory(DataFileCategory category) {
        if (dataFileCategories == null) {
            dataFileCategories = new ArrayList<>();
        }
        dataFileCategories.add(category);
    }

    public Collection<String> getCategoriesByName() {
        Collection<String> ret = getCategoryNames();

        BuiltInTag.stream()
                .filter(tag -> !ret.contains(tag.getLocaleName()))
                .forEach(tag -> ret.add(tag.getLocaleName()));

        return ret;
    }

    public void setCategoriesByName(List<String> newCategoryNames) {
        if (newCategoryNames != null) {
            Collection<String> oldCategoryNames = getCategoryNames();

            for (String newCategoryName : newCategoryNames) {
                if (!oldCategoryNames.contains(newCategoryName)) {
                    DataFileCategory newCategory = new DataFileCategory();
                    newCategory.setName(newCategoryName);
                    newCategory.setDataset(this);
                    addFileCategory(newCategory);
                }
            }
        }
    }

    public DataFileCategory getCategoryByName(String categoryName) {
        if (categoryName != null && !categoryName.isEmpty()) {
            if (dataFileCategories != null) {
                for (DataFileCategory dataFileCategory : dataFileCategories) {
                    if (categoryName.equals(dataFileCategory.getName())) {
                        return dataFileCategory;
                    }
                }
            }

            DataFileCategory newCategory = new DataFileCategory();
            newCategory.setName(categoryName);
            newCategory.setDataset(this);
            addFileCategory(newCategory);

            return newCategory;
        }
        return null;
    }

    private Collection<String> getCategoryNames() {
        return dataFileCategories != null
                ? dataFileCategories.stream()
                    .map(DataFileCategory::getName)
                    .collect(Collectors.toList())
                : new ArrayList<String>();
    }

    public Path getFileSystemDirectory(String filesRootDirectory) {
        return Paths.get(filesRootDirectory, getStorageIdentifier());
    }

    public String getAlternativePersistentIdentifier() {
        List<String> result = new ArrayList<>();
        if (getAlternativePersistentIndentifiers() != null && !getAlternativePersistentIndentifiers().isEmpty()) {
            for (AlternativePersistentIdentifier api : getAlternativePersistentIndentifiers()) {
                result.add(api.getProtocol() + ":" +
                        api.getAuthority() + "/" +
                        api.getIdentifier());
            }
        }
        return String.join("; ", result);
    }

    public String getAuthorityForFileStorage() {
        String retVal = getAuthority();
        if (getAlternativePersistentIndentifiers() != null && !getAlternativePersistentIndentifiers().isEmpty()) {
            for (AlternativePersistentIdentifier api : getAlternativePersistentIndentifiers()) {
                retVal = api.getAuthority();
            }
        }
        return retVal;
    }

    public String getIdentifierForFileStorage() {
        String retVal = getIdentifier();
        if (getAlternativePersistentIndentifiers() != null && !getAlternativePersistentIndentifiers().isEmpty()) {
            for (AlternativePersistentIdentifier api : getAlternativePersistentIndentifiers()) {
                retVal = api.getIdentifier();
            }
        }
        return retVal;
    }

    public String getNextMajorVersionString() {
        // Never need to get the next major version for harvested studies.
        if (isHarvested()) {
            throw new IllegalStateException();
        }
        for (DatasetVersion dv : getVersions()) {
            if (!dv.isWorkingCopy()) {
                return (dv.getVersionNumber().intValue() + 1) + ".0";
            }
        }
        return "1.0";
    }

    public String getNextMinorVersionString() {
        // Never need to get the next minor version for harvested studies.
        if (isHarvested()) {
            throw new IllegalStateException();
        }
        for (DatasetVersion dv : getVersions()) {
            if (!dv.isWorkingCopy()) {
                return dv.getVersionNumber().intValue() + "."
                        + (dv.getMinorVersionNumber().intValue() + 1);
            }
        }
        return "1.0";
    }

    public Integer getVersionNumber() {
        for (DatasetVersion dv : getVersions()) {
            if (!dv.isWorkingCopy()) {
                return dv.getVersionNumber().intValue();
            }
        }
        return 1;
    }

    public Integer getMinorVersionNumber() {
        for (DatasetVersion dv : getVersions()) {
            if (!dv.isWorkingCopy()) {
                return dv.getMinorVersionNumber().intValue();
            }
        }
        return 0;
    }

    public String getPublicationDateFormattedYYYYMMDD() {
        return getPublicationDate() != null
                ? new SimpleDateFormat("yyyy-MM-dd").format(getPublicationDate())
                : null;
    }

    public DataFile getThumbnailFile() {
        return thumbnailFile;
    }

    public void setThumbnailFile(DataFile thumbnailFile) {
        this.thumbnailFile = thumbnailFile;
    }

    public boolean isUseGenericThumbnail() {
        return useGenericThumbnail;
    }

    public void setUseGenericThumbnail(boolean useGenericThumbnail) {
        this.useGenericThumbnail = useGenericThumbnail;
    }

    @ManyToOne
    @JoinColumn(name = "harvestingClient_id")
    private HarvestingClient harvestedFrom;

    public HarvestingClient getHarvestedFrom() {
        return this.harvestedFrom;
    }

    public void setHarvestedFrom(HarvestingClient harvestingClientConfig) {
        this.harvestedFrom = harvestingClientConfig;
    }

    public boolean isHarvested() {
        return this.harvestedFrom != null;
    }

    private String harvestIdentifier;

    public String getHarvestIdentifier() {
        return harvestIdentifier;
    }

    public void setHarvestIdentifier(String harvestIdentifier) {
        this.harvestIdentifier = harvestIdentifier;
    }

    public String getRemoteArchiveURL() {
        if (!isHarvested()) {
            return null;
        }
        if (HarvestStyle.DATAVERSE.equals(getHarvestedFrom().getHarvestStyle())) {
            return getHarvestedFrom().getArchiveUrl() + "/dataset.xhtml?persistentId=" + getGlobalIdString();
        } else if (HarvestStyle.VDC.equals(getHarvestedFrom().getHarvestStyle())) {
            String rootArchiveUrl = getHarvestedFrom().getHarvestingUrl();
            int c = rootArchiveUrl.indexOf("/OAIHandler");
            return c > 0
                    ? rootArchiveUrl.substring(0, c) + "/faces/study/StudyPage.xhtml?globalId=" + getGlobalIdString()
                    : null;
        } else if (HarvestStyle.ICPSR.equals(getHarvestedFrom().getHarvestStyle())) {
            // For the ICPSR, it turns out that the best thing to do is to
            // rely on the DOI to send the user to the right landing page for
            // the study:
            //String icpsrId = identifier;
            //return getOwner().getHarvestingClient().getArchiveUrl() + "/icpsrweb/ICPSR/studies/"+icpsrId+"?q="+icpsrId+"&amp;searchSource=icpsr-landing";
            return "http://doi.org/" + getAuthority() + "/" + getIdentifier();
        } else if (HarvestStyle.DOI.equals(getHarvestedFrom().getHarvestStyle())) {
            return getHarvestedFrom().getArchiveUrl() + "/" + getAuthority() + "/" + getIdentifier();
        } else if (HarvestStyle.NESSTAR.equals(getHarvestedFrom().getHarvestStyle())) {
            String nServerURL = getHarvestedFrom().getArchiveUrl();
            // chop any trailing slashes in the server URL - or they will result
            // in multiple slashes in the final URL pointing to the study
            // on server of origin; Nesstar doesn't like it, apparently.
            nServerURL = nServerURL.replaceAll("/*$", "");
            String nServerURLencoded = nServerURL;
            nServerURLencoded = nServerURLencoded.replace(":", "%3A").replace("/", "%2F");
            //SEK 09/13/18
            return nServerURL + "/webview/?mode=documentation&submode=abstract&studydoc=" + nServerURLencoded + "%2Fobj%2FfStudy%2F"
                    + getIdentifier() + "&top=yes";
        } else if (HarvestStyle.ROPER.equals(getHarvestedFrom().getHarvestStyle())) {
            return getHarvestedFrom().getArchiveUrl() + "/CFIDE/cf/action/catalog/abstract.cfm?archno=" + getIdentifier();
        } else if (HarvestStyle.HGL.equals(getHarvestedFrom().getHarvestStyle())) {
            // a bit of a hack, true.
            // HGL documents, when turned into Dataverse studies/datasets
            // all 1 datafile; the location ("storage identifier") of the file
            // is the URL pointing back to the HGL GUI viewer. This is what
            // we will display for the dataset URL.  -- L.A.
            // TODO: create a 4.+ ticket for a cleaner solution.
            List<DataFile> dataFiles = getFiles();
            if (dataFiles != null && dataFiles.size() == 1 && dataFiles.get(0) != null) {
                String hglUrl = dataFiles.get(0).getStorageIdentifier();
                if (hglUrl != null && hglUrl.matches("^http.*")) {
                    return hglUrl;
                }
            }
            return getHarvestedFrom().getArchiveUrl();
        } else {
            return getHarvestedFrom().getArchiveUrl();
        }
    }

    @Override
    public boolean equals(Object object) {
        // TODO: Warning - this method won't work in the case the id fields are not set
        return object instanceof Dataset
                && Objects.equals(getId(), ((Dataset) object).getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }

    @Override
    public <T> T accept(Visitor<T> v) {
        return v.visit(this);
    }

    @Override
    public String getDisplayName() {
        DatasetVersion dsv = getReleasedVersion();
        return dsv != null
                ? dsv.getParsedTitle()
                : getLatestVersion().getParsedTitle();
    }

    @Override
    public boolean isPermissionRoot() {
        return false;
    }

    @Override
    public boolean isAncestorOf(DvObject other) {
        return equals(other) || equals(other.getOwner());
    }
}