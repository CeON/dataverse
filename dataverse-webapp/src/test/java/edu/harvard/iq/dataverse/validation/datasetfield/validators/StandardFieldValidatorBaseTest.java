package edu.harvard.iq.dataverse.validation.datasetfield.validators;

import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetField;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetFieldType;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.dataset.FieldType;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.validation.datasetfield.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StandardFieldValidatorBaseTest {

    DatasetField datasetField;
    DatasetFieldType datasetFieldType;

    StandardFieldValidatorBase validator = new StandardFieldValidatorBase() {
        @Override
        public String getName() {
            return "test_validator";
        }

        @Override
        public ValidationResult validate(DatasetField field, Map<String, String> params, Map<String, List<DatasetField>> fieldIndex) {
            return ValidationResult.ok();
        }
    };

    @BeforeEach
    void setUp() {
        datasetField = new DatasetField();
        DatasetVersion datasetVersion = new DatasetVersion();
        Dataset dataset = new Dataset();
        Dataverse dataverse = new Dataverse();
        dataset.setOwner(dataverse);
        datasetVersion.setDataset(dataset);
        datasetFieldType = new DatasetFieldType();
        datasetFieldType.setFieldType(FieldType.TEXT);
        datasetField.setDatasetFieldType(datasetFieldType);
        datasetField.setDatasetVersion(datasetVersion);
    }

    @Test
    void isValid__emptyRequired() {
        // given
        datasetFieldType.setRequired(true);

        // when
        ValidationResult result = validator.isValid(datasetField, Collections.emptyMap(), Collections.emptyMap());

        // then
        assertThat(result.isOk()).isFalse();
    }


    @Test
    void isValid__nonEmptyRequired() {
        // given
        datasetFieldType.setRequired(true);
        datasetField.setFieldValue("abc");

        // when
        ValidationResult result = validator.isValid(datasetField, Collections.emptyMap(), Collections.emptyMap());

        // then
        assertThat(result.isOk()).isTrue();
    }

    @Test
    void isValid__emptyNonRequired() {
        // given
        datasetFieldType.setRequired(false);

        // when
        ValidationResult result = validator.isValid(datasetField, Collections.emptyMap(), Collections.emptyMap());

        // then
        assertThat(result.isOk()).isTrue();
    }

    @Test
    @DisplayName("NA value should bypass any further validations")
    void isValid__NAValue() {
        // given
        datasetField.setFieldValue(DatasetField.NA_VALUE);

        // when
        ValidationResult result = new StandardFieldValidatorBase() {
            @Override
            public String getName() {
                return "failing_validator";
            }

            @Override
            public ValidationResult validate(DatasetField field, Map<String, String> params, Map<String, List<DatasetField>> fieldIndex) {
                return ValidationResult.invalid(field, "message");
            }
        }.isValid(datasetField, Collections.emptyMap(), Collections.emptyMap());

        // then
        assertThat(result.isOk()).isTrue();
    }
}