package edu.harvard.iq.dataverse.dataset;

import com.google.common.collect.Lists;
import edu.harvard.iq.dataverse.DataverseFieldTypeInputLevelServiceBean;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetField;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetFieldCompoundValue;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetFieldType;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetFieldUtil;
import edu.harvard.iq.dataverse.persistence.dataset.MetadataBlock;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import org.apache.commons.collections4.SetUtils;

import javax.ejb.Stateless;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author skraffmiller
 */
@Stateless
public class DatasetFieldsInitializer {
    
    private DataverseFieldTypeInputLevelServiceBean dataverseFieldTypeInputLevelService;


    // -------------------- CONSTRUCTORS --------------------
    
    @Deprecated /* JEE requirement */
    DatasetFieldsInitializer() {
    }
    
    @Inject
    public DatasetFieldsInitializer(DataverseFieldTypeInputLevelServiceBean dataverseFieldTypeInputLevelService) {
        this.dataverseFieldTypeInputLevelService = dataverseFieldTypeInputLevelService;
    }

    // -------------------- LOGIC --------------------
    
    /**
     * Prepares list of dataset fields that can be used
     * for metadata view. Preparing consists of following steps:
     * <ul>
     * <li>Filter empty fields</li>
     * <li>Sort fields by {@link DatasetFieldType#getDisplayOrder()}</li>
     * </ul>
     * 
     * @param datasetFields - initial dataset fields
     * @return dataset fields suitable for view operation
     */
    public List<DatasetField> prepareDatasetFieldsForView(List<DatasetField> datasetFields) {
        List<DatasetField> newDatasetFields = filterEmptyFields(datasetFields);
        newDatasetFields = sortDatasetFieldsRecursively(newDatasetFields);
        
        return newDatasetFields;
    }
    
    /**
     * Prepares list of dataset fields that can be used
     * for metadata edit. Preparing consists of following steps:
     * <ul>
     * <li>Create blank fields if they are not present in current fields</li>
     * <li>Filter empty fields from metadata blocks not defined by dataverse</li>
     * <li>Sort fields by {@link DatasetFieldType#getDisplayOrder()}</li>
     * <li>Setting {@link DatasetField#isInclude()} flag</li>
     * </ul>
     * 
     * @param datasetFields - initial dataset fields
     * @param metadataBlocksDataverse - dataverse used as definition of possible dataset fields
     * @return dataset fields suitable for edit operation
     */
    public List<DatasetField> prepareDatasetFieldsForEdit(List<DatasetField> datasetFields, Dataverse metadataBlocksDataverse) {
        List<DatasetField> newDatasetFields = Lists.newArrayList(datasetFields);
        
        newDatasetFields = createBlankDatasetFields(newDatasetFields, metadataBlocksDataverse);
        
        newDatasetFields = filterEmptyFieldsFromUnknownBlocks(newDatasetFields, metadataBlocksDataverse);
        
        newDatasetFields = sortDatasetFieldsRecursively(newDatasetFields);

        updateDatasetFieldIncludeFlag(newDatasetFields, metadataBlocksDataverse);
        
        return newDatasetFields;
    }
    
    /**
     * Groups dataset fields with the same metadata block and
     * updates {@link MetadataBlock#isEmpty()} and {@link MetadataBlock#isHasRequired()}
     * flags.
     * 
     * @param datasetFields - fields to group
     * @return grouped dataset fields
     */
    public Map<MetadataBlock, List<DatasetField>> groupAndUpdateEmptyAndRequiredFlag(List<DatasetField> datasetFields) {
        Map<MetadataBlock, List<DatasetField>> metadataBlocks = DatasetFieldUtil.groupByBlock(datasetFields);
        updateEmptyAndHasRequiredFlag(metadataBlocks);
        
        return metadataBlocks;
    }

    // -------------------- PRIVATE --------------------
    
    /***
    *
    * Note: Updated to retrieve DataverseFieldTypeInputLevel objects in single query
    *
    */
   private void updateDatasetFieldIncludeFlag(List<DatasetField> datasetFields, Dataverse metadataBlocksDataverse) {
       
       List<DatasetField> flatDatasetFields = DatasetFieldUtil.getFlatDatasetFields(datasetFields);
       List<Long> datasetFieldTypeIds = new ArrayList<>();
       
       for (DatasetField dsf: flatDatasetFields) {
           datasetFieldTypeIds.add(dsf.getDatasetFieldType().getId());
       }
       
       List<Long> fieldTypeIdsToHide = dataverseFieldTypeInputLevelService
               .findByDataverseIdAndDatasetFieldTypeIdList(metadataBlocksDataverse.getId(), datasetFieldTypeIds).stream()
               .filter(inputLevel -> !inputLevel.isInclude())
               .map(inputLevel -> inputLevel.getDatasetFieldType().getId())
               .collect(Collectors.toList());
       
       
       for (DatasetField dsf: flatDatasetFields) {
           dsf.setInclude(true);
           if (fieldTypeIdsToHide.contains(dsf.getDatasetFieldType().getId())) {
               dsf.setInclude(false);
           }
       }
   }
    
    
    // TODO: clean up init methods and get them to work, cascading all the way down.
    // right now, only work for one level of compound objects
    private DatasetField createBlankChildDatasetFields(DatasetField dsf) {
        if (dsf.getDatasetFieldType().isCompound()) {
            for (DatasetFieldCompoundValue cv : dsf.getDatasetFieldCompoundValues()) {
                // for each compound value; check the datasetfieldTypes associated with its type
                Set<DatasetFieldType> allChildFieldTypes = new HashSet<>(dsf.getDatasetFieldType().getChildDatasetFieldTypes());
                
                Set<DatasetFieldType> alreadyPresentChildFieldTypes = new HashSet<>();
                for (DatasetField df: cv.getChildDatasetFields()) {
                    DatasetFieldType dsft = df.getDatasetFieldType();
                    alreadyPresentChildFieldTypes.add(dsft);
                }
                
                Set<DatasetFieldType> missingChildFieldTypes = SetUtils.difference(allChildFieldTypes, alreadyPresentChildFieldTypes);
                
                missingChildFieldTypes.forEach(dsft -> {
                    cv.getChildDatasetFields().add(DatasetField.createNewEmptyChildDatasetField(dsft, cv));
                });
            }
        }

        return dsf;
    }

