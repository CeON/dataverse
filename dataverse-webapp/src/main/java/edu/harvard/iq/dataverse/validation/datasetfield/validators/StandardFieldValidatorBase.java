package edu.harvard.iq.dataverse.validation.datasetfield.validators;

import edu.harvard.iq.dataverse.common.BundleUtil;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetField;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetFieldType;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.dataverse.DataverseFieldTypeInputLevel;
import edu.harvard.iq.dataverse.validation.datasetfield.ValidationResult;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;

public abstract class StandardFieldValidatorBase extends FieldValidatorBase {

    @Override
    public ValidationResult isValid(DatasetField field, Map<String, String> params, Map<String, List<DatasetField>> fieldIndex) {
        DatasetFieldType dsfType = field.getDatasetFieldType();
        if (StringUtils.isBlank(field.getValue()) && dsfType.isPrimitive() && isRequiredInDataverse(field)) {
                return ValidationResult.invalid(field,
                        BundleUtil.getStringFromBundle("isrequired", dsfType.getDisplayName()));
        }

        if (StringUtils.isBlank(field.getValue()) || StringUtils.equals(field.getValue(), DatasetField.NA_VALUE)) {
            return ValidationResult.ok();
        }
        return validate(field, params, fieldIndex);
    }

    public abstract ValidationResult validate(DatasetField field, Map<String, String> params, Map<String, List<DatasetField>> fieldIndex);

    // -------------------- PRIVATE --------------------

    private boolean isRequiredInDataverse(DatasetField field) {

        DatasetFieldType fieldType = field.getDatasetFieldType();
        if (fieldType.isRequired()) {
            return true;
        }

        Dataverse dataverse = getDataverse(field).getMetadataBlockRootDataverse();
        return dataverse.getDataverseFieldTypeInputLevels().stream()
                .filter(inputLevel -> inputLevel.getDatasetFieldType().equals(field.getDatasetFieldType()))
                .map(DataverseFieldTypeInputLevel::isRequired)
                .findFirst()
                .orElse(false);
    }

    private Dataverse getDataverse(DatasetField field) {
        return field.getTopParentDatasetField()
                .getDatasetVersion()
                .getDataset()
                .getOwner();
    }
}
