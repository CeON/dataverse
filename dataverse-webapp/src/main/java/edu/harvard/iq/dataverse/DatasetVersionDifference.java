package edu.harvard.iq.dataverse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang.StringUtils;
import edu.harvard.iq.dataverse.DataFile.ChecksumType;
import edu.harvard.iq.dataverse.license.FileTermsOfUse;
import edu.harvard.iq.dataverse.license.FileTermsOfUse.TermsOfUseType;
import edu.harvard.iq.dataverse.util.BundleUtil;

/**
 * @author skraffmiller
 */
public final class DatasetVersionDifference {

    private DatasetVersion newVersion;
    private DatasetVersion originalVersion;
    
    private List<List<DatasetFieldDiff>> detailDataByBlock = new ArrayList<>();
    
    private List<FileMetadata> addedFiles = new ArrayList<>();
    private List<FileMetadata> removedFiles = new ArrayList<>();
    private List<FileMetadataDiff> changedFileMetadata = new ArrayList<>();
    private List<TermsOfUseDiff> changedFileTerms = new ArrayList<>();
    private List<FileMetadataDiff> replacedFiles = new ArrayList<>();
    
    
    private List<DatasetFileDifferenceItem> datasetFilesDiffList = new ArrayList<>();
    private List<DatasetFileTermDifferenceItem> datasetFileTermsDiffList = new ArrayList<>();
    private List<DatasetReplaceFileItem> datasetFilesReplacementList = new ArrayList<>();
    
    
    private List<DatasetFieldChangeCounts> summaryDataForNote = new ArrayList<>();
    private List<MetadataBlockChangeCounts> blockDataForNote = new ArrayList<>();
    private String fileNote = StringUtils.EMPTY;
    
    
    // -------------------- CONSTRUCTORS --------------------
    
    public DatasetVersionDifference(DatasetVersion newVersion, DatasetVersion originalVersion) {
        this.originalVersion = originalVersion;
        this.newVersion = newVersion;
        //Compare Data
        
        // metadata field difference
        
        Set<DatasetFieldType> originalDatasetFieldTypes = extractDatasetFieldTypes(originalVersion);
        Set<DatasetFieldType> newDatasetFieldTypes = extractDatasetFieldTypes(newVersion);
        
        for (DatasetFieldType inBothVersionsFieldType: SetUtils.intersection(originalDatasetFieldTypes, newDatasetFieldTypes)) {
            DatasetField originalDatasetField = extractFieldWithType(originalVersion.getDatasetFields(), inBothVersionsFieldType);
            DatasetField newDatasetField = extractFieldWithType(newVersion.getDatasetFields(), inBothVersionsFieldType);
            
            updateSameFieldTypeSummary(originalDatasetField, newDatasetField);
        }
        for (DatasetFieldType removedFieldType: SetUtils.difference(originalDatasetFieldTypes, newDatasetFieldTypes)) {
            DatasetField originalDatasetField = extractFieldWithType(originalVersion.getDatasetFields(), removedFieldType);
            if (!originalDatasetField.isEmpty()) {
                int valuesCount = extractFieldValuesCount(originalDatasetField);
                updateBlockSummary(originalDatasetField.getDatasetFieldType().getMetadataBlock(), 0, valuesCount, 0);
                addToSummary(originalDatasetField, null);
            }
        }
        for (DatasetFieldType addedFieldType: SetUtils.difference(newDatasetFieldTypes, originalDatasetFieldTypes)) {
            DatasetField newDatasetField = extractFieldWithType(newVersion.getDatasetFields(), addedFieldType);
            if (!newDatasetField.isEmpty()) {
                int valuesCount = extractFieldValuesCount(newDatasetField);
                updateBlockSummary(newDatasetField.getDatasetFieldType().getMetadataBlock(), valuesCount, 0, 0);
                addToSummary(null, newDatasetField);
            }
        }
        
        //Sort within blocks by datasetfieldtype dispaly order then....
        //sort via metadatablock order - citation first...
        for (List<DatasetFieldDiff> blockList : detailDataByBlock) {
            Collections.sort(blockList, Comparator.comparing(x -> x.getOldValue().getDatasetFieldType().getDisplayOrder()));
        }
        Collections.sort(detailDataByBlock, Comparator.comparing(x -> x.get(0).getOldValue().getDatasetFieldType().getMetadataBlock().getId()));
        
        
        // files difference
        
        for (FileMetadata fmdo : originalVersion.getFileMetadatas()) {
            boolean deleted = true;
            for (FileMetadata fmdn : newVersion.getFileMetadatas()) {
                if (fmdo.getDataFile().equals(fmdn.getDataFile())) {
                    deleted = false;
                    if (!areFilesMetadataEqual(fmdo, fmdn)) {
                        changedFileMetadata.add(new FileMetadataDiff(fmdo, fmdn));
                    }
                    if (!areFileTermsEqual(fmdo.getTermsOfUse(), fmdn.getTermsOfUse())) {
                        changedFileTerms.add(new TermsOfUseDiff(fmdo.getTermsOfUse(), fmdn.getTermsOfUse()));
                    }
                    
                    break;
                }
            }
            if (deleted) {
                removedFiles.add(fmdo);
            }
        }
        for (FileMetadata fmdn : newVersion.getFileMetadatas()) {
            boolean added = true;
            for (FileMetadata fmdo : originalVersion.getFileMetadatas()) {
                if (fmdo.getDataFile().equals(fmdn.getDataFile())) {
                    added = false;
                    break;
                }
            }
            if (added) {
                addedFiles.add(fmdn);
            }
        }
        findReplacedFilesAmongAddedAndRemoved();
        initDatasetFilesDifferencesList();
        
        fileNote = buildFileNote();
    }
    
