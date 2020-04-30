package edu.harvard.iq.dataverse.importers.ui;

import edu.harvard.iq.dataverse.importer.metadata.ResultField;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetField;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetFieldType;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetFieldsByType;
import edu.harvard.iq.dataverse.persistence.dataset.MetadataBlock;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class MetadataFiller {
    private String metadataBlockName;

    private Supplier<Map<MetadataBlock, List<DatasetFieldsByType>>> metadataSupplier;

    private Map<String, DatasetFieldsByType> lookup;
    private Map<String, DatasetFieldType> childrenLookup;

    // -------------------- CONSTRUCTORS --------------------

    public MetadataFiller(String metadataBlockName, Supplier<Map<MetadataBlock, List<DatasetFieldsByType>>> metadataSupplier) {
        this.metadataBlockName = metadataBlockName;
        this.metadataSupplier = metadataSupplier;
    }

    // -------------------- LOGIC --------------------

    public List<ResultItem> createItemsForView(List<ResultField> importerResult) {
        Map<String, DatasetFieldsByType> lookup = getOrCreateLookup();
        List<ResultItem> items = new ArrayList<>();
        for (ResultField field : importerResult) {
            ResultItem item = new ResultItem(field);
            DatasetFieldsByType fieldData = lookup.get(field.getName());
            if (fieldData != null) {
                DatasetFieldType fieldType = fieldData.getDatasetFieldType();
                item.setProcessable(true)
                        .setLocalizedName(fieldType.getLocaleTitle())
                        .setMultipleAllowed(fieldType.isAllowMultiples())
                        .setDisplayOrder(fieldType.getDisplayOrder())
                        .setCompound(fieldType.isCompound());
                initializeChildren(item.getChildren());
            } else {
                item.setShouldProcess(false);
            }
            item.setProcessingType(item.getMultipleAllowed()
                    ? ProcessingType.MULTIPLE_CREATE_NEW : ProcessingType.FILL_IF_EMPTY);
            items.add(item);
        }
        Collections.sort(items);
        return items;
    }

    public void fillForm(List<ResultItem> importerFormData) {
        for (ResultItem item : importerFormData) {
            if (!item.getProcessable() || !item.getShouldProcess()) {
                continue;
            }
            switch (item.getProcessingType()) {
                case OVERWRITE:
                case MULTIPLE_OVERWRITE:
                    processItem(item, this::takeLastOrCreate, this::setItemValue);
                    break;
                case MULTIPLE_CREATE_NEW:
                    processItem(item, this::createEmptyField, this::setItemValue);
                    break;
                case FILL_IF_EMPTY:
                    processItem(item, this::takeLastOrCreate, this::setIfBlank);
                    break;
            }
        }
    }

    // -------------------- PRIVATE --------------------

    private Map<String, DatasetFieldsByType> getOrCreateLookup() {
        if (lookup == null) {
            lookup = createLookup();
        }
        return lookup;
    }
    private Map<String, DatasetFieldType> getOrCreateChildrenLookup() {
        if (childrenLookup == null) {
            childrenLookup = createChildrenLookup();
        }
        return childrenLookup;
    }

    private Map<String, DatasetFieldsByType> createLookup() {
        Map<MetadataBlock, List<DatasetFieldsByType>> metadata = metadataSupplier.get();
        List<DatasetFieldsByType> fieldsForBlock = metadata.entrySet().stream()
                .filter(e -> metadataBlockName.equals(e.getKey().getName()))
                .findFirst()
                .map(Map.Entry::getValue)
                .orElse(Collections.emptyList());

        return fieldsForBlock.stream()
                .collect(Collectors.toMap(f -> f.getDatasetFieldType().getName(), Function.identity()));
    }

    private Map<String, DatasetFieldType> createChildrenLookup() {
        return getOrCreateLookup().values().stream()
                .flatMap(f -> f.getDatasetFieldType().getChildDatasetFieldTypes().stream())
                .collect(Collectors.toMap(DatasetFieldType::getName, Function.identity())) ;
    }

    private void initializeChildren(List<ResultItem> children) {
        Map<String, DatasetFieldType> childrenLookup = getOrCreateChildrenLookup();
        for (ResultItem child : children) {
            DatasetFieldType fieldData = childrenLookup.get(child.getName());
            if (fieldData != null) {
                child.setProcessable(true)
                        .setLocalizedName(fieldData.getLocaleTitle())
                        .setDisplayOrder(fieldData.getDisplayOrder());
            } else {
                child.setShouldProcess(false);
            }
        }
        Collections.sort(children);
    }

    private void processItem(ResultItem item, Function<DatasetFieldsByType, DatasetField> fieldProvider,
                             BiConsumer<DatasetField, ResultItem> fieldSetter) {
        DatasetFieldsByType fieldsByType = getOrCreateLookup().get(item.getName());
        DatasetField field = fieldProvider.apply(fieldsByType);
        if (item.getCompound()) {
            for (ResultItem childItem : item.getChildren()) {
                if (!childItem.getProcessable() || !childItem.getShouldProcess()) {
                    continue;
                }
                DatasetField child = matchChild(childItem, field);
                fieldSetter.accept(child, childItem);
            }
        } else {
            fieldSetter.accept(field, item);
        }
    }

    private DatasetField matchChild(ResultItem childItem, DatasetField parent) {
        String name = childItem.getName();
        return parent.getDatasetFieldsChildren().stream()
                .filter(c -> name.equals(c.getDatasetFieldType().getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Child field [" + name + "] not found!"));
    }

    private DatasetField takeLastOrCreate(DatasetFieldsByType datasetFieldsByType) {
        List<DatasetField> fields = datasetFieldsByType.getDatasetFields();
        if (fields.isEmpty()) {
            datasetFieldsByType.addEmptyDatasetField(0);
            return fields.get(0);
        } else {
            return fields.get(fields.size() - 1);
        }
    }

    private DatasetField createEmptyField(DatasetFieldsByType datasetFieldsByType) {
        List<DatasetField> fields = datasetFieldsByType.getDatasetFields();
        datasetFieldsByType.addEmptyDatasetField(fields.size());
        return fields.get(fields.size() - 1);
    }

    private void setItemValue(DatasetField field, ResultItem item) {
        field.setValue(item.getValue());
    }

    private void setIfBlank(DatasetField field, ResultItem item) {
        String value = field.getValue();
        field.setValue(StringUtils.isBlank(value) ? item.getValue() : value);
    }
}
