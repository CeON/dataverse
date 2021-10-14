package edu.harvard.iq.dataverse.validation.datasetfield;

import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetField;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetFieldType;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.dataset.FieldType;
import edu.harvard.iq.dataverse.persistence.dataset.Template;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.validation.datasetfield.validators.StandardInputValidator;
import edu.harvard.iq.dataverse.validation.datasetfield.validators.StandardIntegerValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class FieldValidationDispatcherTest {

    FieldValidatorRegistry registry = new FieldValidatorRegistry();

    DatasetField datasetField;
    DatasetFieldType datasetFieldType;

    FieldValidationDispatcher dispatcher = new FieldValidationDispatcher(registry);

    @BeforeEach
    void setUp() {
        registry.register(new StandardIntegerValidator());
        registry.register(new StandardInputValidator());

        datasetField = new DatasetField();
        DatasetVersion datasetVersion = new DatasetVersion();
        Dataset dataset = new Dataset();
        Dataverse dataverse = new Dataverse();
        dataset.setOwner(dataverse);
        datasetVersion.setDataset(dataset);
        datasetFieldType = new DatasetFieldType() {
            @Override
            public String getDisplayName() { return "testField";
            }
        };
        datasetFieldType.setName("testField");
        datasetFieldType.setFieldType(FieldType.TEXT);
        datasetField.setDatasetFieldType(datasetFieldType);
        datasetField.setDatasetVersion(datasetVersion);
    }

    @Test
    void executeValidations() {
        // given
        datasetFieldType.setValidation("[{\"name\":\"standard_int\"}]");
        datasetField.setValue("44.5");

        // when
        List<ValidationResult> results = dispatcher.init(Collections.singletonList(datasetField)).executeValidations();

        // then
        assertThat(results).extracting(ValidationResult::isOk).containsExactly(false);
    }

    @Test
    @DisplayName("In case of no errors executeValidations(…) should return empty list")
    void executeValidations__ok() {
        // given
        datasetFieldType.setValidation("[{\"name\":\"standard_int\"}]");
        datasetField.setValue("1024");

        // when
        List<ValidationResult> results = dispatcher.init(Collections.singletonList(datasetField)).executeValidations();

        // then
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Execute chain of validators (last should fail)")
    void executeValidations__chain() {
        // given
        datasetFieldType.setValidation("[{\"name\":\"standard_int\"}," +
                "{\"name\":\"standard_input\",\"parameters\":[\"format:[0-9]\"]}]");
        datasetField.setValue("44");
        // when
        List<ValidationResult> results = dispatcher.init(Collections.singletonList(datasetField)).executeValidations();

        // then
        assertThat(results)
                .extracting(ValidationResult::getMessage, ValidationResult::isOk)
                .containsExactly(tuple("testField is not a valid entry.", false));
    }

    @Test
    void validateSingleField() {
        // given
        datasetFieldType.setValidation("[{\"name\":\"standard_int\"}]");
        datasetField.setValue("44 1/2");

        // when
        ValidationResult result = dispatcher.init(Collections.singletonList(datasetField)).validateField(datasetField);

        // then
        assertThat(result.isOk()).isFalse();
    }

    @Test
    @DisplayName("When field is valid validateSingleField(…) should return result with isOk() returning true")
    void validateSingleField__ok() {
        // given
        datasetFieldType.setValidation("[{\"name\":\"standard_input\",\"parameters\":[\"format:[0-9]\"]}]");
        datasetField.setValue("7");

        // when
        ValidationResult result = dispatcher.init(Collections.singletonList(datasetField)).validateField(datasetField);

        // then
        assertThat(result.isOk()).isTrue();
    }

    @Test
    @DisplayName("Fields with template should bypass validation")
    void templatesBypassValidation() {
        // given
        datasetFieldType.setValidation("[{\"name\":\"standard_int\"}]");
        datasetField.setValue("abcd");
        datasetField.setTemplate(new Template());

        // when
        ValidationResult result = dispatcher.init(Collections.singletonList(datasetField)).validateField(datasetField);
        List<ValidationResult> results = dispatcher.init(Collections.singletonList(datasetField)).executeValidations();

        // then
        assertThat(result.isOk()).isTrue();
        assertThat(results).isEmpty();
    }
}