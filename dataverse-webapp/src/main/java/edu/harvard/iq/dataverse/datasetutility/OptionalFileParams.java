/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.harvard.iq.dataverse.datasetutility;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.ejb.Stateful;
import javax.inject.Inject;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import edu.harvard.iq.dataverse.api.dto.FileTermsOfUseDTO;
import edu.harvard.iq.dataverse.common.BundleUtil;
import edu.harvard.iq.dataverse.common.Util;
import edu.harvard.iq.dataverse.license.TermsOfUseFactory;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.datafile.DataFileTag;
import edu.harvard.iq.dataverse.persistence.datafile.FileMetadata;
import edu.harvard.iq.dataverse.persistence.datafile.license.FileTermsOfUse;
import edu.harvard.iq.dataverse.persistence.datafile.license.License;
import edu.harvard.iq.dataverse.persistence.datafile.license.LicenseRepository;

/**
 * This is used in conjunction with the AddReplaceFileHelper
 * <p>
 * It encapsulates these optional parameters:
 * <p>
 * - description
 * - file tags (can be custom)
 * - tabular tags (controlled vocabulary)
 * <p>
 * Future params:
 * - Provenance related information
 *
 * @author rmp553
 */
@Stateful
public class OptionalFileParams implements Serializable {

    private static final long serialVersionUID = 9103033252084387893L;

    private static final Logger logger = Logger.getLogger(OptionalFileParams.class.getName());

    private LicenseRepository licenseRespository;
    private TermsOfUseFactory termsOfUseFactory;

    private String description;
    public static final String DESCRIPTION_ATTR_NAME = "description";

    private List<String> categories;
    public static final String CATEGORIES_ATTR_NAME = "categories";

    private List<String> dataFileTags;
    public static final String FILE_DATA_TAGS_ATTR_NAME = "dataFileTags";

    private FileTermsOfUseDTO fileTermsOfUseDTO;
    public static final String FILE_TERMS_OF_USE = "termsOfUseAndAccess";

    // -------------------- CONSTRUCTORS --------------------
    @Deprecated
    public OptionalFileParams() {
    }

    @Inject
    public OptionalFileParams(LicenseRepository licenseRepository, TermsOfUseFactory termsOfUseFactory) {
        this.licenseRespository = licenseRepository;
        this.termsOfUseFactory = termsOfUseFactory;
    }


    public OptionalFileParams create(String jsonData) throws DataFileTagException {
        if (jsonData != null) {
            loadParamsFromJson(jsonData);
        }
        return this;
    }

    /**
     * Set description
     *
     * @param description
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Get for description
     *
     * @return String
     */
    public String getDescription() {
        return this.description;
    }

    public boolean hasCategories() {
        return (categories != null) && (!this.categories.isEmpty());
    }

    public boolean hasFileDataTags() {
        return (dataFileTags != null) && (!this.dataFileTags.isEmpty());
    }

    public boolean hasDescription() {
        return (description != null) && (!this.description.isEmpty());
    }

    /**
     * Set tags
     *
     * @param tags
     */
    public void setCategories(List<String> newCategories) {

        if (newCategories != null) {
            newCategories = Util.removeDuplicatesNullsEmptyStrings(newCategories);
            if (newCategories.isEmpty()) {
                newCategories = null;
            }
        }

        this.categories = newCategories;
    }

    /**
     * Get for tags
     *
     * @return List<String>
     */
    public List<String> getCategories() {
        return this.categories;
    }


    /**
     * Set dataFileTags
     *
     * @param dataFileTags
     */
    public void setDataFileTags(List<String> dataFileTags) {
        this.dataFileTags = dataFileTags;
    }

    /**
     * Get for dataFileTags
     *
     * @return List<String>
     */
    public List<String> getDataFileTags() {
        return this.dataFileTags;
    }

    public FileTermsOfUseDTO getFileTermsOfUseDTO() {
        return fileTermsOfUseDTO;
    }