    // -------------------- GETTERS --------------------

    public DatasetVersion getNewVersion() {
        return newVersion;
    }

    public DatasetVersion getOriginalVersion() {
        return originalVersion;
    }

    /**
     * Returns dataset fields that have been changed
     * between two dataset versions (includes added and
     * removed fields).
     * Changes are grouped by metadata blocks.
     */
    public List<List<DatasetFieldDiff>> getDetailDataByBlock() {
        return detailDataByBlock;
    }

    /**
     * Returns file metadata of files that have
     * been added (that exists only in old version)
     */
    public List<FileMetadata> getAddedFiles() {
        return addedFiles;
    }

    /**
     * Returns file metadata of files that have
     * been removed (that exists only in new version)
     */
    public List<FileMetadata> getRemovedFiles() {
        return removedFiles;
    }
    
    /**
     * Returns files metadata that have been changed
     * between two dataset versions.
     */
    public List<FileMetadataDiff> getChangedFileMetadata() {
        return changedFileMetadata;
    }
    
    /**
     * Returns differences between files that have
     * changed between two dataset versions (includes added
     * and removed files).
     * <p>
     * Note that change in terms of use of files
     * is not listed here but in {@link #getDatasetFileTermsDiffList()}
     */
    public List<DatasetFileDifferenceItem> getDatasetFilesDiffList() {
        return datasetFilesDiffList;
    }
    
    /**
     * Returns files that have different terms of use
     * between two dataset versions
     */
    public List<DatasetFileTermDifferenceItem> getDatasetFileTermsDiffList() {
        return datasetFileTermsDiffList;
    }

    /**
     * Returns differences in files that have been
     * replaced between two dataset versions.
     */
    public List<DatasetReplaceFileItem> getDatasetFilesReplacementList() {
        return datasetFilesReplacementList;
    }

    /**
     * Returns statistical summary of changes between two versions
     * in dataset fields.
     * <p>
     * For example if element on returned list is:
     * <code>[[DatasetFieldType[author]], 3, 5, 1]</code>
     * that means that 3 authors were added; 5 was removed and in 1 author some metadata was changed.
     * <p>
     * Note that only fields with {@link DatasetFieldType#isDisplayOnCreate()} flag
     * are included in returned summary.
     */
    public List<DatasetFieldChangeCounts> getSummaryDataForNote() {
        return summaryDataForNote;
    }

    /**
     * Returns statistical summary of changes between two versions
     * in dataset fields inside metadata block.
     * <p>
     * For example if element on returned list is:
     * <code>[[MetadataBlock[citation]], addedCount=3, removedCount=5, changedCount=1]</code>
     * that means that 3 field values were added to fields within
     * citation block; 5 was removed and 1 field was changed.
     * <p>
     * Note that only fields without {@link DatasetFieldType#isDisplayOnCreate()} flag
     * are included in returned summary.
     */
    public List<MetadataBlockChangeCounts> getBlockDataForNote() {
        return blockDataForNote;
    }
    
    /**
     * Returns statistical summary of dataset files that changed
     * between two versions in form of formatted and localized string.
     */
    public String getFileNote() {
        return fileNote;
    }
    
    // -------------------- LOGIC --------------------

