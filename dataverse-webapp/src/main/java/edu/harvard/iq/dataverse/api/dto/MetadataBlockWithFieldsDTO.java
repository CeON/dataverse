package edu.harvard.iq.dataverse.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import edu.harvard.iq.dataverse.persistence.dataset.ControlledVocabularyValue;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetField;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetFieldType;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetFieldUtil;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetFieldsByType;
import edu.harvard.iq.dataverse.persistence.dataset.FieldType;
import edu.harvard.iq.dataverse.persistence.dataset.MetadataBlock;
import io.vavr.control.Option;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class MetadataBlockWithFieldsDTO {
    private String displayName;

    private List<DatasetFieldDTO> fields;

    // -------------------- GETTERS --------------------

    public String getDisplayName() {
        return displayName;
    }

    public List<DatasetFieldDTO> getFields() {
        return fields;
    }

    // -------------------- LOGIC --------------------

    public void clearEmailFields() {
        getFields().removeIf(DatasetFieldDTO::isEmailType);
        Object TO_REMOVE = new Object();
        for (DatasetFieldDTO field : getFields()) {
            if (!"compound".equals(field.getTypeClass()) || field.getValue() == null) {
                continue;
            }
            Object value = field.getValue();
            if (Map.class.isAssignableFrom(value.getClass())) {
                Map<String, DatasetFieldDTO> restOfChildrenFields = clearEmailFields(value);
                if (restOfChildrenFields.isEmpty()) {
                    field.setValue(TO_REMOVE);
                }
                continue;
            }
            List<?> valueList = (List<?>) value;
            for (Object element : valueList) {
                if (!Map.class.isAssignableFrom(element.getClass())) {
                    break;
                } else {
                    clearEmailFields(element);
                }
            }
            if (valueList.isEmpty()) {
                field.setValue(TO_REMOVE);
            }
        }
        getFields().removeIf(f -> TO_REMOVE.equals(f.getValue()));
    }

    // -------------------- PRIVATE --------------------

    Map<String, DatasetFieldDTO> clearEmailFields(Object value) {
        Map<String, DatasetFieldDTO> childrenMap = (Map<String, DatasetFieldDTO>) value;
        childrenMap.values().removeIf(DatasetFieldDTO::isEmailType);
        return childrenMap;
    }

    // -------------------- SETTERS --------------------

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setFields(List<DatasetFieldDTO> fields) {
        this.fields = fields;
    }

    // -------------------- INNER CLASSES --------------------

    public static class Creator {

        // -------------------- LOGIC --------------------

        public MetadataBlockWithFieldsDTO create(MetadataBlock metadataBlock, List<DatasetField> datasetFields) {
            MetadataBlockWithFieldsDTO created = new MetadataBlockWithFieldsDTO();
            created.setDisplayName(metadataBlock.getDisplayName());
            created.setFields(createFields(datasetFields));
            return created;
        }

        // -------------------- PRIVATE --------------------

        private List<DatasetFieldDTO> createFields(List<DatasetField> datasetFields) {
            datasetFields.sort(Comparator.comparing(DatasetField::getDatasetFieldTypeDisplayOrder));
            datasetFields.forEach(f -> f.getDatasetFieldsChildren()
                    .sort(Comparator.comparing(DatasetField::getDatasetFieldTypeDisplayOrder)));
            List<DatasetFieldDTO> fields = new ArrayList<>();
            for (DatasetFieldsByType fieldsByType : DatasetFieldUtil.groupByType(datasetFields)) {
                DatasetFieldType fieldType = fieldsByType.getDatasetFieldType();
                DatasetFieldDTO field = createForType(fieldType);
                List<DatasetField> fieldsOfType = fieldsByType.getDatasetFields();
                List<?> values = Collections.emptyList();
                if (fieldType.isControlledVocabulary()) {
                    values = fieldsOfType.stream()
                            .flatMap(f -> f.getControlledVocabularyValues().stream())
                            .sorted(ControlledVocabularyValue.DisplayOrder)
                            .map(ControlledVocabularyValue::getStrValue)
                            .collect(Collectors.toList());
                } else if (fieldType.isPrimitive()) {
                    values = fieldsOfType.stream()
                            .map(DatasetField::getFieldValue)
                            .filter(Option::isDefined)
                            .map(Option::get)
                            .collect(Collectors.toList());
                } else if (fieldType.isCompound()) {
                    values = fieldsOfType.stream()
                            .map(this::extractChildren)
                            .collect(Collectors.toList());
                }
                field.setValue(extractValue(fieldType, values));
                fields.add(field);
            }
            return fields;
        }

        private DatasetFieldDTO createForType(DatasetFieldType fieldType) {
            DatasetFieldDTO field = new DatasetFieldDTO();
            field.setTypeName(fieldType.getName());
            field.setMultiple(fieldType.isAllowMultiples());
            field.setTypeClass(fieldType.isControlledVocabulary()
                    ? "controlledVocabulary"
                    : fieldType.isCompound()
                        ? "compound" : "primitive");
            field.setEmailType(fieldType.getFieldType() == FieldType.EMAIL);
            return field;
        }

        private Object extractChildren(DatasetField datasetField) {
            Map<String, DatasetFieldDTO> children = new LinkedHashMap<>();
            for(DatasetField child : datasetField.getDatasetFieldsChildren()) {
                DatasetFieldType fieldType = child.getDatasetFieldType();
                DatasetFieldDTO field = createForType(fieldType);
                if (fieldType.isControlledVocabulary()) {
                    List<String> values = child.getControlledVocabularyValues().stream()
                            .sorted(Comparator.comparing(ControlledVocabularyValue::getDisplayOrder))
                            .map(ControlledVocabularyValue::getStrValue)
                            .collect(Collectors.toList());
                    field.setValue(extractValue(fieldType, values));
                } else if (fieldType.isPrimitive()) {
                    field.setValue(child.getFieldValue().getOrElse((String) null));
                }
                children.put(field.getTypeName(), field);
            }
            return children;
        }

        private <T> Object extractValue(DatasetFieldType fieldType, List<T> values) {
            return fieldType.isAllowMultiples() || values.size() > 1
                    ? values : values.get(0);
        }
    }
}
