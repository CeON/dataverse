package edu.harvard.iq.dataverse.search.response;

import java.util.ArrayList;
import java.util.List;

public class FacetCategory {

    private String name;
    private List<FacetLabel> facetLabel = new ArrayList<>(); // TODO: should be plural
    private String friendlyName;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void addSingleFacetLabel(FacetLabel facetLabel) {
        this.facetLabel.add(facetLabel);
    }

    public List<FacetLabel> getFacetLabel() {
        return facetLabel;
    }

    public void setFacetLabel(List<FacetLabel> facetLabel) {
        this.facetLabel = facetLabel;
    }

    public String getFriendlyName() {
        return friendlyName;
    }

    public void setFriendlyName(String friendlyName) {
        this.friendlyName = friendlyName;
    }

    public String getDisplayName() {
        return getFriendlyName();
    }

    @Override
    public String toString() {
        return "FacetCategory [name=" + name + ", facetLabel=" + facetLabel + ", friendlyName=" + friendlyName + "]";
    }
    
}