    private void loadParamsFromJson(String jsonData) throws DataFileTagException {

        msgt("jsonData: " + jsonData);
        if (jsonData == null || jsonData.isEmpty()) {
            return;
//            logger.log(Level.SEVERE, "jsonData is null");
        }
        JsonObject jsonObj;
        try {
            jsonObj = new Gson().fromJson(jsonData, JsonObject.class);
        } catch (ClassCastException ex) {
            logger.info("Exception parsing string '" + jsonData + "': " + ex);
            return;
        }

        // -------------------------------
        // get description as string
        // -------------------------------
        if ((jsonObj.has(DESCRIPTION_ATTR_NAME)) && (!jsonObj.get(DESCRIPTION_ATTR_NAME).isJsonNull())) {

            this.description = jsonObj.get(DESCRIPTION_ATTR_NAME).getAsString();
        }

        // -------------------------------
        // get tags 
        // -------------------------------
        Gson gson = new Gson();

        //Type objType = new TypeToken<List<String[]>>() {}.getType();
        Type listType = new TypeToken<List<String>>() {
        }.getType();

        //----------------------
        // Load tags
        //----------------------
        if ((jsonObj.has(CATEGORIES_ATTR_NAME)) && (!jsonObj.get(CATEGORIES_ATTR_NAME).isJsonNull())) {

            /**
             * @todo Use JsonParser.getCategories somehow instead (refactoring
             * required). This code is exercised by FilesIT.
             */
            setCategories(this.categories = gson.fromJson(jsonObj.get(CATEGORIES_ATTR_NAME), listType));
        }

        //----------------------
        // Load tabular tags
        //----------------------
        if ((jsonObj.has(FILE_DATA_TAGS_ATTR_NAME)) && (!jsonObj.get(FILE_DATA_TAGS_ATTR_NAME).isJsonNull())) {


            // Get potential tags from JSON
            List<String> potentialTags = gson.fromJson(jsonObj.get(FILE_DATA_TAGS_ATTR_NAME), listType);

            // Add valid potential tags to the list
            addFileDataTags(potentialTags);

        }

        //----------------------
        // Load File Terms of Use and Access
        //----------------------
        Type objType = new TypeToken<FileTermsOfUseDTO>(){}.getType();
        if ((jsonObj.has(FILE_TERMS_OF_USE)) && (!jsonObj.get(FILE_TERMS_OF_USE).isJsonNull())) {
            fileTermsOfUseDTO = gson.fromJson(jsonObj.get(FILE_TERMS_OF_USE), objType);
        }
    }

    public void addFileDataTags(List<String> potentialTags) throws DataFileTagException {

        if (potentialTags == null) {
            return;
        }

        potentialTags = Util.removeDuplicatesNullsEmptyStrings(potentialTags);

        if (potentialTags.isEmpty()) {
            return;
        }

        // Make a new list
        this.dataFileTags = new ArrayList<>();

        // Add valid potential tags to the list
        for (String tagToCheck : potentialTags) {
            if (DataFileTag.isDataFileTag(tagToCheck)) {
                this.dataFileTags.add(tagToCheck);
            } else {
                String errMsg = BundleUtil.getStringFromBundle("file.addreplace.error.invalid_datafile_tag");
                throw new DataFileTagException(errMsg + " [" + tagToCheck + "]. Please use one of the following: " + DataFileTag.getListofLabelsAsString());
            }
        }
        // Shouldn't happen....
        if (dataFileTags.isEmpty()) {
            dataFileTags = null;
        }
    }


    private void msg(String s) {
        System.out.println(s);
    }

    private void msgt(String s) {
        msg("-------------------------------");
        msg(s);
        msg("-------------------------------");
    }

    /**
     * Add parameters to a DataFile object
     */
    public void addOptionalParams(DataFile df) throws DataFileTagException {
        if (df == null) {
            throw new NullPointerException("The datafile cannot be null!");
        }

        FileMetadata fm = df.getFileMetadata();

        // ---------------------------
        // Add description
        // ---------------------------
        if (hasDescription()) {
            fm.setDescription(this.getDescription());
        }


        // ---------------------------
        // Add categories
        // ---------------------------
        addCategoriesToDataFile(fm);


        // ---------------------------
        // Add DataFileTags
        // ---------------------------
        addFileDataTagsToFile(df);

        // ---------------------------
        // Add File TermsOfUseAndAccess
        // ---------------------------
        addFileTermsOfUseAndAccess(fm);

    }

