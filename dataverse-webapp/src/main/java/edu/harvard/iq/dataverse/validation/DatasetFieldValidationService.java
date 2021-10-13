package edu.harvard.iq.dataverse.validation;

import edu.harvard.iq.dataverse.persistence.dataset.DatasetField;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.validation.datasetfield.FieldValidatorRegistry;
import edu.harvard.iq.dataverse.validation.datasetfield.FieldValidationDispatcher;
import edu.harvard.iq.dataverse.validation.datasetfield.ValidationResult;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.List;

@Stateless
public class DatasetFieldValidationService {

    private FieldValidatorRegistry registry;

    // -------------------- CONSTRUCTORS --------------------

    public DatasetFieldValidationService() { }

    @Inject
    public DatasetFieldValidationService(FieldValidatorRegistry registry) {
        this.registry = registry;
    }

    // -------------------- LOGIC --------------------

    public List<ValidationResult> validateFieldsOfDatasetVersion(DatasetVersion datasetVersion) {
        datasetVersion.getFlatDatasetFields()
                .forEach(f -> f.setValidationMessage(null));
        List<ValidationResult> validationResults = new FieldValidationDispatcher(registry)
                .init(datasetVersion)
                .executeValidations();
        validationResults.forEach(r -> {
                    DatasetField field = r.getField();
                    field.setValidationMessage(r.getMessage());
                });
        return validationResults;
    }

    public ValidatorsWithContext createValidatorsWithContext(DatasetVersion datasetVersion) {
        return new ValidatorsWithContext(registry)
                .init(datasetVersion);
    }

    // -------------------- INNER CLASSES --------------------

    public static class ValidatorsWithContext {
        private FieldValidationDispatcher dispatcher;

        // -------------------- CONSTRUCTORS --------------------

        private ValidatorsWithContext(FieldValidatorRegistry registry) {
            this.dispatcher = new FieldValidationDispatcher(registry);
        }

        // -------------------- LOGIC --------------------

        public ValidationResult validateField(DatasetField field) {
            field.setValidationMessage(null);
            ValidationResult result = dispatcher.validateField(field);
            if (!result.isOk()) {
                field.setValidationMessage(result.getMessage());
            }
            return result;
        }

        ValidatorsWithContext init(DatasetVersion datasetVersion) {
            dispatcher.init(datasetVersion);
            return this;
        }
    }
}