    public String getEditSummaryForLog() {

        String retVal = "";

        retVal = System.lineSeparator() + this.newVersion.getTitle() + " (" + this.originalVersion.getDataset().getIdentifier() + ") was updated " + new Date();

        String valueString = "";
        String groupString = "";

        //Metadata differences displayed by Metdata block
        if (!this.detailDataByBlock.isEmpty()) {
            for (List<DatasetFieldDiff> blocks : detailDataByBlock) {
                groupString = System.lineSeparator() + " Metadata Block";
                
                String blockDisplay = " " + blocks.get(0).getOldValue().getDatasetFieldType().getMetadataBlock().getName() + ": " + System.lineSeparator();
                groupString += blockDisplay;
                for (DatasetFieldDiff dsfArray : blocks) {
                    valueString = " Field: ";
                    String title = dsfArray.getOldValue().getDatasetFieldType().getName();
                    valueString += title;
                    String oldValue = " Changed From: ";
                    
                    DatasetField oldField = dsfArray.getOldValue();
                    if (!oldField.isEmpty()) {
                        if (oldField.getDatasetFieldType().isPrimitive()) {
                            oldValue += oldField.getRawValue();
                        } else {
                            oldValue += oldField.getCompoundRawValue();
                        }
                    }
                    valueString += oldValue;

                    DatasetField newField = dsfArray.getNewValue();
                    String newValue = " To: ";
                    if (!newField.isEmpty()) {
                        if (newField.getDatasetFieldType().isPrimitive()) {
                            newValue += newField.getRawValue();
                        } else {
                            newValue += newField.getCompoundRawValue();
                        }

                    }
                    valueString += newValue;
                    groupString += valueString + System.lineSeparator();
                }
                retVal += groupString + System.lineSeparator();
            }
        }

        // File Differences
        String fileDiff = System.lineSeparator() + "Files: " + System.lineSeparator();
        if (!this.getDatasetFilesDiffList().isEmpty()) {

            String itemDiff;

            for (DatasetFileDifferenceItem item : this.getDatasetFilesDiffList()) {
                FileMetadataDifferenceItem metadataDiff = item.getDifference();
                itemDiff = "File ID: " + item.getFileSummary().getFileId();

                itemDiff += buildValuesDiffString("Name", metadataDiff.fileName1, metadataDiff.fileName2);
                itemDiff += buildValuesDiffString("Type", metadataDiff.fileType1, metadataDiff.fileType2);
                itemDiff += buildValuesDiffString("Size", metadataDiff.fileSize1, metadataDiff.fileSize2);
                itemDiff += buildValuesDiffString("Tag(s)", metadataDiff.fileCat1, metadataDiff.fileCat2);
                itemDiff += buildValuesDiffString("Description", metadataDiff.fileDesc1, metadataDiff.fileDesc1);
                itemDiff += buildValuesDiffString("Provenance Description", metadataDiff.fileProvFree1, metadataDiff.fileProvFree2);

                fileDiff += itemDiff;
            }

            retVal += fileDiff;
        }

        String fileReplaced = System.lineSeparator() + "File(s) Replaced: " + System.lineSeparator();
        if (!this.getDatasetFilesReplacementList().isEmpty()) {
            String itemDiff;
            for (DatasetReplaceFileItem item : this.getDatasetFilesReplacementList()) {
                FileMetadataDifferenceItem metadataDiff = item.getMetadataDifference();
                itemDiff = "";
                itemDiff += buildValuesDiffString("Name", metadataDiff.fileName1, metadataDiff.fileName2);
                itemDiff += buildValuesDiffString("Type", metadataDiff.fileType1, metadataDiff.fileType2);
                itemDiff += buildValuesDiffString("Size", metadataDiff.fileSize1, metadataDiff.fileSize2);
                itemDiff += buildValuesDiffString("Tag(s)", metadataDiff.fileCat1, metadataDiff.fileCat2);
                itemDiff += buildValuesDiffString("Description", metadataDiff.fileDesc1, metadataDiff.fileDesc2);
                itemDiff += buildValuesDiffString("Provenance Description", metadataDiff.fileProvFree1, metadataDiff.fileProvFree2);
                fileReplaced += itemDiff;
            }
            retVal += fileReplaced;
        }

        return retVal;
    }
    
    // -------------------- PRIVATE --------------------
    
    private Set<DatasetFieldType> extractDatasetFieldTypes(DatasetVersion datasetVersion) {
        Set<DatasetFieldType> datasetFieldTypes = new HashSet<DatasetFieldType>();
        for (DatasetField dsfo : datasetVersion.getDatasetFields()) {
            datasetFieldTypes.add(dsfo.getDatasetFieldType());
        }
        return datasetFieldTypes;
    }
    
    private DatasetField extractFieldWithType(List<DatasetField> datasetFields, DatasetFieldType datasetFieldType) {
        for (DatasetField dsf : datasetFields) {
            if (dsf.getDatasetFieldType().equals(datasetFieldType)) {
                return dsf;
            }
        }
        return null;
    }
    
