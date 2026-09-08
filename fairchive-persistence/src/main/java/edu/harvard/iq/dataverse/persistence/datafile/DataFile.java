package edu.harvard.iq.dataverse.persistence.datafile;

import static edu.harvard.iq.dataverse.common.BundleUtil.getStringFromBundle;
import static edu.harvard.iq.dataverse.persistence.datafile.license.FileTermsOfUse.TermsOfUseType.RESTRICTED;
import static java.lang.Boolean.TRUE;
import static java.util.stream.Collectors.toList;
import static javax.persistence.CascadeType.MERGE;
import static javax.persistence.CascadeType.PERSIST;
import static javax.persistence.CascadeType.REMOVE;
import static org.eclipse.persistence.annotations.BatchFetchType.JOIN;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.NamedNativeQueries;
import javax.persistence.NamedNativeQuery;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.validation.constraints.Pattern;

import org.eclipse.persistence.annotations.BatchFetch;
import org.hibernate.validator.constraints.NotBlank;

import com.google.gson.annotations.Expose;

import edu.harvard.iq.dataverse.common.FileSizeUtil;
import edu.harvard.iq.dataverse.common.FriendlyFileTypeUtil;
import edu.harvard.iq.dataverse.common.files.mime.MimeTypes;
import edu.harvard.iq.dataverse.persistence.DvObject;
import edu.harvard.iq.dataverse.persistence.datafile.ingest.IngestReport;
import edu.harvard.iq.dataverse.persistence.datafile.ingest.IngestRequest;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.guestbook.GuestbookResponse;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;

