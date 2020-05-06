package edu.harvard.iq.dataverse.importers.ui;

import edu.harvard.iq.dataverse.persistence.dataset.DatasetFieldType;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetFieldsByType;
import edu.harvard.iq.dataverse.persistence.dataset.MetadataBlock;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class MetadataFormLookup {
    private String metadataBlockName;
    private Supplier<Map<MetadataBlock, List<DatasetFieldsByType>>> metadataSupplier;

    private Map<String, DatasetFieldsByType> lookup;
    private Map<String, DatasetFieldType> childrenLookup;

    // -------------------- CONSTRUCTORS --------------------

    MetadataFormLookup(String metadataBlockName,
                       Supplier<Map<MetadataBlock, List<DatasetFieldsByType>>> metadataSupplier) {
        this.metadataBlockName = metadataBlockName;
        this.metadataSupplier = metadataSupplier;
    }

    // -------------------- GETTERS --------------------

    public Map<String, DatasetFieldsByType> getLookup() {
        return lookup;
    }

    public Map<String, DatasetFieldType> getChildrenLookup() {
        return childrenLookup;
    }

    // -------------------- LOGIC --------------------

    public static MetadataFormLookup create(String metadataBlockName,
                                            Supplier<Map<MetadataBlock, List<DatasetFieldsByType>>> metadataSupplier) {
        MetadataFormLookup instance = new MetadataFormLookup(metadataBlockName, metadataSupplier);
        instance.lookup = instance.create();
        instance.childrenLookup = instance.createChildrenLookup(instance.lookup);
        return instance;
    }

    // -------------------- PRIVATE --------------------

    private Map<String, DatasetFieldsByType> create() {
        Map<MetadataBlock, List<DatasetFieldsByType>> metadata = metadataSupplier.get();
        List<DatasetFieldsByType> fieldsForBlock = metadata.entrySet().stream()
                .filter(e -> metadataBlockName.equals(e.getKey().getName()))
                .findFirst()
                .map(Map.Entry::getValue)
                .orElse(Collections.emptyList());

        return fieldsForBlock.stream()
                .collect(Collectors.toMap(f -> f.getDatasetFieldType().getName(), Function.identity()));
    }

    private Map<String, DatasetFieldType> createChildrenLookup(Map<String, DatasetFieldsByType> parentLookup) {
        return parentLookup.values().stream()
                .flatMap(f -> f.getDatasetFieldType().getChildDatasetFieldTypes().stream())
                .collect(Collectors.toMap(DatasetFieldType::getName, Function.identity()));
    }
}
