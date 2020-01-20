package edu.harvard.iq.dataverse.persistence.dataset;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Utility class with common operations on dataset fields
 *
 * @author madryk
 */
public class DatasetFieldUtil {

    private DatasetFieldUtil() {
        throw new IllegalArgumentException("Could not be instantiated");
    }

    // -------------------- LOGIC -------------------

    @Deprecated
    public static List<DatasetField> getFlatDatasetFields(List<DatasetField> datasetFields) {
        List<DatasetField> retList = new LinkedList<>();
        for (DatasetField dsf : datasetFields) {
            retList.add(dsf);
            if (dsf.getDatasetFieldType().isCompound()) {
                retList.addAll(getFlatDatasetFields(dsf.getDatasetFieldsChildren()));

            }
        }
        return retList;
    }

    public static List<DatasetField> mergeFieldsToOldModel(List<DatasetField> fieldsToMerge) {
        ArrayList<DatasetField> fieldsToModify = new ArrayList<>(fieldsToMerge);
        ArrayList<DatasetField> mergedFields = new ArrayList<>();

        Map<FieldType, List<DatasetField>> fieldsGroupedByType = fieldsToModify.stream()
                .collect(Collectors.groupingBy(datasetField -> datasetField.getDatasetFieldType().getFieldType()));

        for (List<DatasetField> datasetFields : fieldsGroupedByType.values()) {
            if (datasetFields.size() > 1 && datasetFields.get(0).getDatasetFieldType().isCompound()) {
                for (int secondLoop = 1; secondLoop < datasetFields.size(); secondLoop++) {

                    datasetFields.get(0).getDatasetFieldsChildren().addAll(
                            datasetFields.get(secondLoop).getDatasetFieldsChildren());
                }
            }

            if (datasetFields.get(0).getDatasetFieldType().isCompound()) {
                mergedFields.add(datasetFields.get(0));
            } else {
                mergedFields.addAll(datasetFields);
            }
        }

        return mergedFields;
    }

    public static String joinCompoundFieldValues(DatasetField fieldsToJoin) {

        return fieldsToJoin.getDatasetFieldsChildren().stream()
                .filter(datasetField -> datasetField.getFieldValue().isDefined())
                .map(datasetField -> datasetField.getFieldValue().get())
                .collect(Collectors.joining("; "));
    }

    public static String joinPrimitiveFieldValues(List<DatasetField> fieldsToJoin) {

        return fieldsToJoin.stream()
                .filter(datasetField -> datasetField.getFieldValue().isDefined())
                .map(datasetField -> datasetField.getFieldValue().get())
                .collect(Collectors.joining("; "));
    }

    public String getCompoundDisplayValue(DatasetField parentDsf) {

        return parentDsf.getDatasetFieldsChildren().stream()
                .filter(datasetField -> datasetField.getFieldValue().isDefined())
                .map(datasetField -> datasetField.getFieldValue().get())
                .map(String::trim)
                .collect(Collectors.joining("; "));
    }

    public static List<DatasetField> copyDatasetFields(List<DatasetField> copyFromList) {
        List<DatasetField> retList = new ArrayList<>();

        for (DatasetField sourceDsf : copyFromList) {
            retList.add(sourceDsf.copy());
        }

        return retList;
    }

    public static Map<MetadataBlock, List<DatasetField>> groupByBlock(List<DatasetField> datasetFields) {
        Map<MetadataBlock, List<DatasetField>> metadataBlocks = new TreeMap<>(Comparator.comparingLong(mb -> mb.getId()));

        for (DatasetField dsf : datasetFields) {
            MetadataBlock metadataBlockOfField = dsf.getDatasetFieldType().getMetadataBlock();
            metadataBlocks.putIfAbsent(metadataBlockOfField, new ArrayList<>());
            metadataBlocks.get(metadataBlockOfField).add(dsf);
        }

        return metadataBlocks;
    }

    /**
     * Returns merged dataset fields from two given dataset field lists.
     * Merging is based on equality of {@link DatasetField#getDatasetFieldType()}.
     * <p>
     * If both source lists contains dataset field with the same {@link DatasetField#getDatasetFieldType()}
     * then resulting list will contain only dataset field from the second
     * source list.
     */
    public static List<DatasetField> mergeDatasetFields(List<DatasetField> fields1, List<DatasetField> fields2) {
        Map<DatasetFieldType, DatasetField> datasetFieldsMap = new LinkedHashMap<>();

        for (DatasetField datasetField : fields1) {
            datasetFieldsMap.put(datasetField.getDatasetFieldType(), datasetField);
        }
        for (DatasetField datasetField : fields2) {
            datasetFieldsMap.put(datasetField.getDatasetFieldType(), datasetField);
        }

        return new ArrayList<>(datasetFieldsMap.values());
    }
}
