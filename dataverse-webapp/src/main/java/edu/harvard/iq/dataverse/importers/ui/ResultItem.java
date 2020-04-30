package edu.harvard.iq.dataverse.importers.ui;

import edu.harvard.iq.dataverse.importer.metadata.ResultField;

import java.util.List;
import java.util.stream.Collectors;

public class ResultItem implements Comparable<ResultItem> {
    private ResultField resultField;
    private List<ResultItem> children;

    private boolean processable = false;
    private boolean shouldProcess = true;
    private boolean multipleAllowed = false;
    private boolean compound = false;

    private int displayOrder = Integer.MAX_VALUE;
    private String localizedName;

    private ProcessingType processingType;

    // -------------------- CONSTRUCTOR --------------------

    public ResultItem(ResultField resultField) {
        this.resultField = resultField;
        this.children = resultField.getChildren().stream()
                .map(ResultItem::new)
                .collect(Collectors.toList());
        this.localizedName = resultField.getName();
    }

    // -------------------- GETTERS --------------------

    public String getName() {
        return resultField.getName();
    }

    public String getLocalizedName() {
        return localizedName;
    }

    public String getValue() {
        return resultField.getValue();
    }

    public ResultField getResultField() {
        return resultField;
    }

    public List<ResultItem> getChildren() {
        return children;
    }

    public boolean getProcessable() {
        return processable;
    }

    public boolean getShouldProcess() {
        return shouldProcess;
    }

    public boolean getMultipleAllowed() {
        return multipleAllowed;
    }

    public ProcessingType getProcessingType() {
        return processingType;
    }

    public boolean getCompound() {
        return compound;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    // -------------------- LOGIC --------------------

    @Override
    public int compareTo(ResultItem that) {
        return Integer.compare(displayOrder, that.displayOrder);
    }


    // -------------------- SETTERS --------------------

    public void setValue(String name) {
        this.resultField.setValue(name);
    }

    public void setShouldProcess(boolean shouldProcess) {
        this.shouldProcess = shouldProcess;
    }

    public void setProcessingType(ProcessingType processingType) {
        this.processingType = processingType;
    }

    // -------------------- NON-JavaBeans SETTERS --------------------

    public ResultItem setMultipleAllowed(boolean multipleAllowed) {
        this.multipleAllowed = multipleAllowed;
        return this;
    }

    public ResultItem setProcessable(boolean processable) {
        this.processable = processable;
        return this;
    }

    public ResultItem setCompound(boolean compound) {
        this.compound = compound;
        return this;
    }

    public ResultItem setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
        return this;
    }

    public ResultItem setLocalizedName(String localizedName) {
        this.localizedName = localizedName;
        return this;
    }

    // -------------------- toString --------------------

    @Override
    public String toString() {
        return getName() + " : " + getValue()
                + "\tMultiple: " + multipleAllowed
                + ", ProcessingType: " + processingType;
    }
}
