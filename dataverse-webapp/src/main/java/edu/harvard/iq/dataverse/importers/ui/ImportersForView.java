package edu.harvard.iq.dataverse.importers.ui;

import edu.harvard.iq.dataverse.importer.metadata.ImporterConstants;
import edu.harvard.iq.dataverse.importer.metadata.MetadataImporter;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.MetadataBlock;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Collectors;

public class ImportersForView implements MapForView<MetadataImporter, ImportersForView.ImporterItem> {
    private Map<MetadataImporter, ImporterItem> items = new HashMap<>();

    // -------------------- GETTERS --------------------

    public List<MetadataImporter> getImportersView() {
        return new ArrayList<>(items.keySet()); // TODO: Implement some kind of sorting later
    }

    @Override
    public Map<MetadataImporter, ImporterItem> getUnderlyingMap() {
        return items;
    }

    // -------------------- CONSTRUCTORS --------------------

    public ImportersForView(Dataset dataset, Map<String, MetadataImporter> importers, Locale locale) {

        if (dataset == null || locale == null) {
            return;
        }

        Dataverse owner = dataset.getOwner();
        Set<String> metadataBlockNames = owner.getMetadataBlocks().stream()
                .map(MetadataBlock::getName)
                .collect(Collectors.toSet());

        this.items = importers.entrySet().stream()
                .filter(e -> metadataBlockNames.contains(e.getValue().getMetadataBlockName()))
                .collect(Collectors.toMap(Map.Entry::getValue, e -> new ImporterItem(e, locale)));
    }

    // -------------------- INNER CLASSES --------------------

    public static class ImporterItem {
        private String name;
        private String description;

        private static final String UNKNOWN = "?";

        // -------------------- CONSTRUCTORS --------------------

        public ImporterItem(Map.Entry<String, MetadataImporter> importer, Locale locale) {
            try {
                ResourceBundle bundle = importer.getValue().getBundle(locale);
                this.name = bundle.getString(ImporterConstants.IMPORTER_NAME);
                this.description = bundle.getString(ImporterConstants.IMPORTER_DESCRIPTION);
            } catch (MissingResourceException | NullPointerException e) {
                this.name = ImporterConstants.UNKNOWN_BUNDLE_VALUE;
                this.description = ImporterConstants.UNKNOWN_BUNDLE_VALUE;
            }
        }

        // -------------------- GETTERS --------------------

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }
    }
}
