package edu.harvard.iq.dataverse.persistence.datafile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.Expose;
import edu.harvard.iq.dataverse.common.DateUtil;
import edu.harvard.iq.dataverse.persistence.JpaEntity;
import edu.harvard.iq.dataverse.persistence.datafile.license.FileTermsOfUse;
import edu.harvard.iq.dataverse.persistence.datafile.license.TermsOfUseForm;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.persistence.annotations.BatchFetch;
import org.eclipse.persistence.annotations.BatchFetchType;
import org.hibernate.validator.constraints.NotBlank;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.OrderBy;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.Version;
import javax.validation.constraints.Pattern;
import java.io.Serializable;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * @author skraffmiller
 */
@Table(indexes = {@Index(columnList = "datafile_id"), @Index(columnList = "datasetversion_id")})
@Entity
public class FileMetadata implements JpaEntity<Long>, Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(FileMetadata.class.getCanonicalName());


    @Expose
    @Pattern(regexp = "^[^:<>;#/\"\\*\\|\\?\\\\]*$",
            message = "{filename.illegalCharacters}")
    @NotBlank(message = "{filename.blank}")
    @Column(nullable = false)
    private String label = "";

    @Pattern(regexp = "|[^/\\\\]|^[^/\\\\]+.*[^/\\\\]+$",
            message = "{directoryname.illegalCharacters}")
    @Expose
    @Column(nullable = true)
    private String directoryLabel;
    @Column(columnDefinition = "TEXT")
    private String description = "";

    @ManyToOne
    @JoinColumn(nullable = false)
    private DatasetVersion datasetVersion;

    @ManyToOne
    @JoinColumn(nullable = false)
    @BatchFetch(BatchFetchType.JOIN)
    private DataFile dataFile;

    /**
     * There are two types of provenance types and this "free-form" type is
     * represented in the GUI as text box the user can type into. The other type
     * is based on PROV-JSON from the W3C.
     */
    @Column(columnDefinition = "TEXT", nullable = true, name = "prov_freeform")
    private String provFreeForm;

    private int displayOrder;

    @OneToOne(cascade = {CascadeType.MERGE, CascadeType.PERSIST, CascadeType.REMOVE}, orphanRemoval = true)
    @JoinColumn(nullable = false, name = "termsofuse_id")
    @BatchFetch(BatchFetchType.JOIN)
    private FileTermsOfUse termsOfUse;

    /**
     * Creates a copy of {@code this}, with identical business logic fields.
     * E.g., {@link #label} would be duplicated; {@link #version} will not.
     *
     * @return A copy of {@code this}, except for the DB-related data.
     */
    public FileMetadata createCopy() {
        FileMetadata fmd = new FileMetadata();
        fmd.setCategories(new LinkedList<>(getCategories()));
        fmd.setDataFile(getDataFile());
        fmd.setDatasetVersion(getDatasetVersion());
        fmd.setDescription(getDescription());
        fmd.setLabel(getLabel());
        fmd.setDisplayOrder(getDisplayOrder());

        FileTermsOfUse termsOfUseCopy = getTermsOfUse().createCopy();
        fmd.setTermsOfUse(termsOfUseCopy);

        return fmd;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDirectoryLabel() {
        return directoryLabel;
    }

    public void setDirectoryLabel(String directoryLabel) {
        this.directoryLabel = directoryLabel;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public FileTermsOfUse getTermsOfUse() {
        return termsOfUse;
    }

    public void setTermsOfUse(FileTermsOfUse termsOfUse) {
        this.termsOfUse = termsOfUse;
    }

    /*
     * File Categories to which this version of the DataFile belongs:
     */
    @ManyToMany
    @JoinTable(indexes = {@Index(columnList = "filecategories_id"), @Index(columnList = "filemetadatas_id")})
    @OrderBy("name")
    private List<DataFileCategory> fileCategories = new ArrayList<>();

    public List<DataFileCategory> getCategories() {
        return fileCategories;
    }

    public void setCategories(List<DataFileCategory> fileCategories) {
        this.fileCategories = fileCategories;
    }

    public void addCategory(DataFileCategory category) {
        fileCategories.add(category);
    }

    /**
     * Retrieve categories
     *
     * @return
     */
    public List<String> getCategoriesByName() {
        ArrayList<String> ret = new ArrayList<>();

        if (fileCategories.isEmpty()) {
            return ret;
        }

        for (DataFileCategory fileCategory : fileCategories) {
            ret.add(fileCategory.getName());
        }

        return ret;
    }


    public JsonArrayBuilder getCategoryNamesAsJsonArrayBuilder() {

        JsonArrayBuilder builder = Json.createArrayBuilder();

        if (fileCategories.isEmpty()) {
            return builder;
        }

        for (DataFileCategory fileCategory : fileCategories) {
            builder.add(fileCategory.getName());
        }

        //fileCategories.stream()
        //              .map(x -> builder.add(x.getName()));

        return builder;

    }


    // alternative, experimental method:

    public void setCategoriesByName(List<String> newCategoryNames) {
        setCategories(null); // ?? TODO: investigate!

        if (newCategoryNames != null) {

            for (String newCategoryName : newCategoryNames) {
                // Dataset.getCategoryByName() will check if such a category
                // already exists for the parent dataset; it will be created
                // if not. The method will return null if the supplied
                // category name is null or empty. -- L.A. 4.0 beta 10
                DataFileCategory fileCategory;
                try {
                    // Using "try {}" to catch any null pointer exceptions,
                    // just in case:
                    fileCategory = this.getDatasetVersion().getDataset().getCategoryByName(newCategoryName);
                } catch (Exception ex) {
                    fileCategory = null;
                }
                if (fileCategory != null) {
                    this.addCategory(fileCategory);
                    fileCategory.addFileMetadata(this);
                }
            }
        }
    }

    /*
        note that this version only *adds* new categories, but does not
        remove the ones that has been unchecked!
    public void setCategoriesByName(List<String> newCategoryNames) {
        if (newCategoryNames != null) {
            Collection<String> oldCategoryNames = getCategoriesByName();


            for (int i = 0; i < newCategoryNames.size(); i++) {
                if (!oldCategoryNames.contains(newCategoryNames.get(i))) {
                    // Dataset.getCategoryByName() will check if such a category
                    // already exists for the parent dataset; it will be created
                    // if not. The method will return null if the supplied
                    // category name is null or empty. -- L.A. 4.0 beta 10
                    DataFileCategory fileCategory = null;
                    try {
                        // Using "try {}" to catch any null pointer exceptions,
                        // just in case:
                        fileCategory = this.getDatasetVersion().getDataset().getCategoryByName(newCategoryNames.get(i));
                    } catch (Exception ex) {
                        fileCategory = null;
                    }
                    if (fileCategory != null) {
                        this.addCategory(fileCategory);
                        fileCategory.addFileMetadata(this);
                    }
                }
            }
        }
    }
    */

    public void addCategoryByName(String newCategoryName) {
        if (newCategoryName != null && !newCategoryName.isEmpty()) {
            Collection<String> oldCategoryNames = getCategoriesByName();
            if (!oldCategoryNames.contains(newCategoryName)) {
                DataFileCategory fileCategory;
                // Dataset.getCategoryByName() will check if such a category
                // already exists for the parent dataset; it will be created
                // if not. The method will return null if the supplied
                // category name is null or empty. -- L.A. 4.0 beta 10
                try {
                    // Using "try {}" to catch any null pointer exceptions,
                    // just in case:
                    fileCategory = this.getDatasetVersion().getDataset().getCategoryByName(newCategoryName);
                } catch (Exception ex) {
                    // If we failed to obtain an existing category, we'll create a new one:
                    fileCategory = new DataFileCategory();
                    fileCategory.setName(newCategoryName);
                }


                if (fileCategory != null) {
                    logger.log(Level.FINE, "Found file category for {0}", newCategoryName);

                    this.addCategory(fileCategory);
                    fileCategory.addFileMetadata(this);
                } else {
                    logger.log(Level.INFO, "Could not find file category for {0}", newCategoryName);
                }
            } else {
                // don't do anything - this file metadata already belongs to
                // this category.
            }
        }
    }

    public Date getFileDateToDisplay() {
        Date fileDate = null;
        DataFile datafile = this.getDataFile();
        if (datafile != null) {
            boolean fileHasBeenReleased = datafile.isReleased();
            if (fileHasBeenReleased) {
                Timestamp filePublicationTimestamp = datafile.getPublicationDate();
                if (filePublicationTimestamp != null) {
                    fileDate = filePublicationTimestamp;
                }
            } else {
                Timestamp fileCreateTimestamp = datafile.getCreateDate();
                if (fileCreateTimestamp != null) {
                    fileDate = fileCreateTimestamp;
                }
            }
        }
        return fileDate;
    }

    public DatasetVersion getDatasetVersion() {
        return datasetVersion;
    }

    public void setDatasetVersion(DatasetVersion datasetVersion) {
        this.datasetVersion = datasetVersion;
    }


    public DataFile getDataFile() {
        return dataFile;
    }

    public void setDataFile(DataFile dataFile) {
        this.dataFile = dataFile;
    }


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Getter for property id.
     *
     * @return Value of property id.
     */
    public Long getId() {
        return this.id;
    }

    /**
     * Setter for property id.
     *
     * @param id New value of property id.
     */
    public void setId(Long id) {
        this.id = id;
    }


    @Version
    private Long version;

    /**
     * Getter for property version.
     *
     * @return Value of property version.
     */
    public Long getVersion() {
        return this.version;
    }

    /**
     * Setter for property version.
     *
     * @param version New value of property version.
     */
    public void setVersion(Long version) {
        this.version = version;
    }

    @Transient
    private boolean selected;

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Transient
    private FileVersionDifference fileVersionDifference;

    public FileVersionDifference getFileVersionDifference() {
        return fileVersionDifference;
    }

    public void setFileVersionDifference(FileVersionDifference fileVersionDifference) {
        this.fileVersionDifference = fileVersionDifference;
    }

    @Transient
    private String contributorNames;

    public String getContributorNames() {
        return contributorNames;
    }

    public void setContributorNames(String contributorNames) {
        this.contributorNames = contributorNames;
    }


    @Transient
    private TermsOfUseForm termsOfUseForm = new TermsOfUseForm();

    public TermsOfUseForm getTermsOfUseForm() {
        return termsOfUseForm;
    }

    public void setTermsOfUseForm(TermsOfUseForm termsOfUseForm) {
        this.termsOfUseForm = termsOfUseForm;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash += (id != null ? id.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof FileMetadata)) {
            return false;
        }
        FileMetadata other = (FileMetadata) object;

        return !((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id)));
    }

    /*
     * An experimental method for comparing 2 file metadatas *by content*; i.e.,
     * this would be for checking 2 metadatas from 2 different versions, to
     * determine if any of the actual metadata fields have changed between
     * versions.
     */
    public boolean contentEquals(FileMetadata other) {
        if (other == null) {
            return false;
        }

        if (!StringUtils.equals(getLabel(), other.getLabel())) {
            return false;
        }
        if (!StringUtils.equals(getDirectoryLabel(), other.getDirectoryLabel())) {
            return false;
        }
        if (!StringUtils.equals(getDescription(), other.getDescription())) {
            return false;
        }

        return true;
    }


    @Override
    public String toString() {
        return "edu.harvard.iq.dvn.core.study.FileMetadata[id=" + id + "]";
    }

    public static final Comparator<FileMetadata> compareByDisplayOrder = new Comparator<FileMetadata>() {
        @Override
        public int compare(FileMetadata o1, FileMetadata o2) {
            return o1.getDisplayOrder() - o2.getDisplayOrder();
        }
    };


    public String toPrettyJSON() {

        return serializeAsJSON(true);
    }

    public String toJSON() {

        return serializeAsJSON(false);
    }

    /**
     * @param prettyPrint
     * @return
     */
    private String serializeAsJSON(boolean prettyPrint) {

        JsonObject jsonObj = asGsonObject(prettyPrint);

        return jsonObj.toString();

    }


    public JsonObject asGsonObject(boolean prettyPrint) {


        GsonBuilder builder;
        if (prettyPrint) {  // Add pretty printing
            builder = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().setPrettyPrinting();
        } else {
            builder = new GsonBuilder().excludeFieldsWithoutExposeAnnotation();
        }

        builder.serializeNulls();   // correctly capture nulls
        Gson gson = builder.create();

        // serialize this object
        JsonElement jsonObj = gson.toJsonTree(this);
        jsonObj.getAsJsonObject().addProperty("id", this.getId());

        return jsonObj.getAsJsonObject();
    }

    public String getProvFreeForm() {
        return provFreeForm;
    }

    public void setProvFreeForm(String provFreeForm) {
        this.provFreeForm = provFreeForm;
    }

    public void decreaseDisplayOrder() {
        this.displayOrder--;
    }

}
