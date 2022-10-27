/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package edu.harvard.iq.dataverse.persistence.datafile;

import edu.harvard.iq.dataverse.persistence.JpaEntity;
import org.apache.commons.lang3.StringUtils;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedNativeQueries;
import javax.persistence.NamedNativeQuery;
import javax.persistence.Table;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Leonid Andreev
 */
@Entity
@NamedNativeQueries({
        @NamedNativeQuery(name = "DataFileTag.findData2",
                query = "SELECT DATAFILE_ID, TYPE FROM DATAFILETAG WHERE DATAFILE_ID IN (?, ?)"),
        @NamedNativeQuery(name = "DataFileTag.findData6",
                query = "SELECT DATAFILE_ID, TYPE FROM DATAFILETAG WHERE DATAFILE_ID IN (?, ?, ?, ?, ?, ?)"),
        @NamedNativeQuery(name = "DataFileTag.findData10",
                query = "SELECT DATAFILE_ID, TYPE FROM DATAFILETAG WHERE DATAFILE_ID IN (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"),
})
@Table(indexes = {@Index(columnList = "datafile_id")})
public class DataFileTag implements JpaEntity<Long>, Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    /*
     * DataFile to which this tag belongs.
     */
    @ManyToOne
    @JoinColumn(nullable = false)
    private DataFile dataFile;


    // TODO:
    // add a mechanism for defining tags in the database, in addition to
    // these built-in tags (?).
    // -- L.A. 4.0 beta 9
    public enum TagType {Survey, TimeSeries, Panel, Event, Genomics, Network, Geospatial}

    private static final Map<TagType, String> TagTypeToLabels = new HashMap<>();

    private static final Map<String, TagType> TagLabelToTypes = new HashMap<>();


    static {
        TagTypeToLabels.put(TagType.Survey, "Survey");
        TagTypeToLabels.put(TagType.TimeSeries, "Time Series");
        TagTypeToLabels.put(TagType.Panel, "Panel");
        TagTypeToLabels.put(TagType.Event, "Event");
        TagTypeToLabels.put(TagType.Genomics, "Genomics");
        TagTypeToLabels.put(TagType.Network, "Network");
        TagTypeToLabels.put(TagType.Geospatial, "Geospatial");
    }

    static {
        TagLabelToTypes.put("Survey", TagType.Survey);
        TagLabelToTypes.put("Time Series", TagType.TimeSeries);
        TagLabelToTypes.put("Panel", TagType.Panel);
        TagLabelToTypes.put("Event", TagType.Event);
        TagLabelToTypes.put("Genomics", TagType.Genomics);
        TagLabelToTypes.put("Network", TagType.Network);
        TagLabelToTypes.put("Geospatial", TagType.Geospatial);
    }

    public static List<String> listTags() {
        List<String> retlist = new ArrayList<>();

        for (TagType t : TagType.values()) {
            retlist.add(TagTypeToLabels.get(t));
        }

        return retlist;
    }

    @Column(nullable = false)
    private TagType type;

    public DataFile getDataFile() {
        return this.dataFile;
    }

    public void setDataFile(DataFile dataFile) {
        this.dataFile = dataFile;
    }

    public TagType getType() {
        return this.type;
    }


    public void setType(TagType type) {
        this.type = type;
    }

    public void setTypeByLabel(String label) {
        TagType tagtype = TagLabelToTypes.get(label);

        if (tagtype == null) {
            throw new IllegalArgumentException("Unknown DataFile Tag: " + label);
        }

        this.type = tagtype;
    }

    public String getTypeLabel() {
        if (this.type != null) {
            return TagTypeToLabels.get(this.type);
        }

        return null;
    }

    /**
     * Is this a geospatial tag, e.g. TagType.Geospatial
     *
     * @return
     */
    public boolean isGeospatialTag() {
        if (this.type == null) {
            return false;
        }
        return this.type == TagType.Geospatial;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash += (id != null ? id.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object object) {
        // TODO: Warning - this method won't work in the case the id fields are not set
        if (!(object instanceof DataFileTag)) {
            return false;
        }
        DataFileTag other = (DataFileTag) object;
        return !((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id)));
    }

    @Override
    public String toString() {
        return "DataFileTag[ id=" + id + " ]";
    }


    /**
     * Static method to check whether a string is a valid tag
     * <p>
     * Used for API check
     *
     * @param tagString
     * @return
     */
    public static boolean isDataFileTag(String label) {

        if (label == null) {
            throw new NullPointerException("label cannot be null");
        }

        return TagLabelToTypes.containsKey(label);
    }

    public TagType getDataFileTagFromLabel(String label) {

        if (!TagLabelToTypes.containsKey(label)) {
            return null;
        }

        return TagLabelToTypes.get(label);
    }


    public static List<String> getListofLabels() {

        return new ArrayList<>(TagTypeToLabels.values());
    }

    public static String getListofLabelsAsString() {

        return StringUtils.join(DataFileTag.getListofLabels(), ", ");
    }
}
