package edu.harvard.iq.dataverse.importers.ui;

import edu.harvard.iq.dataverse.importer.metadata.ImporterConstants;
import edu.harvard.iq.dataverse.importer.metadata.ImporterData;
import edu.harvard.iq.dataverse.importer.metadata.ImporterFieldKey;
import edu.harvard.iq.dataverse.importer.metadata.ImporterFieldType;
import edu.harvard.iq.dataverse.importer.metadata.MetadataImporter;
import edu.harvard.iq.dataverse.importer.metadata.ResultField;
import edu.harvard.iq.dataverse.importer.metadata.SafeBundleWrapper;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetFieldsByType;
import edu.harvard.iq.dataverse.persistence.dataset.MetadataBlock;
import edu.harvard.iq.dataverse.util.FileUtil;
import org.primefaces.component.fileupload.FileUpload;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.file.UploadedFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public class ImporterForm {
    private static final ProcessingType[] SINGLE_OPTIONS
            = new ProcessingType[] { ProcessingType.FILL_IF_EMPTY, ProcessingType.OVERWRITE };
    private static final ProcessingType[] MULTIPLE_OPTIONS
            = new ProcessingType[] { ProcessingType.MULTIPLE_CREATE_NEW, ProcessingType.MULTIPLE_OVERWRITE};

    public enum ImportStep {
        FIRST, SECOND;
    }
    private List<FormItem> items = new ArrayList<>();
    private List<ResultItem> resultItems = new ArrayList<>();
    private ImportStep step;

    private MetadataImporter importer;
    private MetadataFormLookup lookup;

    // -------------------- CONSTRUCTORS --------------------

    public ImporterForm() {
        this.step = ImportStep.FIRST;
    }


    // -------------------- GETTERS --------------------

    public List<FormItem> getItems() {
        return items;
    }

    public List<ResultItem> getResultItems() {
        return resultItems;
    }

    public ImportStep getStep() {
        return step;
    }

    public ProcessingType[] getItemProcessingOptions(ResultItem item) {
        if (ItemType.VOCABULARY.equals(item.getItemType())) {
            return SINGLE_OPTIONS;
        } else {
            return item.getMultipleAllowed() ? MULTIPLE_OPTIONS : SINGLE_OPTIONS;
        }
     }

    // -------------------- LOGIC --------------------

    public static ImporterForm createInitializedForm(MetadataImporter importer, Locale locale,
                                                     Supplier<Map<MetadataBlock, List<DatasetFieldsByType>>> metadataSupplier) {
        ImporterForm instance = new ImporterForm();
        instance.initializeForm(importer, locale,
                MetadataFormLookup.create(importer.getMetadataBlockName(), metadataSupplier));
        return instance;
    }

    public void initializeForm(MetadataImporter importer, Locale locale, MetadataFormLookup lookup) {
        this.lookup = lookup;
        this.importer = importer;

        SafeBundleWrapper bundle = new SafeBundleWrapper(importer, locale);
        int counter = 1;
        for (ImporterData.ImporterField field : getImporterFields(importer)) {
            String viewId = String.join("_", String.valueOf(Math.abs(field.fieldKey.hashCode() % 512)),
                    field.fieldKey.getName(), String.valueOf(counter));
            items.add(new FormItem(viewId, field, bundle));
            counter++;
        }
    }

    public void handleFileUpload(FileUploadEvent event) throws IOException {
        FileUpload component = (FileUpload) event.getComponent();
        removePreviousTempFile(component);

        UploadedFile file = Optional.ofNullable(event)
                .map(FileUploadEvent::getFile)
                .orElseThrow(() -> new IllegalStateException("Null event or file"));
        Path tempPath = prepareTempPath(file);
        Files.copy(file.getInputStream(), tempPath, StandardCopyOption.REPLACE_EXISTING);
        component.setValue(tempPath.toFile());
    }

    public Map<ImporterFieldKey, Object> toImporterInput() {
        return items.stream()
                .filter(i -> !ImporterFieldKey.IRRELEVANT.equals(i.importerField.fieldKey))
                .collect(HashMap::new, (m, i) -> m.put(i.importerField.fieldKey, i.getValue()), HashMap::putAll);
    }

    public void nextStep() {
//        if (new Random().nextInt() % 4 == 0) {
//            step = ImporterStep.SECOND;
//        } else {
//            List<FormItem> formItems = items.stream()
//                    .filter(i -> !ImporterFieldKey.IRRELEVANT.equals(i.importerField.fieldKey))
//                    .collect(Collectors.toList());
//            FacesContext fctx = FacesContext.getCurrentInstance();
//            for (FormItem item : formItems) {
//                String viewId = item.getViewId();
//                UIComponent component = JsfHelper.findComponent(fctx.getViewRoot(), viewId, String::endsWith);
//                String clientId = component.getClientId();
//                fctx.addMessage(clientId, new FacesMessage(FacesMessage.SEVERITY_ERROR, "ZENON", "zenon"));
//                Map<String, Object> attributes = component.getAttributes();
//                attributes.isEmpty();
//            }
//            fctx.validationFailed();
//        }

        // VALIDATION ut supra

        List<ResultField> resultFields = importer.fetchMetadata(toImporterInput());
        resultItems = new ResultItemsCreator(lookup).createItemsForView(resultFields);
        step = ImportStep.SECOND;
    }

    public void onExit(Map<MetadataBlock, List<DatasetFieldsByType>> metadata) {
        new MetadataFormFiller(lookup).fillForm(resultItems);
    }



    // -------------------- PRIVATE --------------------

    private List<ImporterData.ImporterField> getImporterFields(MetadataImporter importer) {
        return Optional.ofNullable(importer)
                .map(MetadataImporter::getImporterData)
                .map(ImporterData::getImporterFormSchema)
                .orElseGet(Collections::emptyList);
    }

    private Path prepareTempPath(UploadedFile file) throws IOException {
        String tempDirectory = Optional.ofNullable(FileUtil.getFilesTempDirectory())
                .orElseThrow(() -> new IllegalStateException("Cannot obtain temp directory path"));
        return Files.createTempFile(Paths.get(tempDirectory), "import",
                ImporterConstants.FILE_NAME_SEPARATOR + file.getFileName());
    }

    private void removePreviousTempFile(FileUpload component) {
        if (component.getValue() == null) {
            return;
        }
        File tempFile = (File) component.getValue();
        tempFile.delete();
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
