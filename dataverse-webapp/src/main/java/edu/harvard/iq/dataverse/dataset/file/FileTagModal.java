package edu.harvard.iq.dataverse.dataset.file;

import edu.harvard.iq.dataverse.persistence.datafile.FileMetadata;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;

import javax.faces.view.ViewScoped;
import javax.inject.Named;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

@ViewScoped
@Named("FileTagModal")
public class FileTagModal implements Serializable {

    private List<String> fileMetadataTags = new ArrayList<>();
    private List<String> dataFileTags = new ArrayList<>();
    private TreeSet<String> selectedFileMetadataTags = new TreeSet<>();
    private TreeSet<String> selectedDataFileTags = new TreeSet<>();

    private boolean removeUnusedTags;
    private String newCategoryName;
    private FileMetadata fileMetadata;

    private Dataset dataset;


    // -------------------- GETTERS --------------------

    public List<String> getFileMetadataTags() {
        return fileMetadataTags;
    }

    public List<String> getDataFileTags() {
        return dataFileTags;
    }

    public TreeSet<String> getSelectedFileMetadataTags() {
        return selectedFileMetadataTags;
    }

    public TreeSet<String> getSelectedDataFileTags() {
        return selectedDataFileTags;
    }

    public String getNewCategoryName() {
        return newCategoryName;
    }

    public boolean isRemoveUnusedTags() {
        return removeUnusedTags;
    }

    public FileMetadata getFileMetadata() {
        return fileMetadata;
    }

    // -------------------- LOGIC --------------------

    public void init(FileMetadata fileMetadata, Dataset dataset) {
        this.fileMetadata = fileMetadata;
        this.dataset = dataset;

        prepareTags(fileMetadata, dataset);
    }

    public void init2(List<FileMetadata> fileMetadata, Dataset dataset) {
        //this.fileMetadata = fileMetadata;
        this.dataset = dataset;

        //prepareTags(fileMetadata, dataset);
    }

    public String saveNewCategory() {

        if (!newCategoryName.isEmpty()) {

            fileMetadataTags.add(newCategoryName);
            selectedFileMetadataTags.add(newCategoryName);
            newCategoryName = "";
        }

        return "";
    }

    /*public void handleFileCategoriesSelection(final AjaxBehaviorEvent event) {
        if (selectedFileMetadataTags != null) {
            selectedFileMetadataTags = selectedFileMetadataTags.clone();
        }
    }*/

    public boolean isTabularFile() {
        return fileMetadata.getDataFile().isTabularData();
    }

    // -------------------- PRIVATE --------------------

    private void prepareTags(FileMetadata fileMetadata, Dataset dataset) {
        prepareFileMetadataTags(fileMetadata, dataset);
        prepareDataFileTags(fileMetadata);
    }

    private void prepareFileMetadataTags(FileMetadata fileMetadata, Dataset dataset) {
        fileMetadataTags.addAll(dataset.getCategoriesByName());
        selectedFileMetadataTags.addAll(fileMetadata.getCategoriesByName());
    }

    private void prepareDataFileTags(FileMetadata fileMetadata) {
        dataFileTags.addAll(fileMetadata.getDataFile().getTagLabels());
        selectedDataFileTags.addAll(fileMetadata.getDataFile().getTagLabels());
    }

    // -------------------- SETTERS --------------------


    public void setFileMetadataTags(List<String> fileMetadataTags) {
        this.fileMetadataTags = fileMetadataTags;
    }

    public void setDataFileTags(List<String> dataFileTags) {
        this.dataFileTags = dataFileTags;
    }

    public void setSelectedFileMetadataTags(TreeSet<String> selectedFileMetadataTags) {
        this.selectedFileMetadataTags = selectedFileMetadataTags;
    }

    public void setSelectedDataFileTags(TreeSet<String> selectedDataFileTags) {
        this.selectedDataFileTags = selectedDataFileTags;
    }

    public void setRemoveUnusedTags(boolean removeUnusedTags) {
        this.removeUnusedTags = removeUnusedTags;
    }

    public void setNewCategoryName(String newCategoryName) {
        this.newCategoryName = newCategoryName;
    }

    public void setFileMetadata(FileMetadata fileMetadata) {
        this.fileMetadata = fileMetadata;
    }

    public void setDataset(Dataset dataset) {
        this.dataset = dataset;
    }
}
