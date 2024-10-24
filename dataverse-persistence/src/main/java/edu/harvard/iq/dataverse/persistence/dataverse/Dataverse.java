package edu.harvard.iq.dataverse.persistence.dataverse;

import edu.harvard.iq.dataverse.common.BundleUtil;
import edu.harvard.iq.dataverse.common.MarkupChecker;
import edu.harvard.iq.dataverse.persistence.DvObject;
import edu.harvard.iq.dataverse.persistence.DvObjectContainer;
import edu.harvard.iq.dataverse.persistence.dataset.ControlledVocabularyValue;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetFieldType;
import edu.harvard.iq.dataverse.persistence.dataset.MetadataBlock;
import edu.harvard.iq.dataverse.persistence.dataset.Template;
import edu.harvard.iq.dataverse.persistence.dataverse.link.DatasetLinkingDataverse;
import edu.harvard.iq.dataverse.persistence.dataverse.link.DataverseLinkingDataverse;
import edu.harvard.iq.dataverse.persistence.dataverse.link.SavedSearch;
import edu.harvard.iq.dataverse.persistence.guestbook.Guestbook;
import edu.harvard.iq.dataverse.persistence.harvest.HarvestingClient;
import edu.harvard.iq.dataverse.persistence.user.DataverseRole;
import org.hibernate.validator.constraints.NotBlank;
import org.hibernate.validator.constraints.NotEmpty;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.NamedNativeQueries;
import javax.persistence.NamedNativeQuery;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.OrderBy;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * @author gdurand
 * @author mbarsinai
 */