    private int extractFieldValuesCount(DatasetField datasetField) {
        if (datasetField.getDatasetFieldType().isPrimitive()) {
            if (datasetField.getDatasetFieldType().isControlledVocabulary()) {
                return datasetField.getControlledVocabularyValues().size();
            } else {
                return datasetField.getDatasetFieldValues().size();
            }
        }
        return datasetField.getDatasetFieldCompoundValues().size();
    }
    

    private void findReplacedFilesAmongAddedAndRemoved() {
        if (addedFiles.isEmpty() || removedFiles.isEmpty()) {
            return;
        }
        for (FileMetadata added : addedFiles) {
            DataFile addedDF = added.getDataFile();
            Long replacedId = addedDF.getPreviousDataFileId();
            for (FileMetadata removed : removedFiles) {
                DataFile test = removed.getDataFile();
                if (test.getId().equals(replacedId)) {
                    replacedFiles.add(new FileMetadataDiff(removed, added));
                }
            }
        }
        replacedFiles.stream().forEach(replaced -> {
            removedFiles.remove(replaced.getOldValue());
            addedFiles.remove(replaced.getNewValue());
        });
    }


    private void addToSummary(DatasetField dsfo, DatasetField dsfn) {
        if (dsfo == null) {
            dsfo = new DatasetField();
            dsfo.setDatasetFieldType(dsfn.getDatasetFieldType());
        }
        if (dsfn == null) {
            dsfn = new DatasetField();
            dsfn.setDatasetFieldType(dsfo.getDatasetFieldType());
        }
        MetadataBlock blockToUpdate = dsfo.getDatasetFieldType().getMetadataBlock();
        List<DatasetFieldDiff> blockListDiffToUpdate = extractOrCreateDiffForBlock(blockToUpdate);
        
        blockListDiffToUpdate.add(new DatasetFieldDiff(dsfo, dsfn));
    }

    private List<DatasetFieldDiff> extractOrCreateDiffForBlock(MetadataBlock blockToUpdate) {
        for (List<DatasetFieldDiff> blockListDiff : detailDataByBlock) {
            MetadataBlock block = blockListDiff.get(0).getOldValue().getDatasetFieldType().getMetadataBlock();
            if (block.equals(blockToUpdate)) {
                return blockListDiff;
            }
        }
        List<DatasetFieldDiff> newBlockListDiff = new ArrayList<>();
        detailDataByBlock.add(newBlockListDiff);
        return newBlockListDiff;
    }

    private void updateBlockSummary(MetadataBlock metadataBlock, int added, int deleted, int changed) {
        
        for (int i=0; i<blockDataForNote.size(); ++i) {
            MetadataBlock metadataBlockFromBlockData = blockDataForNote.get(i).getItem();
            if (metadataBlockFromBlockData.equals(metadataBlock)) {
                blockDataForNote.get(i).incrementAdded(added);
                blockDataForNote.get(i).incrementRemoved(deleted);
                blockDataForNote.get(i).incrementChanged(changed);
                return;
            }
        }
        MetadataBlockChangeCounts changeCounts = new MetadataBlockChangeCounts(metadataBlock);
        changeCounts.incrementAdded(added);
        changeCounts.incrementRemoved(deleted);
        changeCounts.incrementChanged(changed);
        blockDataForNote.add(changeCounts);
    }

    private void addToNoteSummary(DatasetFieldType dsft, int added, int deleted, int changed) {
        DatasetFieldChangeCounts counts = new DatasetFieldChangeCounts(dsft);
        counts.incrementAdded(added);
        counts.incrementRemoved(deleted);
        counts.incrementChanged(changed);
        summaryDataForNote.add(counts);
    }

    private boolean areFilesMetadataEqual(FileMetadata fmdo, FileMetadata fmdn) {

        if (!StringUtils.equals(fmdo.getDescription(), fmdn.getDescription())) {
            return false;
        }

        if (!StringUtils.equals(fmdo.getCategoriesByName().toString(), fmdn.getCategoriesByName().toString())) {
            return false;
        }

        if (!StringUtils.equals(fmdo.getLabel(), fmdn.getLabel())) {
            return false;
        }

        if (!StringUtils.equals(fmdo.getProvFreeForm(), fmdn.getProvFreeForm())) {
            return false;
        }

        return true;
    }

    private boolean areFileTermsEqual(FileTermsOfUse termsOriginal, FileTermsOfUse termsNew) {
        if (termsOriginal.getTermsOfUseType() != termsNew.getTermsOfUseType()) {
            return false;
        }
        if (termsOriginal.getTermsOfUseType() == TermsOfUseType.LICENSE_BASED) {
            return termsOriginal.getLicense().getId().equals(termsNew.getLicense().getId());
        }
        if (termsOriginal.getTermsOfUseType() == TermsOfUseType.RESTRICTED) {
            return termsOriginal.getRestrictType() == termsNew.getRestrictType() &&
                    StringUtils.equals(termsOriginal.getRestrictCustomText(), termsNew.getRestrictCustomText());
        }
        return true;
    }

