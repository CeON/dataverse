package edu.harvard.iq.dataverse.importer.metadata;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class ImporterData {

    private List<ImporterField> importerFormSchema = new ArrayList<>();

    // -------------------- GETTERS --------------------

    public List<ImporterField> getImporterFormSchema() {
        return importerFormSchema;
    }

    // -------------------- LOGIC --------------------

    public ImporterData addField(ImporterField field) {
        importerFormSchema.add(field);
        return this;
    }

    public ImporterData addDescription(String descriptionKey) {
        importerFormSchema.add(ImporterField.of(ImporterFieldKey.IRRELEVANT, ImporterFieldType.DESCRIPTION,
                false, StringUtils.EMPTY, descriptionKey));
        return this;
    }

    // -------------------- INNER CLASSES --------------------

    public static class ImporterField {
        public final ImporterFieldKey fieldKey;
        public final ImporterFieldType fieldType;
        public final boolean required;
        public final String labelKey;
        public final String descriptionKey;

        private ImporterField(ImporterFieldKey fieldKey, ImporterFieldType fieldType, boolean required,
                              String labelKey, String descriptionKey) {
            this.fieldKey = fieldKey;
            this.fieldType = fieldType;
            this.required = required;
            this.labelKey = labelKey;
            this.descriptionKey = descriptionKey;
        }

        public static ImporterField of(ImporterFieldKey fieldKey, ImporterFieldType fieldType, boolean required,
                                       String labelKey, String descriptionKey) {
            return new ImporterField(fieldKey, fieldType, required, labelKey, descriptionKey);
        }
    }
}
