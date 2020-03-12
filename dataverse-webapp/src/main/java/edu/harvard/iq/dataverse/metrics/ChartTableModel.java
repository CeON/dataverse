package edu.harvard.iq.dataverse.metrics;

import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;

public class ChartTableModel {
    private List<Pair<String, String>> data = new ArrayList<>();
    private String title;
    private String leftColumnName;
    private String rightColumnName;

    // -------------------- CONSTRUCTORS --------------------
    public ChartTableModel() {
    }

    public ChartTableModel(List<Pair<String, String>> data, String title, String leftColumnName, String rightColumnName) {
        this.data = data;
        this.title = title;
        this.leftColumnName = leftColumnName;
        this.rightColumnName = rightColumnName;
    }

    // -------------------- GETTERS --------------------
    public List<Pair<String, String>> getData() {
        return data;
    }

    public String getTitle() {
        return title;
    }

    public String getLeftColumnName() {
        return leftColumnName;
    }

    public String getRightColumnName() {
        return rightColumnName;
    }

    // -------------------- SETTERS --------------------

    public void setData(List<Pair<String, String>> data) {
        this.data = data;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setLeftColumnName(String leftColumnName) {
        this.leftColumnName = leftColumnName;
    }

    public void setRightColumnName(String rightColumnName) {
        this.rightColumnName = rightColumnName;
    }
}
