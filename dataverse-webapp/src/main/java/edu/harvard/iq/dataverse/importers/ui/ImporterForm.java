package edu.harvard.iq.dataverse.importers.ui;

import edu.harvard.iq.dataverse.importer.metadata.ImporterData;
import edu.harvard.iq.dataverse.importer.metadata.ImporterFieldType;
import edu.harvard.iq.dataverse.importer.metadata.ImporterInput;
import edu.harvard.iq.dataverse.importer.metadata.MetadataImporter;
import edu.harvard.iq.dataverse.importer.metadata.SafeBundleWrapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class ImporterForm {
    private SafeBundleWrapper bundle;

    private List<FormItem> items = new ArrayList<>();

    // -------------------- GETTERS --------------------

    public List<FormItem> getItems() {
        return items;
    }

    // -------------------- LOGIC --------------------

    public static ImporterForm createInitializedForm(MetadataImporter importer, Locale locale) {
        ImporterForm instance = new ImporterForm();
        instance.initializeForm(importer, locale);
        return instance;
    }

    public void initializeForm(MetadataImporter importer, Locale locale) {
        SafeBundleWrapper bundle = new SafeBundleWrapper(importer, locale);
        int counter = 1;
        for (ImporterData.ImporterField field : getImporterFields(importer)) {
            String viewId = String.join("_", String.valueOf(Math.abs(field.fieldKey.hashCode() % 512)),
                    field.fieldKey.getName(), String.valueOf(counter));
            this.items.add(new FormItem(viewId, field, bundle));
            counter++;
        }
    }

    public ImporterInput toImporterInput() {
        return null;
    }

    // -------------------- PRIVATE --------------------

    private List<ImporterData.ImporterField> getImporterFields(MetadataImporter importer) {
        return Optional.ofNullable(importer)
                .map(MetadataImporter::getImporterData)
                .map(ImporterData::getImporterFormSchema)
                .orElseGet(Collections::emptyList);
    }

    // -------------------- INNER CLASSES --------------------

    public static class FormItem {
        private final String viewId;
        private final ImporterData.ImporterField importerField;
        private final SafeBundleWrapper bundle;

        private Object value;

        // -------------------- CONSTRUCTORS --------------------

        public FormItem(String viewId, ImporterData.ImporterField importerField, SafeBundleWrapper bundle) {
            this.viewId = viewId;
            this.importerField = importerField;
            this.bundle = bundle;
        }

        // -------------------- GETTERS --------------------

        public String getViewId() {
            return viewId;
        }

        public ImporterFieldType getType() {
            return importerField.fieldType;
        }

        public String getLabel() {
            return bundle.getString(importerField.labelKey);
        }

        public String getDescription() {
            return bundle.getString(importerField.descriptionKey);
        }

        public Boolean getRequired() {
            return importerField.required;
        }

        public Object getValue() {
            return value;
        }

        // -------------------- SETTERS --------------------

        public void setValue(Object value) {
            this.value = value;
        }
    }
}
