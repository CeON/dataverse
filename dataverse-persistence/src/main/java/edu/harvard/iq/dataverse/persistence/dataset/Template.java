package edu.harvard.iq.dataverse.persistence.dataset;

import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import org.hibernate.validator.constraints.NotBlank;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.Transient;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author skraffmiller
 */
@Entity
@Table(indexes = {@Index(columnList = "dataverse_id")})
public class Template implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    public Template() {

    }
    public Template(Dataverse dataverseIn) {
        dataverse = dataverseIn;
        datasetFields = initDatasetFields();
    }

    public Long getId() {
        return this.id;
    }

    @NotBlank(message = "{dataset.templatename}")
    @Size(max = 255, message = "{dataset.nameLength}")
    @Column(nullable = false)
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    private Long usageCount;

    public Long getUsageCount() {
        return usageCount;
    }

    public void setUsageCount(Long usageCount) {
        this.usageCount = usageCount;
    }

    @Temporal(value = TemporalType.TIMESTAMP)
    @Column(nullable = false)
    private Date createTime;

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getCreateDate() {
        return new SimpleDateFormat("MMMM d, yyyy").format(createTime);
    }

    @OneToOne(cascade = {CascadeType.REMOVE, CascadeType.MERGE, CascadeType.PERSIST}, orphanRemoval = true)
    @JoinColumn(name = "termsOfUseAndAccess_id")
    private TermsOfUseAndAccess termsOfUseAndAccess;

    public TermsOfUseAndAccess getTermsOfUseAndAccess() {
        return termsOfUseAndAccess;
    }

    public void setTermsOfUseAndAccess(TermsOfUseAndAccess termsOfUseAndAccess) {
        this.termsOfUseAndAccess = termsOfUseAndAccess;
    }

    @OneToMany(mappedBy = "template", orphanRemoval = true, cascade = {CascadeType.REMOVE, CascadeType.MERGE, CascadeType.PERSIST})
    private List<DatasetField> datasetFields = new ArrayList<>();

    @ManyToOne
    @JoinColumn(nullable = true)
    private Dataverse dataverse;

    public List<DatasetField> getDatasetFields() {
        return datasetFields;
    }

    @Transient
    private boolean isDefaultForDataverse;

    public boolean isIsDefaultForDataverse() {
        return isDefaultForDataverse;
    }

    public void setIsDefaultForDataverse(boolean isDefaultForDataverse) {
        this.isDefaultForDataverse = isDefaultForDataverse;
    }

    @Transient
    private List<Dataverse> dataversesHasAsDefault;

    public List<Dataverse> getDataversesHasAsDefault() {
        return dataversesHasAsDefault;
    }

    public void setDataversesHasAsDefault(List<Dataverse> dataversesHasAsDefault) {
        this.dataversesHasAsDefault = dataversesHasAsDefault;
    }

    public Dataverse getDataverse() {
        return dataverse;
    }

    public void setDataverse(Dataverse dataverse) {
        this.dataverse = dataverse;
    }

    private List<DatasetField> initDatasetFields() {
        //retList - Return List of values
        List<DatasetField> retList = new ArrayList<>();
        for (DatasetField dsf : this.getDatasetFields()) {
            retList.add(initDatasetField(dsf));
        }

        //Test to see that there are values for 
        // all fields in this dataset via metadata blocks
        //only add if not added above
        for (MetadataBlock mdb : this.getDataverse().getRootMetadataBlocks()) {
            for (DatasetFieldType dsfType : mdb.getDatasetFieldTypes()) {
                if (!dsfType.isSubField()) {
                    boolean add = true;
                    //don't add if already added as a val
                    for (DatasetField dsf : retList) {
                        if (dsfType.equals(dsf.getDatasetFieldType())) {
                            add = false;
                            break;
                        }
                    }

                    if (add) {
                        retList.add(DatasetField.createNewEmptyDatasetField(dsfType, this));
                    }
                }
            }
        }

        return sortDatasetFields(retList);
    }

    private List<DatasetField> sortDatasetFields(List<DatasetField> dsfList) {
        dsfList.sort((dsf1, dsf2) -> {
            int dsf1Order = dsf1.getDatasetFieldType().getDisplayOrder();
            int dsf2Order = dsf2.getDatasetFieldType().getDisplayOrder();
            return Integer.compare(dsf1Order, dsf2Order);
        });
        return dsfList;
    }

    // TODO: clean up init methods and get them to work, cascading all the way down.
    // right now, only work for one level of compound objects
    private DatasetField initDatasetField(DatasetField dsf) {
        if (dsf.getDatasetFieldType().isCompound()) {
            for (DatasetFieldCompoundValue cv : dsf.getDatasetFieldCompoundValues()) {
                // for each compound value; check the datasetfieldTypes associated with its type
                for (DatasetFieldType dsfType : dsf.getDatasetFieldType().getChildDatasetFieldTypes()) {
                    boolean add = true;
                    for (DatasetField subfield : cv.getChildDatasetFields()) {
                        if (dsfType.equals(subfield.getDatasetFieldType())) {
                            add = false;
                            break;
                        }
                    }

                    if (add) {
                        cv.getChildDatasetFields().add(DatasetField.createNewEmptyChildDatasetField(dsfType, cv));
                    }
                }

                sortDatasetFields(cv.getChildDatasetFields());
            }
        }

        return dsf;
    }

    public Template cloneNewTemplate(Template source) {
        Template newTemplate = new Template();
        Template latestVersion = source;
        //if the latest version has values get them copied over
        if (latestVersion.getDatasetFields() != null && !latestVersion.getDatasetFields().isEmpty()) {
            newTemplate.setDatasetFields(DatasetFieldUtil.copyDatasetFields(source.getDatasetFields()));
        }
        TermsOfUseAndAccess terms;
        if (source.getTermsOfUseAndAccess() != null) {
            terms = source.getTermsOfUseAndAccess().copyTermsOfUseAndAccess();
        } else {
            terms = new TermsOfUseAndAccess();
            terms.setLicense(TermsOfUseAndAccess.defaultLicense);
        }
        newTemplate.setTermsOfUseAndAccess(terms);
        return newTemplate;
    }

    public void setDatasetFields(List<DatasetField> datasetFields) {
        for (DatasetField dsf : datasetFields) {
            dsf.setTemplate(this);
        }
        this.datasetFields = datasetFields;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash += (this.id != null ? this.id.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object object) {
        // TODO: Warning - this method won't work in the case the id fields are not set
        if (!(object instanceof Template)) {
            return false;
        }
        Template other = (Template) object;
        return this.id == other.id || (this.id != null && this.id.equals(other.id));
    }

}
