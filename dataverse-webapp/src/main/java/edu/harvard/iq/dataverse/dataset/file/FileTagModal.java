package edu.harvard.iq.dataverse.dataset.file;

import edu.harvard.iq.dataverse.persistence.datafile.FileMetadata;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;

import javax.faces.view.ViewScoped;
import javax.inject.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

@ViewScoped
@Named("FileTagModal")
public class FileTagModal {

    private List<String> categoriesByName = new ArrayList<>();
    private List<String> tabFileTags = new ArrayList<>();
    private TreeSet<String> selectedTabFileTags = new TreeSet<>();
    private TreeSet<String> selectableTags = new TreeSet<>();
    private String newCategoryName;
    private FileMetadata fileMetadata;

    private Dataset dataset;

    /*public String saveNewCategory() {

        if (!newCategoryName.isEmpty()) {
            categoriesByName.add(newCategoryName);
        }


        //Now increase size of selectedTags and add new category
        String[] temp = new String[selectableTags.length + 1];
        System.arraycopy(selectableTags, 0, temp, 0, selectableTags.length);
        selectableTags = temp;
        selectableTags[selectableTags.length - 1] = newCategoryName;
        //Blank out added category
        newCategoryName = "";
        return "";
    }

    public void handleFileCategoriesSelection(final AjaxBehaviorEvent event) {
        if (selectableTags != null) {
            selectableTags = selectableTags.clone();
        }
    }

    public void refreshTagsPopUp(FileMetadata fm) {
        fileMetadata = fm;
        refreshCategoriesByName();
        refreshTabFileTagsByName();
    }

    private void refreshCategoriesByName() {
        categoriesByName.addAll(dataset.getCategoriesByName());
        selectableTags.addAll(fileMetadata.getCategoriesByName());
    }

    private void refreshTabFileTagsByName() {
        tabFileTagsByName = new ArrayList<>();
        if (fileMetadataSelectedForTagsPopup.getDataFile().getTags() != null) {
            for (int i = 0; i < fileMetadataSelectedForTagsPopup.getDataFile().getTags().size(); i++) {
                tabFileTagsByName.add(fileMetadataSelectedForTagsPopup.getDataFile().getTags().get(i).getTypeLabel());
            }
        }

        selectedTabFileTags = new String[0];
        if (tabFileTagsByName.size() > 0) {
            selectedTabFileTags = new String[tabFileTagsByName.size()];
            for (int i = 0; i < tabFileTagsByName.size(); i++) {
                selectedTabFileTags[i] = tabFileTagsByName.get(i);
            }
        }

        selectedTabFileTags.addAll(fileMetadata.getDataFile().getTagLabels());
    }*/

}
