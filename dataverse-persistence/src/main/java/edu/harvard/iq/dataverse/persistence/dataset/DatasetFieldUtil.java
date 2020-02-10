package edu.harvard.iq.dataverse.persistence.dataset;

import java.util.ArrayList;
import java.util.Collection;
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

    public static String joinAllValues(DatasetField fieldToJoin) {
        if (!fieldToJoin.isEmpty()) {
            if (fieldToJoin.getDatasetFieldType().isPrimitive()) {
                return fieldToJoin.getRawValue();
            } else {
                return fieldToJoin.getCompoundRawValue();
            }
        }

        return "";
    }

    /**
     * Joins values of the fields with ';' delimiter.
     */
    public static String joinAllValues(Collection<DatasetField> fields) {
        return fields.stream()
                .map(DatasetFieldUtil::joinAllValues)
                .collect(Collectors.joining("; "));
    }

    /**
     * Copies original datasetfield to newly created datasetfield alongside values and type.
     * @param originalDsf datasetfield to copy.
     * @return Copied datasetfield
     */
    public static DatasetField copyDatasetField(DatasetField originalDsf){
        DatasetField dsf = new DatasetField();
        dsf.setDatasetFieldType(originalDsf.getDatasetFieldType());
        dsf.setControlledVocabularyValues(originalDsf.getControlledVocabularyValues());
        dsf.setFieldValue(originalDsf.getFieldValue().getOrNull());

        for (DatasetField dsfChildren : originalDsf.getDatasetFieldsChildren()) {
            dsf.getDatasetFieldsChildren().add(copyDatasetField(dsfChildren, dsf));
        }

        return dsf;
    }

    public static List<DatasetField> copyDatasetFields(List<DatasetField> copyFromList) {
        List<DatasetField> retList = new ArrayList<>();

        for (DatasetField sourceDsf : copyFromList) {
            retList.add(copyDatasetField(sourceDsf));
        }

        return retList;
    }

    public static Map<MetadataBlock, List<DatasetField>> groupByBlock(List<DatasetField> datasetFields) {
        Map<MetadataBlock, List<DatasetField>> metadataBlocks = new TreeMap<>(Comparator.comparingLong(MetadataBlock::getId));

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

    // -------------------- PRIVATE --------------------

    private static DatasetField copyDatasetField(DatasetField originalDsf, DatasetField parentDsf){
        DatasetField dsf = new DatasetField();
        dsf.setDatasetFieldType(originalDsf.getDatasetFieldType());
        dsf.setControlledVocabularyValues(originalDsf.getControlledVocabularyValues());
        dsf.setFieldValue(originalDsf.getFieldValue().getOrNull());
        dsf.setDatasetFieldParent(parentDsf);

        for (DatasetField dsfChildren : originalDsf.getDatasetFieldsChildren()) {
            dsf.getDatasetFieldsChildren().add(copyDatasetField(dsfChildren, dsf));
        }

        return dsf;
    }
}