@NamedQueries({
        @NamedQuery(name = "Dataverse.ownedObjectsById", query = "SELECT COUNT(obj) FROM DvObject obj WHERE obj.owner.id=:id"),
        @NamedQuery(name = "Dataverse.findAll", query = "SELECT d FROM Dataverse d order by d.name"),
        @NamedQuery(name = "Dataverse.findRoot", query = "SELECT d FROM Dataverse d where d.owner.id=null"),
        @NamedQuery(name = "Dataverse.findByAlias", query = "SELECT dv FROM Dataverse dv WHERE LOWER(dv.alias)=:alias"),
        @NamedQuery(name = "Dataverse.findByOwnerId", query = "select object(o) from Dataverse as o where o.owner.id =:ownerId order by o.name"),
        @NamedQuery(name = "Dataverse.filterByAlias", query = "SELECT dv FROM Dataverse dv WHERE LOWER(dv.alias) LIKE :alias order by dv.alias"),
        @NamedQuery(name = "Dataverse.filterByAliasNameAffiliation", query = "SELECT dv FROM Dataverse dv WHERE (LOWER(dv.alias) LIKE :alias) OR (LOWER(dv.name) LIKE :name) OR (LOWER(dv.affiliation) LIKE :affiliation) order by dv.alias"),
        @NamedQuery(name = "Dataverse.filterByAliasName", query = "SELECT dv FROM Dataverse dv WHERE (LOWER(dv.alias) LIKE :alias) OR (LOWER(dv.name) LIKE :name) order by dv.alias")
})
@NamedNativeQueries({
        @NamedNativeQuery(name = "Dataverse.findDataForSolrResults2", query =
                "SELECT t0.ID, t0.AFFILIATION, t0.ALIAS, t2.ALIAS " +
                "FROM DATAVERSE t0 JOIN DVOBJECT t1 ON t0.ID = t1.ID LEFT JOIN DATAVERSE t2 ON t1.OWNER_ID = t2.ID " +
                "WHERE t0.ID IN (?, ?)"),
        @NamedNativeQuery(name = "Dataverse.findDataForSolrResults6", query =
                "SELECT t0.ID, t0.AFFILIATION, t0.ALIAS, t2.ALIAS " +
                "FROM DATAVERSE t0 JOIN DVOBJECT t1 ON t0.ID = t1.ID LEFT JOIN DATAVERSE t2 ON t1.OWNER_ID = t2.ID " +
                "WHERE t0.ID IN (?, ?, ?, ?, ?, ?)"),
        @NamedNativeQuery(name = "Dataverse.findDataForSolrResults10", query =
                "SELECT t0.ID, t0.AFFILIATION, t0.ALIAS, t2.ALIAS " +
                "FROM DATAVERSE t0 JOIN DVOBJECT t1 ON t0.ID = t1.ID LEFT JOIN DATAVERSE t2 ON t1.OWNER_ID = t2.ID " +
                "WHERE t0.ID IN (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
})
@Entity
@Table(indexes = {@Index(columnList = "defaultcontributorrole_id")
        , @Index(columnList = "defaulttemplate_id")
        , @Index(columnList = "alias")
        , @Index(columnList = "affiliation")
        , @Index(columnList = "dataversetype")
        , @Index(columnList = "facetroot")
        , @Index(columnList = "guestbookroot")
        , @Index(columnList = "metadatablockroot")
        , @Index(columnList = "templateroot")
        , @Index(columnList = "permissionroot")
        , @Index(columnList = "themeroot")})
public class Dataverse extends DvObjectContainer {

    public enum DataverseType {
        RESEARCHERS, RESEARCH_PROJECTS, JOURNALS, ORGANIZATIONS_INSTITUTIONS, TEACHING_COURSES, UNCATEGORIZED, LABORATORY, RESEARCH_GROUP, DEPARTMENT
    }

    public enum FeaturedDataversesSorting {
        BY_HAND, BY_NAME_ASC, BY_NAME_DESC, BY_DATASET_COUNT
    }

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "{dataverse.name}")
    @Column(nullable = false)
    private String name;

    /**
     * @todo add @Column(nullable = false) for the database to enforce non-null
     */
    @NotBlank(message = "{dataverse.alias}")
    @Column(nullable = false, unique = true)
    @Size(max = 100, message = "{dataverse.aliasLength}")
    @Pattern.List({@Pattern(regexp = "[a-zA-Z0-9\\_\\-]*", message = "{dataverse.nameIllegalCharacters}"),
            @Pattern(regexp = ".*\\D.*", message = "{dataverse.aliasNotnumber}")})
    private String alias;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "additional_description", columnDefinition = "TEXT")
    private String additionalDescription;

    @Enumerated(EnumType.STRING)
    @NotNull(message = "{dataverse.category}")
    @Column(nullable = false)
    private DataverseType dataverseType;

    /**
     * When {@code true}, users are not granted permissions the got for parent
     * dataverses.
     */
    protected boolean permissionRoot;


    public DataverseType getDataverseType() {
        return dataverseType;
    }

    public void setDataverseType(DataverseType dataverseType) {
        this.dataverseType = dataverseType;
    }

    @Transient
    private final String uncategorizedString = "Uncategorized";

    public String getFriendlyCategoryName() {
        switch (this.dataverseType) {
            case RESEARCHERS:
                return BundleUtil.getStringFromBundle("dataverse.type.selectTab.researcher");
            case RESEARCH_PROJECTS:
                return BundleUtil.getStringFromBundle("dataverse.type.selectTab.research_project");
            case JOURNALS:
                return BundleUtil.getStringFromBundle("dataverse.type.selectTab.journal");
            case ORGANIZATIONS_INSTITUTIONS:
                return BundleUtil.getStringFromBundle("dataverse.type.selectTab.organization_or_institution");
            case TEACHING_COURSES:
                return BundleUtil.getStringFromBundle("dataverse.type.selectTab.teaching_course");
            case LABORATORY:
                return BundleUtil.getStringFromBundle("dataverse.type.selectTab.laboratory");
            case RESEARCH_GROUP:
                return BundleUtil.getStringFromBundle("dataverse.type.selectTab.research_group");
            case DEPARTMENT:
                return BundleUtil.getStringFromBundle("dataverse.type.selectTab.department");
            case UNCATEGORIZED:
                return uncategorizedString;
            default:
                return "";
        }
    }

    private String getCategoryNameForIndex() {
        switch (this.dataverseType) {
            case RESEARCHERS:
                return BundleUtil.getStringFromBundleWithLocale("dataverse.type.selectTab.researcher", Locale.ENGLISH);
            case RESEARCH_PROJECTS:
                return BundleUtil.getStringFromBundleWithLocale("dataverse.type.selectTab.research_project", Locale.ENGLISH);
            case JOURNALS:
                return BundleUtil.getStringFromBundleWithLocale("dataverse.type.selectTab.journal", Locale.ENGLISH);
            case ORGANIZATIONS_INSTITUTIONS:
                return BundleUtil.getStringFromBundleWithLocale("dataverse.type.selectTab.organization_or_institution", Locale.ENGLISH);
            case TEACHING_COURSES:
                return BundleUtil.getStringFromBundleWithLocale("dataverse.type.selectTab.teaching_course", Locale.ENGLISH);
            case LABORATORY:
                return BundleUtil.getStringFromBundleWithLocale("dataverse.type.selectTab.laboratory", Locale.ENGLISH);
            case RESEARCH_GROUP:
                return BundleUtil.getStringFromBundleWithLocale("dataverse.type.selectTab.research_group", Locale.ENGLISH);
            case DEPARTMENT:
                return BundleUtil.getStringFromBundleWithLocale("dataverse.type.selectTab.department", Locale.ENGLISH);
            case UNCATEGORIZED:
                return BundleUtil.getStringFromBundleWithLocale("dataverse.type.selectTab.uncategorized", Locale.ENGLISH);
            default:
                return "";
        }
    }

    public String getIndexableCategoryName() {
        return getCategoryNameForIndex();
    }

    private String affiliation;

    // Note: We can't have "Remove" here, as there are role assignments that refer
    //       to this role. So, adding it would mean violating a forign key contstraint.
    @OneToMany(cascade = {CascadeType.MERGE},
            fetch = FetchType.LAZY,
            mappedBy = "owner")
    private Set<DataverseRole> roles;

    @ManyToOne
    @JoinColumn(nullable = true)
    private DataverseRole defaultContributorRole;

    public DataverseRole getDefaultContributorRole() {
        return defaultContributorRole;
    }

    public void setDefaultContributorRole(DataverseRole defaultContributorRole) {
        this.defaultContributorRole = defaultContributorRole;
    }

    private boolean metadataBlockRoot;
    private boolean facetRoot;
    // By default, themeRoot should be true, as new dataverses should start with the default theme
    private boolean themeRoot = true;
    private boolean templateRoot;

    private boolean allowMessagesBanners;

    @Enumerated(EnumType.STRING)
    private FeaturedDataversesSorting featuredDataversesSorting;

    @OneToOne(mappedBy = "dataverse", cascade = {CascadeType.REMOVE, CascadeType.MERGE, CascadeType.PERSIST}, orphanRemoval = true)
    private DataverseTheme dataverseTheme;

    @OneToMany(mappedBy = "dataverse", cascade = {CascadeType.REMOVE, CascadeType.MERGE, CascadeType.PERSIST}, orphanRemoval = true)
    @OrderBy("displayOrder")
    @NotEmpty(message = "At least one contact is required.")
    private List<DataverseContact> dataverseContacts = new ArrayList<>();

    @ManyToMany(cascade = {CascadeType.MERGE})
    private List<MetadataBlock> metadataBlocks = new ArrayList<>();

    @OneToMany(mappedBy = "dataverse", cascade = {CascadeType.REMOVE, CascadeType.MERGE, CascadeType.PERSIST}, orphanRemoval = true)
    @OrderBy("displayOrder")
    private List<DataverseFacet> dataverseFacets = new ArrayList<>();

    @ManyToMany(cascade = {CascadeType.MERGE})
    @JoinTable(name = "dataverse_citationDatasetFieldTypes",
            joinColumns = @JoinColumn(name = "dataverse_id"),
            inverseJoinColumns = @JoinColumn(name = "citationdatasetfieldtype_id"))
    private List<DatasetFieldType> citationDatasetFieldTypes = new ArrayList<>();

    @ManyToMany
    @JoinTable(name = "dataversesubjects",
            joinColumns = @JoinColumn(name = "dataverse_id"),
            inverseJoinColumns = @JoinColumn(name = "controlledvocabularyvalue_id"))
    private Set<ControlledVocabularyValue> dataverseSubjects;

    @OneToMany(mappedBy = "dataverse", cascade = {CascadeType.REMOVE, CascadeType.MERGE, CascadeType.PERSIST})
    private List<DataverseFeaturedDataverse> dataverseFeaturedDataverses;

    public List<DataverseFeaturedDataverse> getDataverseFeaturedDataverses() {
        return dataverseFeaturedDataverses;
    }

    public void setDataverseFeaturedDataverses(List<DataverseFeaturedDataverse> dataverseFeaturedDataverses) {
        this.dataverseFeaturedDataverses = dataverseFeaturedDataverses;
    }

    @OneToMany(mappedBy = "featuredDataverse", cascade = {CascadeType.REMOVE, CascadeType.MERGE, CascadeType.PERSIST})
    private List<DataverseFeaturedDataverse> dataverseFeaturingDataverses;

    public List<DataverseFeaturedDataverse> getDataverseFeaturingDataverses() {
        return dataverseFeaturingDataverses;
    }

    public void setDataverseFeaturingDataverses(List<DataverseFeaturedDataverse> dataverseFeaturingDataverses) {
        this.dataverseFeaturingDataverses = dataverseFeaturingDataverses;
    }

    @OneToMany(mappedBy = "dataverse", cascade = {CascadeType.REMOVE, CascadeType.MERGE, CascadeType.PERSIST})
    private List<DataverseLinkingDataverse> dataverseLinkingDataverses;

    public List<DataverseLinkingDataverse> getDataverseLinkingDataverses() {
        return dataverseLinkingDataverses;
    }

    public void setDataverseLinkingDataverses(List<DataverseLinkingDataverse> dataverseLinkingDataverses) {
        this.dataverseLinkingDataverses = dataverseLinkingDataverses;
    }

    @OneToMany(mappedBy = "linkingDataverse", cascade = {CascadeType.REMOVE, CascadeType.MERGE, CascadeType.PERSIST})
    private List<DataverseLinkingDataverse> dataverseLinkedDataverses;

    public List<DataverseLinkingDataverse> getDataverseLinkedDataverses() {
        return dataverseLinkedDataverses;
    }

    public void setDataverseLinkedDataverses(List<DataverseLinkingDataverse> dataverseLinkedDataverses) {
        this.dataverseLinkedDataverses = dataverseLinkedDataverses;
    }

    @OneToMany(mappedBy = "linkingDataverse", cascade = {CascadeType.REMOVE, CascadeType.MERGE, CascadeType.PERSIST})
    private List<DatasetLinkingDataverse> datasetLinkingDataverses;

    public List<DatasetLinkingDataverse> getDatasetLinkingDataverses() {
        return datasetLinkingDataverses;
    }

    public void setDatasetLinkingDataverses(List<DatasetLinkingDataverse> datasetLinkingDataverses) {
        this.datasetLinkingDataverses = datasetLinkingDataverses;
    }

    public Set<ControlledVocabularyValue> getDataverseSubjects() {
        return dataverseSubjects;
    }

    public void setDataverseSubjects(Set<ControlledVocabularyValue> dataverseSubjects) {
        this.dataverseSubjects = dataverseSubjects;
    }


    @OneToMany(mappedBy = "dataverse")
    private List<DataverseFieldTypeInputLevel> dataverseFieldTypeInputLevels = new ArrayList<>();

    @ManyToOne
    @JoinColumn(nullable = true)
    private Template defaultTemplate;

    @OneToMany(mappedBy = "definitionPoint", cascade = {CascadeType.REMOVE, CascadeType.MERGE, CascadeType.PERSIST})
    private List<SavedSearch> savedSearches;

    public List<SavedSearch> getSavedSearches() {
        return savedSearches;
    }

    public void setSavedSearches(List<SavedSearch> savedSearches) {
        this.savedSearches = savedSearches;
    }

    @OneToMany(mappedBy = "dataverse", cascade = {CascadeType.MERGE, CascadeType.REMOVE}, orphanRemoval = true)
    private List<Template> templates = new ArrayList<>();

    @OneToMany(mappedBy = "dataverse", cascade = {CascadeType.MERGE, CascadeType.REMOVE})
    private List<Guestbook> guestbooks;

    public List<Guestbook> getGuestbooks() {
        return guestbooks;
    }

    public void setGuestbooks(List<Guestbook> guestbooks) {
        this.guestbooks = guestbooks;
    }


    @OneToMany(mappedBy = "dataverse", cascade = {CascadeType.MERGE, CascadeType.REMOVE})
    private List<HarvestingClient> harvestingClientConfigs;

    public List<HarvestingClient> getHarvestingClientConfigs() {
        return this.harvestingClientConfigs;
    }

    public void setHarvestingClientConfigs(List<HarvestingClient> harvestingClientConfigs) {
        this.harvestingClientConfigs = harvestingClientConfigs;
    }
    /*
    public boolean isHarvested() {
        return harvestingClient != null;
    }
    */

    public boolean isRoot() {
        return this.getOwner() == null;
    }

    public List<Guestbook> getParentGuestbooks() {
        List<Guestbook> retList = new ArrayList<>();
        Dataverse testDV = this;
        while (testDV.getOwner() != null) {
            retList.addAll(testDV.getOwner().getGuestbooks());
            if (testDV.getOwner().guestbookRoot) {
                break;
            }
            testDV = testDV.getOwner();
        }
        return retList;
    }

    public List<Guestbook> getAvailableGuestbooks() {
        //get all guestbooks
        List<Guestbook> retList = new ArrayList<>();
        Dataverse testDV = this;
        List<Guestbook> allGbs = new ArrayList<>();
        if (!this.guestbookRoot) {
            while (testDV.getOwner() != null) {

                allGbs.addAll(testDV.getOwner().getGuestbooks());
                if (testDV.getOwner().isGuestbookRoot()) {
                    break;
                }
                testDV = testDV.getOwner();
            }
        }

        allGbs.addAll(this.getGuestbooks());
        //then only display them if they are enabled
        for (Guestbook gbt : allGbs) {
            if (gbt.isEnabled()) {
                retList.add(gbt);
            }
        }
        return retList;

    }

    private boolean guestbookRoot;

    public boolean isGuestbookRoot() {
        return guestbookRoot;
    }

    public void setGuestbookRoot(boolean guestbookRoot) {
        this.guestbookRoot = guestbookRoot;
    }


    public void setDataverseFieldTypeInputLevels(List<DataverseFieldTypeInputLevel> dataverseFieldTypeInputLevels) {
        this.dataverseFieldTypeInputLevels = dataverseFieldTypeInputLevels;
    }

    public List<DataverseFieldTypeInputLevel> getDataverseFieldTypeInputLevels() {
        return dataverseFieldTypeInputLevels;
    }


    public Template getDefaultTemplate() {
        return defaultTemplate;
    }

    public void setDefaultTemplate(Template defaultTemplate) {
        this.defaultTemplate = defaultTemplate;
    }

    public List<Template> getTemplates() {
        return templates;
    }

    public void setTemplates(List<Template> templates) {
        this.templates = templates;
    }

    public List<Template> getParentTemplates() {
        List<Template> retList = new ArrayList<>();
        Dataverse testDV = this;
        while (testDV.getOwner() != null) {

            if (!testDV.getRootMetadataBlocks().equals(testDV.getOwner().getRootMetadataBlocks())) {
                break;
            }
            retList.addAll(testDV.getOwner().getTemplates());

            if (testDV.getOwner().templateRoot) {
                break;
            }
            testDV = testDV.getOwner();
        }
        return retList;
    }

    public boolean isThemeRoot() {
        return themeRoot;
    }

    public boolean getThemeRoot() {
        return themeRoot;
    }

    public void setThemeRoot(boolean themeRoot) {
        this.themeRoot = themeRoot;
    }

    public boolean isTemplateRoot() {
        return templateRoot;
    }

    public void setTemplateRoot(boolean templateRoot) {
        this.templateRoot = templateRoot;
    }


    public Dataverse getMetadataBlockRootDataverse() {
        if (metadataBlockRoot || getOwner() == null) {
            return this;
        } else {
            return getOwner().getMetadataBlockRootDataverse();
        }
    }

    public List<MetadataBlock> getRootMetadataBlocks() {
        return getMetadataBlockRootDataverse().getMetadataBlocks();
    }

    public List<MetadataBlock> getMetadataBlocks() {
        return metadataBlocks;
    }

    public Long getMetadataRootId() {
        return getMetadataBlockRootDataverse().getId();
    }


    public DataverseTheme getDataverseTheme() {
        return getDataverseTheme(false);
    }

    public DataverseTheme getDataverseTheme(boolean returnActualDB) {
        if (returnActualDB || themeRoot || getOwner() == null) {
            return dataverseTheme;
        } else {
            return getOwner().getDataverseTheme();
        }
    }

    public String getGuestbookRootDataverseName() {
        Dataverse testDV = this;
        String retName = "Parent";
        while (testDV.getOwner() != null) {
            retName = testDV.getOwner().getDisplayName();
            if (testDV.getOwner().guestbookRoot) {
                break;
            }
            testDV = testDV.getOwner();
        }
        return retName;
    }

    public String getTemplateRootDataverseName() {
        Dataverse testDV = this;
        String retName = "Parent";
        while (testDV.getOwner() != null) {
            retName = testDV.getOwner().getDisplayName();
            if (testDV.getOwner().templateRoot) {
                break;
            }
            testDV = testDV.getOwner();
        }
        return retName;
    }

    public String getThemeRootDataverseName() {
        Dataverse testDV = this;
        String retName = "Parent";
        while (testDV.getOwner() != null) {
            retName = testDV.getOwner().getDisplayName();
            if (testDV.getOwner().themeRoot) {
                break;
            }
            testDV = testDV.getOwner();
        }
        return retName;
    }

    public String getMetadataParentRootDataverseName() {
        if (getOwner() == null) {
            return this.getDisplayName();
        } else {
            return getOwner().getMetadataBlockRootDataverse().getDisplayName();
        }
    }

    public String getFacetRootDataverseName() {
        Dataverse testDV = this;
        String retName = "Parent";
        while (testDV.getOwner() != null) {
            retName = testDV.getOwner().getDisplayName();
            if (testDV.getOwner().facetRoot) {
                break;
            }
            testDV = testDV.getOwner();
        }
        return retName;
    }


    public String getLogoOwnerId() {

        if (themeRoot || getOwner() == null) {
            return this.getId().toString();
        } else {
            return getOwner().getId().toString();
        }
    }

    public void setDataverseTheme(DataverseTheme dataverseTheme) {
        this.dataverseTheme = dataverseTheme;
    }

    public void setMetadataBlocks(List<MetadataBlock> metadataBlocks) {
        this.metadataBlocks = metadataBlocks;
    }

    public List<DatasetFieldType> getCitationDatasetFieldTypes() {
        return citationDatasetFieldTypes;
    }

    public void setCitationDatasetFieldTypes(List<DatasetFieldType> citationDatasetFieldTypes) {
        this.citationDatasetFieldTypes = citationDatasetFieldTypes;
    }


    public List<DataverseFacet> getDataverseFacets() {
        return getDataverseFacets(false);
    }

    public List<DataverseFacet> getDataverseFacets(boolean returnActualDB) {
        if (returnActualDB || facetRoot || getOwner() == null) {
            return dataverseFacets;
        } else {
            return getOwner().getDataverseFacets();
        }
    }

    public Long getFacetRootId() {
        if (facetRoot || getOwner() == null) {
            return this.getId();
        } else {
            return getOwner().getFacetRootId();
        }
    }

    public void setDataverseFacets(List<DataverseFacet> dataverseFacets) {
        this.dataverseFacets = dataverseFacets;
    }

    public List<DataverseContact> getDataverseContacts() {
        return dataverseContacts;
    }

    /**
     * Get the email addresses of the dataverse contacts as a comma-separated
     * concatenation.
     *
     * @return a comma-separated concatenation of email addresses, or the empty
     * string if there are no contacts.
     * @author bencomp
     */
    public String getContactEmails() {
        if (dataverseContacts != null && !dataverseContacts.isEmpty()) {
            StringBuilder buf = new StringBuilder();
            Iterator<DataverseContact> it = dataverseContacts.iterator();
            while (it.hasNext()) {
                DataverseContact con = it.next();
                buf.append(con.getContactEmail());
                if (it.hasNext()) {
                    buf.append(",");
                }
            }
            return buf.toString();
        } else {
            return "";
        }
    }

    public void setDataverseContacts(List<DataverseContact> dataverseContacts) {
        this.dataverseContacts = dataverseContacts;
    }

    public void addDataverseContact(int index) {
        dataverseContacts.add(index, new DataverseContact(this));
    }

    public void removeDataverseContact(int index) {
        dataverseContacts.remove(index);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAdditionalDescription() {
        return additionalDescription;
    }

    public void setAdditionalDescription(String additionalDescription) {
        this.additionalDescription = additionalDescription;
    }

    public String getAffiliation() {
        return affiliation;
    }

    public void setAffiliation(String affiliation) {
        this.affiliation = affiliation;
    }

    public boolean isMetadataBlockRoot() {
        return metadataBlockRoot;
    }

    public void setMetadataBlockRoot(boolean metadataBlockRoot) {
        this.metadataBlockRoot = metadataBlockRoot;
    }

    public boolean isFacetRoot() {
        return facetRoot;
    }

    public void setFacetRoot(boolean facetRoot) {
        this.facetRoot = facetRoot;
    }


    public void addRole(DataverseRole role) {
        role.setOwner(this);
        if (roles == null) {
            roles = new HashSet<>();
        }
        roles.add(role);
    }

    /**
     * Note: to add a role, use {@link #addRole(edu.harvard.iq.dataverse.authorization.DataverseRole)},
     * do not call this method and try to add directly to the list.
     *
     * @return the roles defined in this Dataverse.
     */
    public Set<DataverseRole> getRoles() {
        if (roles == null) {
            roles = new HashSet<>();
        }
        return roles;
    }

    public List<Dataverse> getOwners() {
        List<Dataverse> owners = new ArrayList<>();
        if (getOwner() != null) {
            owners.addAll(getOwner().getOwners());
            owners.add(getOwner());
        }
        return owners;
    }

    @Override
    public boolean equals(Object object) {
        // TODO: Warning - this method won't work in the case the id fields are not set
        if (!(object instanceof Dataverse)) {
            return false;
        }
        Dataverse other = (Dataverse) object;
        return Objects.equals(getId(), other.getId());
    }

    @Override
    protected String toStringExtras() {
        return "name:" + getName();
    }

    @Override
    public <T> T accept(Visitor<T> v) {
        return v.visit(this);
    }

    /**
     * @todo implement in https://github.com/IQSS/dataverse/issues/551
     */
    public String getDepositTermsOfUse() {
        return "Dataverse Deposit Terms of Use will be implemented in https://github.com/IQSS/dataverse/issues/551";
    }

    @Override
    public String getDisplayName() {
        return MarkupChecker.stripAllTags(MarkupChecker.sanitizeBasicHTML(getName()));
    }

    @Override
    public boolean isPermissionRoot() {
        return permissionRoot;
    }

    public void setPermissionRoot(boolean permissionRoot) {
        this.permissionRoot = permissionRoot;
    }

    @Override
    public boolean isAncestorOf(DvObject other) {
        while (other != null) {
            if (equals(other)) {
                return true;
            }
            other = other.getOwner();
        }
        return false;
    }

    public boolean isAllowMessagesBanners() {
        return allowMessagesBanners;
    }

    public void setAllowMessagesBanners(boolean allowMessagesBanners) {
        this.allowMessagesBanners = allowMessagesBanners;
    }

    public FeaturedDataversesSorting getFeaturedDataversesSorting() {
        if (featuredDataversesSorting == null) {
            featuredDataversesSorting = FeaturedDataversesSorting.BY_HAND;
        }
        return featuredDataversesSorting;
    }

    public void setFeaturedDataversesSorting(FeaturedDataversesSorting featuredDataversesSorting) {
        this.featuredDataversesSorting = featuredDataversesSorting;
    }
}