@NamedNativeQueries({
    @NamedNativeQuery(name = "Datafile.findDataForSolrResults2", query =
            DataFile.FIND_DATA_FOR_SOLR_RESULTS_QUERY_BASE + "WHERE t0.ID IN (?, ?)"),
    @NamedNativeQuery(name = "Datafile.findDataForSolrResults6", query =
            DataFile.FIND_DATA_FOR_SOLR_RESULTS_QUERY_BASE + "WHERE t0.ID IN (?, ?, ?, ?, ?, ?)"),
    @NamedNativeQuery(name = "Datafile.findDataForSolrResults10", query =
            DataFile.FIND_DATA_FOR_SOLR_RESULTS_QUERY_BASE + "WHERE t0.ID IN (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
})
@Entity
@Table(indexes = {@Index(columnList = "ingeststatus"),
        @Index(columnList = "checksumvalue"),
        @Index(columnList = "contenttype")
})
public class DataFile extends DvObject implements Comparable<DataFile> {
    private static final long serialVersionUID = 1L;

    public static final String TARGET_URL = "/file.xhtml?persistentId=";
    public static final char INGEST_STATUS_NONE = 'A';
    public static final char INGEST_STATUS_SCHEDULED = 'B';
    public static final char INGEST_STATUS_INPROGRESS = 'C';
    public static final char INGEST_STATUS_ERROR = 'D';

    public static final Long ROOT_DATAFILE_ID_DEFAULT = -1L;
    
    static final String FIND_DATA_FOR_SOLR_RESULTS_QUERY_BASE =
            "SELECT t0.ID, t0.CREATEDATE, " +
            "t0.PUBLICATIONDATE, t0.PREVIEWIMAGEAVAILABLE, t0.STORAGEIDENTIFIER, t0.AUTHORITY, t0.PROTOCOL, t0.IDENTIFIER, " +
            "t1.CONTENTTYPE, t1.FILESIZE, t1.INGESTSTATUS, t1.CHECKSUMVALUE, t1.CHECKSUMTYPE, " +
            "t2.ID, t2.IDENTIFIER, t2.AUTHORITY, t2.STORAGEIDENTIFIER, t3.ID, t3.UNF, t3.CASEQUANTITY, t3.VARQUANTITY " +
            "FROM DVOBJECT t0 JOIN DATAFILE t1 ON t0.ID = t1.ID JOIN DVOBJECT t2 ON t0.OWNER_ID = t2.ID " +
            "LEFT JOIN DATATABLE t3 ON t3.DATAFILE_ID = t0.ID ";

    @Expose
    @NotBlank
    @Column(nullable = false)
    @Pattern(regexp = "^.*/.*$", message = "{contenttype.slash}")
    private String contentType;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ChecksumType checksumType;

    /**
     * Examples include "f622da34d54bdc8ee541d6916ac1c16f" as an MD5 value or
     * "3a484dfdb1b429c2e15eb2a735f1f5e4d5b04ec6" as a SHA-1 value"
     */
    @Column(nullable = false)
    private String checksumValue;

    // start: FILE REPLACE ATTRIBUTES

    /** For the initial version of a file, this will be equivalent to the ID
     * Default is -1 until the intial id is generated
     */
    @Expose
    @Column(nullable = false)
    private Long rootDataFileId;

    /** null for initial version; subsequent versions will point to the previous file */
    @Expose
    @Column
    private Long previousDataFileId;
    // end: FILE REPLACE ATTRIBUTES

    @Expose
    @Column
    private Long filesize; // Number of bytes in file. Allows 0 and null, negative numbers not permitted

    @Expose
    @Column(columnDefinition = "TEXT", nullable = true, name = "prov_entityname")
    private String provEntityName;

    //    Tabular (formerly "subsettable") data files have DataTable objects
    //    associated with them:

    @OneToOne(mappedBy = "dataFile", cascade = {REMOVE, MERGE, PERSIST})
    @BatchFetch(JOIN)
    private DataTable dataTable;

    @OneToMany(mappedBy = "dataFile", cascade = {REMOVE, MERGE, PERSIST}, orphanRemoval = true)
    private List<IngestReport> ingestReports;

    @OneToOne(mappedBy = "dataFile", cascade = {REMOVE, MERGE, PERSIST})
    @BatchFetch(JOIN)
    private IngestRequest ingestRequest;

    @OneToMany(mappedBy = "dataFile", cascade = {REMOVE, MERGE, PERSIST}, orphanRemoval = true)
    private List<DataFileTag> dataFileTags = new ArrayList<>();

    @OneToMany(mappedBy = "dataFile", cascade = {REMOVE, MERGE, PERSIST}, orphanRemoval = true)
    private List<FileMetadata> fileMetadatas;

    @OneToMany(mappedBy = "dataFile", cascade = {REMOVE, MERGE, PERSIST})
    private List<GuestbookResponse> guestbookResponses;

    @OneToOne(mappedBy = "thumbnailFile")
    @BatchFetch(JOIN)
    private Dataset thumbnailForDataset;

    @ManyToMany
    @JoinTable(name = "fileaccessrequests",
            joinColumns = @JoinColumn(name = "datafile_id"),
            inverseJoinColumns = @JoinColumn(name = "authenticated_user_id"))
    private List<AuthenticatedUser> fileAccessRequesters;

    @Column
    private Long uncompressedSize = 0L;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private IngestType ingestType = IngestType.NON;

    private char ingestStatus = INGEST_STATUS_NONE;

    // -------------------- CONSTRUCTORS --------------------

    public DataFile() {
        this.fileMetadatas = new ArrayList<>();
        initFileReplaceAttributes();
    }

    public DataFile(String contentType) {
        this();
        this.contentType = contentType;
    }

    // -------------------- GETTERS --------------------
    @Override
    public DvObject getPermissionRoot() {
    	return getOwner();
    }
    
    @Override
    public String getAuthorString() {
        return this.getOwner().getLatestVersion().getAuthorsStr();
    }
    @Override
    public String getTargetUrl() {
        return TARGET_URL;
    }

    public List<GuestbookResponse> getGuestbookResponses() {
        return guestbookResponses;
    }

    public DataTable getDataTable() {
        return dataTable;
    }

    public List<DataFileTag> getTags() {
        return dataFileTags;
    }

    public List<FileMetadata> getFileMetadatas() {
        return fileMetadatas;
    }

    public IngestRequest getIngestRequest() {
        return ingestRequest;
    }

    public String getContentType() {
        return contentType;
    }
    
	public String getFileClass() {
		// order of if's is important
		if (isTabularData() || isTabular()) {
			return "tabular";
		}
		if (isDocument()) {
			return "document";
		}
		if (isImage()) {
			return "image";
		}
		if (isVideo()) {
			return "video";
		}
		if (isAudio()) {
			return "audio";
		}
		if (isCode()) {
			return "code";
		}
		if (isAstro()) {
			return "astro";
		}
		if (isNetwork()) {
			return "network";
		}
		if (isFilePackage()) {
			return "package";
		}
		if (isShapefileType()) {
			return "geodata";
		}
		return "other";
	}
    
    private boolean isVideo() {
    	return MimeTypes.isVideo(this.contentType);
    }
    
    private boolean isAudio() {
    	return MimeTypes.isAudio(this.contentType);
    }
    
    private boolean isCode() {
    	return MimeTypes.isCode(this.contentType);
    			
    }
    
    private boolean isDocument() {
    	return MimeTypes.isDocument(this.contentType);
    			
    }
    
    private boolean isAstro() {
    	return MimeTypes.isAstro(this.contentType);
    }
    
    private boolean isTabular() {
    	return MimeTypes.isTabular(this.contentType);
    }
    
    private boolean isNetwork() {
    	return MimeTypes.isNetwork(this.contentType);
    }
    
    public boolean isReplacementFile() {
        return this.previousDataFileId != null;
    }
    

    public ChecksumType getChecksumType() {
        return checksumType;
    }

    public String getChecksumValue() {
        return this.checksumValue;
    }

    public IngestType getIngestType() {
        return this.ingestType;
    }

    public void setIngestType(final IngestType type) {
        this.ingestType = type;
    }
    
    
    public String getIngestTypeStr() {
        return this.ingestType.toString();
    }
    
    public void setIngestTypeStr(final String type) {
        this.ingestType = type != null 
                ? IngestType.valueOf(type)
                : IngestType.NON;
    }

    public int getIngestStatus() {
        return ingestStatus;
    }
    
    public boolean hasBeenIngested() {
        if(isImage()) {
            return this.ingestType != IngestType.NON;
        } else {
            return getIngestStatus() != INGEST_STATUS_NONE || isTabularData();
        }
    }

    public Boolean getIncludedInIngest() {
        return this.ingestType != IngestType.NON;
    }
    
    public boolean isIngestableAsTabular() {
    	return isTabular();
    }

    public Dataset getThumbnailForDataset() {
        return thumbnailForDataset;
    }

    public List<AuthenticatedUser> getFileAccessRequesters() {
        return fileAccessRequesters;
    }

    public String getProvEntityName() {
        return provEntityName;
    }

    public Long getRootDataFileId() {
        return rootDataFileId;
    }

    public Long getPreviousDataFileId() {
        return previousDataFileId;
    }

    public Long getUncompressedSize() {
        return uncompressedSize;
    }

    public List<IngestReport> getIngestReports() {
        return ingestReports;
    }

    @Override
    public boolean isEffectivelyPermissionRoot() {
        return false;
    }

    @Override
    public Dataset getOwner() {
        return (Dataset) super.getOwner();
    }

    // -------------------- LOGIC --------------------

    public List<String> getTagLabels() {
        return this.dataFileTags.stream().map(DataFileTag::getTypeLabel)
                .collect(toList());
    }

    public void addTag(final DataFileTag tag) {
        this.dataFileTags.add(tag);
    }

    public IngestReport getIngestReport() {
        return ingestReports != null && !ingestReports.isEmpty()
                ? ingestReports.get(0) : null;
    }

    public void setIngestReport(IngestReport report) {
        if (ingestReports == null) {
            ingestReports = new ArrayList<>();
        } else {
            ingestReports.clear();
        }
        ingestReports.add(report);
    }

    public boolean isTabularData() {
        return dataTable != null;
    }

    public String getOriginalFileFormat() {
        return isTabularData() && getDataTable() != null
                ? getDataTable().getOriginalFileFormat() : null;
    }

    public Long getOriginalFileSize() {
        return isTabularData() && getDataTable() != null
                ? getDataTable().getOriginalFileSize() : null;
    }

    @Override
    public boolean isAncestorOf(DvObject other) {
        return equals(other);
    }
    
    public boolean isPublicIn(final DatasetVersion datasetVersion) {
        if (getOwner().equals(datasetVersion.getDataset())) {
            boolean released = datasetVersion.isReleased();
            boolean embargoed = getOwner().hasActiveEmbargo();
            boolean restricted = getFileMetadata().getTermsOfUse()
                    .getTermsOfUseType() == RESTRICTED;
            return released && !embargoed && !restricted;
        } else {
            return false;
        }
    }
    
    public boolean isNonPublicOrNotIngestedTsvFile(final DatasetVersion datasetVersion) {
        final boolean isTsvAltContentType = MimeTypes.TAB_SEPARATED_VALUES
                .equals(isTabularData() ? MimeTypes.TAB_SEPARATED_VALUES : getContentType());

        return isTsvAltContentType && (!isPublicIn(datasetVersion) || !isTabularData());
    }

    /**
     * A user-friendly version of the "original format":
     */
    public String getOriginalFormatLabel() {
        return FriendlyFileTypeUtil.getUserFriendlyOriginalType(this);
    }

    public String getFriendlyType() {
        return FriendlyFileTypeUtil.getUserFriendlyFileType(this);
    }

    public String getFriendlyTypeForIndex(Locale locale) {
        return FriendlyFileTypeUtil.getUserFriendlyFileType(this, locale);
    }

    public String getDescription() {
        FileMetadata fmd = getLatestFileMetadata();
        return fmd != null ? fmd.getDescription() : null;
    }

    public void setDescription(String description) {
        FileMetadata fmd = getLatestFileMetadata();
        if (fmd != null) {
            fmd.setDescription(description);
        }
    }

    public FileMetadata getFileMetadata() {
        return getLatestFileMetadata();
    }

    public FileMetadata getLatestFileMetadata() {
        if (this.fileMetadatas.isEmpty()) {
            return null;
        } else if (this.fileMetadatas.size() == 1) { // for newly added or harvested
            return this.fileMetadatas.get(0);
        } else {
            FileMetadata result = this.fileMetadatas.get(0);
            for (final FileMetadata fileMetadata : this.fileMetadatas) {
                if (fileMetadata.getDatasetVersion().isDraft()) {
                    return fileMetadata;
                } else if (fileMetadata.refersToNewerDatasetVersionThan(result)) {
                    result = fileMetadata;
                }
            }
            return result;
        }
    }

    public long getFilesize() {
        // -1 means "unknown"
        return this.filesize != null ? this.filesize : -1;
    }

    public void setFilesize(long filesize) {
        if (filesize < 0) {
            return;
        }
        this.filesize = filesize;
    }

    /**
     * Converts the stored size of the file in bytes to
     * a user-friendly value in KB, MB or GB.
     */
    public String getFriendlySize() {
        return FileSizeUtil.bytesToHumanReadable(filesize);
    }

    public String getOriginalChecksumType() {
        return getStringFromBundle("file.originalChecksumType", this.checksumType.toString());
    }

    // Does the contentType indicate a shapefile?
    public boolean isShapefileType() {
        return MimeTypes.isGeoShape(this.contentType);
    }
    
    public boolean isShapeOrTabularWithGeospatialTag() {
        return isShapefileType() || isTabularData() && hasGeospatialTag();
    }
    

	public boolean isImage() {
		if (isAstro()) {
			// Some browsers (Chrome?) seem to identify FITS files as mime
			// type "image/fits" on upload; this is both incorrect (the official
			// mime type for FITS is "application/fits", and problematic: then
			// the file is identified as an image, and the page will attempt to
			// generate a preview - which of course is going to fail...
			return false;
		} else {
			// a pdf file is an "image" for practical purposes (we will attempt to
			// generate thumbnails and previews for them)
			return MimeTypes.isImage(this.contentType) || isPdf();
		}
	}
    
    public boolean isPdf() {
        return MimeTypes.isPDF(this.contentType);
    }

    public boolean isFilePackage() {
        return MimeTypes.isDataversePackage(this.contentType);
    }
    
    /**
     * This method tells you if thumbnail generation is *supported*
     * on this type of file. i.e., if true, it does not guarantee that a thumbnail
     * can/will be generated; but it means that we can try.
     */
    public boolean isThumbnailSupported() {
    	
        if (isHarvested()) {
            return false;
        }
        if (!isStored()) {
            return false;
        }
        
        if(this.contentType == null) {
        	return false;
        }
        
        if(isImage()) {
        	return true;
        }
        
        if(isShapeOrTabularWithGeospatialTag()) {
        	return true;
        }
        
        return false;
    }

    public boolean isIngestScheduled() {
        return ingestStatus == INGEST_STATUS_SCHEDULED;
    }

    public boolean isIngestInProgress() {
        return ingestStatus == INGEST_STATUS_SCHEDULED || ingestStatus == INGEST_STATUS_INPROGRESS;
    }

    public boolean isIngestProblem() {
        return ingestStatus == INGEST_STATUS_ERROR;
    }

    public void setIngestScheduled() {
        ingestStatus = INGEST_STATUS_SCHEDULED;
    }

    public void setIngestInProgress() {
        ingestStatus = INGEST_STATUS_INPROGRESS;
    }

    public void setIngestProblem() {
        ingestStatus = INGEST_STATUS_ERROR;
    }

    public void setIngestDone() {
        ingestStatus = INGEST_STATUS_NONE;
    }

    // 8/10/2014 - Using the current "open access" url
    public String getMapItFileDownloadURL(String serverName) {
        return this.getId() != null && serverName != null
                ? serverName + "/api/access/datafile/" + this.getId() : null;
    }

    /*
     * If this is tabular data, the corresponding dataTable may have a UNF -
     * "numeric fingerprint" signature - generated:
     */
    public String getUnf() {
        // isTabularData() method above verifies that that this file
        // has a datDatable associated with it, so the line below is
        // safe, in terms of a NullPointerException:
        return this.isTabularData() ? this.getDataTable().getUnf() : null;
    }

    public boolean isHarvested() {
        return getStorageIdentifier().startsWith("http://")
                || this.getStorageIdentifier().startsWith("https://")
                || getOwner().isHarvested();
    }

    public String getRemoteArchiveURL() {
        return isHarvested() ? this.getOwner().getRemoteArchiveURL() : null;
    }

    @Override
    protected String toStringExtras() {
        final FileMetadata fmd = getLatestFileMetadata();
        return fmd != null ? "label:".concat(fmd.getLabel()) : "label:[no metadata]";
    }

    @Override
    public <T> T accept(Visitor<T> v) {
        return v.visit(this);
    }

    @Override
    public String getDisplayName() {
        return getLatestFileMetadata().getLabel();
    }

    @Override
    public int compareTo(DataFile other) {
        return this.getDisplayName().toUpperCase().compareTo(other.getDisplayName().toUpperCase());
    }

    /**
     * Check if the Geospatial Tag has been assigned to this file
     */
    public boolean hasGeospatialTag() {
        return this.dataFileTags.stream().anyMatch(DataFileTag::isGeospatialTag);
    }

    public String getPublicationDateFormattedYYYYMMDD() {
        return getPublicationDate() != null ? format(getPublicationDate()) : null;
    }

    public String getCreateDateFormattedYYYYMMDD() {
        return getCreateDate() != null ? format(getCreateDate()) : null;
    }

    private static String format(final Timestamp ts) {
        return new SimpleDateFormat("yyyy-MM-dd").format(ts);
    }
    
    public boolean supportsPickingEncoding() {
        return MimeTypes.supportsPickingEncoding(this.contentType);
    }

    public boolean supportsInclusionOfLabels() {
        return MimeTypes.supportsInclusionOfLabels(this.contentType);
    }
    
    public boolean supportsAdvancedIngestOptions() {
        return supportsPickingEncoding() || supportsInclusionOfLabels();
    }

    public boolean isSelectivelyIngestable() {
        return MimeTypes.isSelectivelyIngestable(this.contentType) || isImage();
    }
    
    public boolean canBeUningested() {
        // File from draft version can be uningested if it:
        // was not published yet (ie. it has only one metadata set, but we assume that
        //     the file is from the latest, draft version – which is NOT checked here);
        if(getFileMetadatas().size() != 1) {
            return false;
        }
        // is of XLSX, CSV or TSV type or image;
        if(! isSelectivelyIngestable()) {
            return false;
        }
        // has been ingested (successfully or not).
        
        return hasBeenIngested();
    }

    // -------------------- PRIVATE --------------------

    /**
     * All constructors should use this method
     * to initialize this file replace attributes
     */
    private void initFileReplaceAttributes() {
        this.rootDataFileId = ROOT_DATAFILE_ID_DEFAULT;
        this.previousDataFileId = null;
    }

    // -------------------- SETTERS --------------------

    public void setRootDataFileId(Long rootDataFileId) {
        this.rootDataFileId = rootDataFileId;
    }

    public void setPreviousDataFileId(Long previousDataFileId) {
        this.previousDataFileId = previousDataFileId;
    }

    public void setUncompressedSize(Long uncompressedSize) {
        this.uncompressedSize = uncompressedSize;
    }

    public void setDataTable(DataTable dataTable) {
        this.dataTable = dataTable;
    }

    public void setTags(final List<DataFileTag> dataFileTags) {
        this.dataFileTags = dataFileTags != null ? dataFileTags : new ArrayList<>();
    }

    public void setFileMetadatas(List<FileMetadata> fileMetadatas) {
        this.fileMetadatas = fileMetadatas;
    }

    public void setIngestRequest(IngestRequest ingestRequest) {
        this.ingestRequest = ingestRequest;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public void setOwner(Dataset dataset) {
        super.setOwner(dataset);
    }

    public void setChecksumType(ChecksumType checksumType) {
        this.checksumType = checksumType;
    }

    public void setChecksumValue(String checksumValue) {
        this.checksumValue = checksumValue;
    }

    public void setIngestStatus(char ingestStatus) {
        this.ingestStatus = ingestStatus;
    }

    public void setAsThumbnailForDataset(Dataset dataset) {
        this.thumbnailForDataset = dataset;
    }

    public void setIncludedInIngest(Boolean includedInIngest) {
        this.ingestType = includedInIngest == TRUE ? IngestType.TAB : IngestType.NON;
    }

    public void setFileAccessRequesters(List<AuthenticatedUser> fileAccessRequesters) {
        this.fileAccessRequesters = fileAccessRequesters;
    }

    public void setProvEntityName(String name) {
        this.provEntityName = name;
    }

    public void setGuestbookResponses(List<GuestbookResponse> guestbookResponses) {
        this.guestbookResponses = guestbookResponses;
    }

    // -------------------- equals & hashCode --------------------

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof DataFile
            ? Objects.equals(getId(), ((DataFile) other).getId())
            : false;
    }
    

    // -------------------- INNER CLASSES --------------------

    /**
     * End users will see "SHA-1" (with a hyphen) rather than "SHA1" in the GUI
     * and API but in the "datafile" table we persist "SHA1" (no hyphen) for
     * type safety (using keys of the enum). In the "setting" table, we persist
     * "SHA-1" (with a hyphen) to match the GUI and the "Algorithm Name" list at
     * https://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html#MessageDigest
     * <p>
     * The list of types should be limited to the list above in the technote
     * because the string gets passed into MessageDigest.getInstance() and you
     * can't just pass in any old string.
     */
    public enum ChecksumType {

        MD5("MD5"),
        SHA1("SHA-1"),
        SHA256("SHA-256"),
        SHA512("SHA-512");

        private final String text;

        ChecksumType(final String text) {
            this.text = text;
        }

        public static ChecksumType fromString(final String text) {
            for (final ChecksumType checksumType : values()) {
                if (checksumType.text.equals(text)) {
                    return checksumType;
                }
            }
            throw new IllegalArgumentException(
                    "ChecksumType must be one of these values: " + values() + '.');
        }

        @Override
        public String toString() {
            return this.text;
        }
    }
    
    public enum IngestType {
        // names need to have 3 characters to fit database column
        OCR,
        HTR,
        TAB,
        NON;
    }
}