    private List<DatasetField> createBlankDatasetFields(List<DatasetField> datasetFields, Dataverse metadataBlocksDataverse) {
        Set<MetadataBlock> dataverseMetadataBlocks = new HashSet<>(metadataBlocksDataverse.getMetadataBlocks());
        
        //retList - Return List of values
        List<DatasetField> retList = new ArrayList<>();
        for (DatasetField dsf : datasetFields) {
            retList.add(createBlankChildDatasetFields(dsf));
        }

        Set<DatasetFieldType> allFieldTypes = new HashSet<>();
        for (MetadataBlock mdb: dataverseMetadataBlocks) {
            for (DatasetFieldType dsft: mdb.getDatasetFieldTypes()) {
                if (!dsft.isSubField()) {
                    allFieldTypes.add(dsft);
                }
            }
        }
        
        Set<DatasetFieldType> alreadyPresentFieldTypes = new HashSet<>();
        for (DatasetField df: datasetFields) {
            alreadyPresentFieldTypes.add(df.getDatasetFieldType());
        }
        
        Set<DatasetFieldType> missingFieldTypes = SetUtils.difference(allFieldTypes, alreadyPresentFieldTypes);
        
        missingFieldTypes.forEach(dsft -> {
            retList.add(DatasetField.createNewEmptyDatasetField(dsft, null));
        });

        return retList;
    }

    private List<DatasetField> sortDatasetFieldsRecursively(List<DatasetField> dsfList) {
        List<DatasetField> sortedDatasetFields = sortDatasetFields(dsfList);
        
        for (DatasetField dsf : sortedDatasetFields) {
            if (dsf.getDatasetFieldType().isCompound()) {
                for (DatasetFieldCompoundValue cv : dsf.getDatasetFieldCompoundValues()) {
                    List<DatasetField> sortedChildren = sortDatasetFields(cv.getChildDatasetFields());
                    cv.setChildDatasetFields(sortedChildren);
                }
            }
        }
        
        return sortedDatasetFields;
    }
    
    private List<DatasetField> sortDatasetFields(List<DatasetField> dsfList) {
        List<DatasetField> sortedDatasetFields = Lists.newArrayList(dsfList);
        Collections.sort(sortedDatasetFields, Comparator.comparingInt(df -> df.getDatasetFieldType().getDisplayOrder()));
        return sortedDatasetFields;
    }

    private List<DatasetField> filterEmptyFields(List<DatasetField> datasetFields) {
        List<DatasetField> notEmptyFields = new ArrayList<>();
        for (DatasetField dsf : datasetFields) {
            if (!dsf.isEmptyForDisplay()) {
                notEmptyFields.add(dsf);
            }
        }
        return notEmptyFields;
    }
    
    private List<DatasetField> filterEmptyFieldsFromUnknownBlocks(List<DatasetField> datasetFields, Dataverse metadataBlocksDataverse) {
        Set<MetadataBlock> dataverseMetadataBlocks = new HashSet<>(metadataBlocksDataverse.getMetadataBlocks());
        
        List<DatasetField> newDatasetFields = new ArrayList<>();
        for (DatasetField dsf : datasetFields) {
            MetadataBlock metadataBlockOfField = dsf.getDatasetFieldType().getMetadataBlock();
            
            if (!dsf.isEmptyForDisplay() || dataverseMetadataBlocks.contains(metadataBlockOfField)) {
                newDatasetFields.add(dsf);
            }
        }
        return newDatasetFields;
    }
    
    private void updateEmptyAndHasRequiredFlag(Map<MetadataBlock, List<DatasetField>> metadataBlocksForEdit) {
        for (MetadataBlock mdb : metadataBlocksForEdit.keySet()) {
            mdb.setEmpty(allFieldsEmpty(metadataBlocksForEdit.get(mdb)));
            mdb.setHasRequired(anyFieldsRequired(metadataBlocksForEdit.get(mdb)));
        }
    }

    private boolean anyFieldsRequired(List<DatasetField> datasetFields) {
        return datasetFields.stream().anyMatch(DatasetField::isRequired);
    }
    
    private boolean allFieldsEmpty(List<DatasetField> datasetFields) {
        return datasetFields.stream().allMatch(DatasetField::isEmptyForDisplay);
    }
}