    private List<String> extractValuesToCompare(DatasetField datasetField) {
        
        if (datasetField.getDatasetFieldType().isPrimitive()) {
            return datasetField.getValues();
        }
        
        List<String> values = new ArrayList<String>();
        
        for (DatasetFieldCompoundValue datasetFieldCompoundValueOriginal : datasetField.getDatasetFieldCompoundValues()) {
            String originalValue = "";
            for (DatasetField dsfo : datasetFieldCompoundValueOriginal.getChildDatasetFields()) {
                if (!dsfo.getDisplayValue().isEmpty()) {
                    originalValue += dsfo.getDisplayValue() + ", ";
                }
            }
            values.add(originalValue);
        }
        return values;
    }
    
    private void updateSameFieldTypeSummary(DatasetField originalField, DatasetField newField) {
        int totalAdded = 0;
        int totalDeleted = 0;
        int totalChanged = 0;

        List<String> originalValues = extractValuesToCompare(originalField);
        List<String> newValues = extractValuesToCompare(newField);
        
        for (int i=0; i<originalValues.size(); ++i) {
            String originalValue = originalValues.get(i);
            String newValue = (i < newValues.size()) ? newValues.get(i) : StringUtils.EMPTY;
            
            if (originalValue.isEmpty() && !newValue.isEmpty()) {
                ++totalAdded;
            } else if (!originalValue.isEmpty() && newValue.isEmpty()) {
                ++totalDeleted;
            } else if (!StringUtils.equals(newValue.trim(), originalValue.trim())) {
                ++totalChanged;
            }
        }
        if (newValues.size() > originalValues.size()) {
            totalAdded += (newValues.size() - originalValues.size());
        }
        
        if ((totalAdded + totalDeleted + totalChanged) > 0) {
            if (originalField.getDatasetFieldType().isDisplayOnCreate()) {
                addToNoteSummary(originalField.getDatasetFieldType(), totalAdded, totalDeleted, totalChanged);
            } else {
                updateBlockSummary(originalField.getDatasetFieldType().getMetadataBlock(), totalAdded, totalDeleted, totalChanged);
            }
            addToSummary(originalField, newField);
        }
    }

    private String buildFileNote() {
        
        List<String> fileChangeStrings = new ArrayList<>();

        if (addedFiles.size() > 0) {
            String addedString = BundleUtil.getStringFromBundle("dataset.version.file.added", addedFiles.size());
            fileChangeStrings.add(addedString);
        }

        if (removedFiles.size() > 0) {
            String removedString = BundleUtil.getStringFromBundle("dataset.version.file.removed", removedFiles.size());
            fileChangeStrings.add(removedString);
        }

        if (replacedFiles.size() > 0) {
            String replacedString = BundleUtil.getStringFromBundle("dataset.version.file.replaced", replacedFiles.size());
            fileChangeStrings.add(replacedString);
        }

        if (changedFileMetadata.size() > 0) {
            String changedFileMetadataString = BundleUtil.getStringFromBundle("dataset.version.file.changedMetadata", changedFileMetadata.size());
            fileChangeStrings.add(changedFileMetadataString);
        }

        if (changedFileTerms.size() > 0) {
            String changedFileTermString = BundleUtil.getStringFromBundle("dataset.version.file.changedTerms", changedFileTerms.size());
            fileChangeStrings.add(changedFileTermString);
        }

        if (fileChangeStrings.isEmpty()) {
            return StringUtils.EMPTY;
        }
        return "(" + StringUtils.join(fileChangeStrings, "; ") + ")";
    }

    
    private DatasetReplaceFileItem buildDatasetReplaceFileItem(FileMetadata replacedFile, FileMetadata newFile) {
        DataFile replacedDataFile = replacedFile.getDataFile();
        FileSummary replacedSummary = new FileSummary(
                replacedDataFile.getId().toString(),
                replacedDataFile.getChecksumType(),
                replacedDataFile.getChecksumValue());

        DataFile newDataFile = newFile.getDataFile();
        FileSummary newSummary = new FileSummary(
                newDataFile.getId().toString(),
                newDataFile.getChecksumType(),
                newDataFile.getChecksumValue());
        
        FileMetadataDifferenceItem metadataDiff = new FileMetadataDifferenceItem();
        fillFileMetadataDifference(metadataDiff, replacedFile, newFile);
        
        DatasetReplaceFileItem fdr = new DatasetReplaceFileItem(replacedSummary, newSummary, metadataDiff);
        return fdr;
    }
    