    /**
     * Add File terms of use and access
     */
    private void addFileTermsOfUseAndAccess(FileMetadata fileMetadata) {
        if (fileMetadata == null) {
            throw new NullPointerException("The fileMetadata cannot be null!");
        }

        if(this.getFileTermsOfUseDTO().getTermsType().equals(FileTermsOfUse.TermsOfUseType.LICENSE_BASED.toString())) {
            License license = licenseRespository.findActiveOrderedByPosition()
                    .stream()
                    .filter(l -> l.getName().equals(this.getFileTermsOfUseDTO().getLicense()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("There is no active license with name: " + this.getFileTermsOfUseDTO().getLicense()));

            fileMetadata.setTermsOfUse(termsOfUseFactory.createTermsOfUseFromLicense(license));
        }

        if(this.getFileTermsOfUseDTO().getTermsType().equals(FileTermsOfUse.TermsOfUseType.ALL_RIGHTS_RESERVED.toString())) {
            fileMetadata.setTermsOfUse(termsOfUseFactory.createAllRightsReservedTermsOfUse());
        }

        if(this.getFileTermsOfUseDTO().getTermsType().equals(FileTermsOfUse.TermsOfUseType.RESTRICTED.toString())) {
            if(!this.getFileTermsOfUseDTO().getAccessConditions().equals(FileTermsOfUse.RestrictType.CUSTOM.toString())) {
                fileMetadata.setTermsOfUse(termsOfUseFactory.createRestrictedTermsOfUse(FileTermsOfUse.RestrictType.valueOf(this.getFileTermsOfUseDTO().getAccessConditions())));
            } else {
                fileMetadata.setTermsOfUse(termsOfUseFactory.createRestrictedCustomTermsOfUse(this.getFileTermsOfUseDTO().getAccessConditionsCustomText()));
            }
        }
    }


    /**
     * Add Tags to the DataFile
     */
    private void addCategoriesToDataFile(FileMetadata fileMetadata) {

        if (fileMetadata == null) {
            throw new NullPointerException("The fileMetadata cannot be null!");
        }

        // Is there anything to add?
        //
        if (!hasCategories()) {
            return;
        }

        List<String> currentCategories = fileMetadata.getCategoriesByName();

        // Add categories to the file metadata object
        //
        this.getCategories().stream().forEach((catText) -> {
            fileMetadata.addCategoryByName(catText);  // fyi: "addCategoryByName" checks for dupes
        });
    }


    /**
     * NOTE: DataFile tags can only be added to tabular files
     * <p>
     * - e.g. The file must already be ingested.
     * <p>
     * Because of this, these tags cannot be used when "Adding" a file via
     * the API--e.g. b/c the file will note yet be ingested
     *
     * @param df
     */
    private void addFileDataTagsToFile(DataFile df) throws DataFileTagException {
        if (df == null) {
            throw new NullPointerException("The DataFile (df) cannot be null!");
        }

        // --------------------------------------------------
        // Is there anything to add?
        // --------------------------------------------------
        if (!hasFileDataTags()) {
            return;
        }

        // --------------------------------------------------
        // Is this a tabular file?
        // --------------------------------------------------
        if (!df.isTabularData()) {
            String errMsg = BundleUtil.getStringFromBundle("file.metadata.datafiletag.not_tabular");

            throw new DataFileTagException(errMsg);
        }

        // --------------------------------------------------
        // Get existing tag list and convert it to list of strings (labels)
        // --------------------------------------------------
        List<DataFileTag> existingDataFileTags = df.getTags();
        List<String> currentLabels;

        if (existingDataFileTags == null) {
            // nothing, just make an empty list
            currentLabels = new ArrayList<>();
        } else {
            // Yes, get the labels in a list
            currentLabels = df.getTags().stream()
                    .map(x -> x.getTypeLabel())
                    .collect(Collectors.toList())
            ;
        }

        // --------------------------------------------------
        // Iterate through and add any new labels
        // --------------------------------------------------
        DataFileTag newTagObj;
        for (String tagLabel : this.getDataFileTags()) {

            if (!currentLabels.contains(tagLabel)) {     // not  already there!

                // redundant "if" check here.  Also done in constructor
                //
                if (DataFileTag.isDataFileTag(tagLabel)) {

                    newTagObj = new DataFileTag();
                    newTagObj.setDataFile(df);
                    newTagObj.setTypeByLabel(tagLabel);
                    df.addTag(newTagObj);

                }
            }
        }

    }
}
