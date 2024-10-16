package edu.harvard.iq.dataverse.validation;

import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.dataset.ValidatableField;
import edu.harvard.iq.dataverse.validation.field.DatasetFieldValidationDispatcherFactory;
import edu.harvard.iq.dataverse.validation.field.FieldValidationResult;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.List;

@Stateless
public class DatasetFieldValidationService {

    private DatasetFieldValidationDispatcherFactory dispatcherFactory;

    // -------------------- CONSTRUCTORS --------------------

    public DatasetFieldValidationService() { }

    @Inject
    public DatasetFieldValidationService(DatasetFieldValidationDispatcherFactory dispatcherFactory) {
        this.dispatcherFactory = dispatcherFactory;
    }

// -------------------- LOGIC --------------------

    public List<FieldValidationResult> validateFieldsOfDatasetVersion(DatasetVersion datasetVersion) {
        datasetVersion.getFlatDatasetFields()
                .forEach(f -> f.setValidationMessage(null));
        List<FieldValidationResult> fieldValidationResults = dispatcherFactory.create(datasetVersion.getFlatDatasetFields())
                .executeValidations();
        fieldValidationResults.forEach(r -> {
                    ValidatableField field = r.getField();
                    field.setValidationMessage(r.getMessage());
                });
        return fieldValidationResults;
    }
}