    private DatasetFileDifferenceItem buildDatasetFileDifferenceItem(FileMetadata fm1, FileMetadata fm2) {
        DataFile dataFileForDifference = fm1 != null ? fm1.getDataFile() : fm2.getDataFile();
        FileSummary dataFileSummary = new FileSummary(
                dataFileForDifference.getId().toString(),
                dataFileForDifference.getChecksumType(),
                dataFileForDifference.getChecksumValue());

        FileMetadataDifferenceItem metadataDiff = new FileMetadataDifferenceItem();
        fillFileMetadataDifference(metadataDiff, fm1, fm2);
        
        DatasetFileDifferenceItem fdi = new DatasetFileDifferenceItem(dataFileSummary, metadataDiff);
        
        return fdi;
    }
    
    private void initDatasetFilesDifferencesList() {
        
        replacedFiles.stream()
            .map((replacedPair) -> buildDatasetReplaceFileItem(replacedPair.getOldValue(), replacedPair.getNewValue()))
            .forEach(datasetFilesReplacementList::add);
        
        for (FileMetadata addedFile: addedFiles) {
            datasetFilesDiffList.add(buildDatasetFileDifferenceItem(null, addedFile));
        }
        for (FileMetadata removedFile: removedFiles) {
            datasetFilesDiffList.add(buildDatasetFileDifferenceItem(removedFile, null));
        }
        for (FileMetadataDiff changedPair: changedFileMetadata) {
            FileMetadata originalMetadata = changedPair.getOldValue();
            FileMetadata newMetadata = changedPair.getNewValue();
            datasetFilesDiffList.add(buildDatasetFileDifferenceItem(originalMetadata, newMetadata));
        }
        
        for (TermsOfUseDiff changedTermsPair: changedFileTerms) {
            FileTermsOfUse originalTerms = changedTermsPair.getOldValue();
            FileTermsOfUse newTerms = changedTermsPair.getNewValue();
            DataFile dataFile = originalTerms.getFileMetadata().getDataFile();
            
            datasetFileTermsDiffList.add(new DatasetFileTermDifferenceItem(
                    new FileSummary(dataFile.getId().toString(), dataFile.getChecksumType(), dataFile.getChecksumValue()),
                    originalTerms, newTerms));
        }
    }
    

    private void fillFileMetadataDifference(FileMetadataDifferenceItem fdi, FileMetadata fm1, FileMetadata fm2) {

        if (fm1 == null && fm2 == null) {
            return;
        }
        
        if (fm2 == null) {
            fdi.setFileName1(fm1.getLabel());
            fdi.setFileType1(fm1.getDataFile().getFriendlyType());
            //fdi.setFileSize1(FileUtil. (new File(fm1.getDataFile().getFileSystemLocation()).length()));

            // deprecated: fdi.setFileCat1(fm1.getCategory());
            fdi.setFileDesc1(fm1.getDescription());
            if (!fm1.getCategoriesByName().isEmpty()) {
                fdi.setFileCat1(fm1.getCategoriesByName().toString());
            }

            fdi.setFileProvFree1(fm1.getProvFreeForm());
            fdi.setFile2Empty(true);

        } else if (fm1 == null) {
            fdi.setFile1Empty(true);

            fdi.setFileName2(fm2.getLabel());
            fdi.setFileType2(fm2.getDataFile().getFriendlyType());

            //fdi.setFileSize2(FileUtil.byteCountToDisplaySize(new File(fm2.getStudyFile().getFileSystemLocation()).length()));
            // deprecated: fdi.setFileCat2(fm2.getCategory());
            fdi.setFileDesc2(fm2.getDescription());
            if (!fm2.getCategoriesByName().isEmpty()) {
                fdi.setFileCat2(fm2.getCategoriesByName().toString());
            }
            fdi.setFileProvFree2(fm2.getProvFreeForm());
        } else {
            // Both are non-null metadata objects.
            // We simply go through the 5 metadata fields, if any are
            // different between the 2 versions, we add them to the
            // difference object:

            String value1;
            String value2;

            // filename:
            value1 = fm1.getLabel();
            value2 = fm2.getLabel();

            value1 = StringUtils.trimToEmpty(value1);
            value2 = StringUtils.trimToEmpty(value2);

            if (!value1.equals(value2)) {

                fdi.setFileName1(value1);
                fdi.setFileName2(value2);
            }

            // NOTE:
            // fileType and fileSize will always be the same
            // for the same studyFile! -- so no need to check for differences in
            // these 2 items.
            // file category:
            value1 = fm1.getCategoriesByName().toString();
            value2 = fm2.getCategoriesByName().toString();
            
            value1 = StringUtils.trimToEmpty(value1);
            value2 = StringUtils.trimToEmpty(value2);

            if (!value1.equals(value2)) {
                fdi.setFileCat1(value1);
                fdi.setFileCat2(value2);
            }

            // file description:
            value1 = fm1.getDescription();
            value2 = fm2.getDescription();

            value1 = StringUtils.trimToEmpty(value1);
            value2 = StringUtils.trimToEmpty(value2);

            if (!value1.equals(value2)) {

                fdi.setFileDesc1(value1);
                fdi.setFileDesc2(value2);
            }

            // provenance freeform
            value1 = fm1.getProvFreeForm();
            value2 = fm2.getProvFreeForm();

            value1 = StringUtils.trimToEmpty(value1);
            value2 = StringUtils.trimToEmpty(value2);

            if (!value1.equals(value2)) {

                fdi.setFileProvFree1(value1);
                fdi.setFileProvFree2(value2);
            }
        }
    }
    
    private String buildValuesDiffString(String valueType, String val1, String val2) {
        if (val1 == null && val2 == null) {
            return StringUtils.EMPTY;
        }
        
        return System.lineSeparator() + " " + valueType + ": " +
            StringUtils.defaultString(val1, "N/A") + " : " + StringUtils.defaultString(val2, "N/A ");
    }
    
    
    // -------------------- INNER CLASSES --------------------
    
    public class DifferenceSummaryItem {
        private String displayName;
        private int changed;
        private int added;
        private int deleted;
        private int replaced;
        private boolean multiple;

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public int getChanged() {
            return changed;
        }

        public void setChanged(int changed) {
            this.changed = changed;
        }

        public int getAdded() {
            return added;
        }

        public void setAdded(int added) {
            this.added = added;
        }

        public int getDeleted() {
            return deleted;
        }

        public void setDeleted(int deleted) {
            this.deleted = deleted;
        }

        public int getReplaced() {
            return replaced;
        }

        public void setReplaced(int replaced) {
            this.replaced = replaced;
        }

        public boolean isMultiple() {
            return multiple;
        }

        public void setMultiple(boolean multiple) {
            this.multiple = multiple;
        }


    }

    public class DatasetReplaceFileItem {

        private FileSummary oldFileSummary;
        private FileSummary newFileSummary;
        
        private FileMetadataDifferenceItem metadataDifference;

        public DatasetReplaceFileItem(FileSummary oldFileSummary, FileSummary newFileSummary,
                FileMetadataDifferenceItem metadataDifference) {
            this.oldFileSummary = oldFileSummary;
            this.newFileSummary = newFileSummary;
            this.metadataDifference = metadataDifference;
        }

        public FileSummary getOldFileSummary() {
            return oldFileSummary;
        }

        public FileSummary getNewFileSummary() {
            return newFileSummary;
        }

        public FileMetadataDifferenceItem getMetadataDifference() {
            return metadataDifference;
        }
    }

    public class DatasetFileTermDifferenceItem {

        private FileSummary fileSummary;
        
        private FileTermsOfUse oldTerms;
        private FileTermsOfUse newTerms;
        
        public DatasetFileTermDifferenceItem(FileSummary fileSummary, FileTermsOfUse oldTerms, FileTermsOfUse newTerms) {
            this.fileSummary = fileSummary;
            this.oldTerms = oldTerms;
            this.newTerms = newTerms;
        }

        public FileSummary getFileSummary() {
            return fileSummary;
        }

        public FileTermsOfUse getOldTerms() {
            return oldTerms;
        }

        public FileTermsOfUse getNewTerms() {
            return newTerms;
        }
    }

    public class DatasetFileDifferenceItem {
        
        private FileSummary fileSummary;
        private FileMetadataDifferenceItem difference;
        
        public DatasetFileDifferenceItem(FileSummary fileSummary, FileMetadataDifferenceItem difference) {
            this.fileSummary = fileSummary;
            this.difference = difference;
        }

        public FileSummary getFileSummary() {
            return fileSummary;
        }

        public FileMetadataDifferenceItem getDifference() {
            return difference;
        }
    }
    
    public class FileMetadataDifferenceItem {
        
        private String fileName1;
        private String fileType1;
        private String fileSize1;
        private String fileCat1;
        private String fileDesc1;
        private String fileProvFree1;

        private String fileName2;
        private String fileType2;
        private String fileSize2;
        private String fileCat2;
        private String fileDesc2;
        private String fileProvFree2;

        public String getFileProvFree1() {
            return fileProvFree1;
        }

        public void setFileProvFree1(String fileProvFree1) {
            this.fileProvFree1 = fileProvFree1;
        }

        public String getFileProvFree2() {
            return fileProvFree2;
        }

        public void setFileProvFree2(String fileProvFree2) {
            this.fileProvFree2 = fileProvFree2;
        }

        private boolean file1Empty = false;
        private boolean file2Empty = false;

        public String getFileName1() {
            return fileName1;
        }

        public void setFileName1(String fn) {
            this.fileName1 = fn;
        }

        public String getFileType1() {
            return fileType1;
        }

        public void setFileType1(String ft) {
            this.fileType1 = ft;
        }

        public String getFileSize1() {
            return fileSize1;
        }

        public void setFileSize1(String fs) {
            this.fileSize1 = fs;
        }

        public String getFileCat1() {
            return fileCat1;
        }

        public void setFileCat1(String fc) {
            this.fileCat1 = fc;
        }

        public String getFileDesc1() {
            return fileDesc1;
        }

        public void setFileDesc1(String fd) {
            this.fileDesc1 = fd;
        }

        public String getFileName2() {
            return fileName2;
        }

        public void setFileName2(String fn) {
            this.fileName2 = fn;
        }

        public String getFileType2() {
            return fileType2;
        }

        public void setFileType2(String ft) {
            this.fileType2 = ft;
        }

        public String getFileSize2() {
            return fileSize2;
        }

        public void setFileSize2(String fs) {
            this.fileSize2 = fs;
        }

        public String getFileCat2() {
            return fileCat2;
        }

        public void setFileCat2(String fc) {
            this.fileCat2 = fc;
        }

        public String getFileDesc2() {
            return fileDesc2;
        }

        public void setFileDesc2(String fd) {
            this.fileDesc2 = fd;
        }

        public boolean isFile1Empty() {
            return file1Empty;
        }

        public boolean isFile2Empty() {
            return file2Empty;
        }

        public void setFile1Empty(boolean state) {
            file1Empty = state;
        }

        public void setFile2Empty(boolean state) {
            file2Empty = state;
        }

    }
    
    public class FileSummary {

        private String fileId;
        private DataFile.ChecksumType fileChecksumType;
        private String fileChecksumValue;
        
        public FileSummary(String fileId, ChecksumType fileChecksumType, String fileChecksumValue) {
            this.fileId = fileId;
            this.fileChecksumType = fileChecksumType;
            this.fileChecksumValue = fileChecksumValue;
        }
        
        public String getFileId() {
            return fileId;
        }
        public DataFile.ChecksumType getFileChecksumType() {
            return fileChecksumType;
        }
        public String getFileChecksumValue() {
            return fileChecksumValue;
        }
    }
    
    public static class TermsOfUseDiff extends ItemDiff<FileTermsOfUse> {

        public TermsOfUseDiff(FileTermsOfUse oldValue, FileTermsOfUse newValue) {
            super(oldValue, newValue);
        }
        
    }
    
    public static class FileMetadataDiff extends ItemDiff<FileMetadata> {

        public FileMetadataDiff(FileMetadata oldValue, FileMetadata newValue) {
            super(oldValue, newValue);
        }
        
    }
    
    public static class DatasetFieldDiff extends ItemDiff<DatasetField> {

        public DatasetFieldDiff(DatasetField oldValue, DatasetField newValue) {
            super(oldValue, newValue);
        }
        
    }
    
    public static class ItemDiff<T> {
        private T oldValue;
        private T newValue;
        
        // -------------------- CONSTRUCTORS --------------------
        
        public ItemDiff(T oldValue, T newValue) {
            this.oldValue = oldValue;
            this.newValue = newValue;
        }

        // -------------------- GETTERS --------------------
        
        public T getOldValue() {
            return oldValue;
        }

        public T getNewValue() {
            return newValue;
        }
    }
    
    
    public static class MetadataBlockChangeCounts extends ChangeCounts<MetadataBlock> {

        public MetadataBlockChangeCounts(MetadataBlock item) {
            super(item);
        }
        
    }
    
    public static class DatasetFieldChangeCounts extends ChangeCounts<DatasetFieldType> {

        public DatasetFieldChangeCounts(DatasetFieldType item) {
            super(item);
        }
    }
    
    public static class ChangeCounts<T> {
        private T item;
        private int addedCount;
        private int removedCount;
        private int changedCount;
        
        // -------------------- CONSTRUCTORS --------------------
        
        public ChangeCounts(T item) {
            this.item = item;
        }
        
        // -------------------- GETTERS --------------------
        
        public T getItem() {
            return item;
        }
        public int getAddedCount() {
            return addedCount;
        }
        public int getRemovedCount() {
            return removedCount;
        }
        public int getChangedCount() {
            return changedCount;
        }
        
        // -------------------- LOGIC --------------------
        
        public void incrementAdded(int count) {
            addedCount += count;
        }
        public void incrementRemoved(int count) {
            removedCount += count;
        }
        public void incrementChanged(int count) {
            changedCount += count;
        }
    }
}
