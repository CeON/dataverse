package edu.harvard.iq.dataverse.validation.datasetfield.validators;

import edu.harvard.iq.dataverse.common.BundleUtil;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetField;
import edu.harvard.iq.dataverse.validation.datasetfield.ValidationResult;
import org.apache.commons.lang.StringUtils;
import org.omnifaces.cdi.Eager;

import javax.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Map;

@Eager
@ApplicationScoped
public class StandardTextValidator extends StandardFieldValidatorBase {

    @Override
    public String getName() {
        return "standard_text";
    }

    @Override
    public ValidationResult validate(DatasetField field, Map<String, String> params, Map<String, List<DatasetField>> fieldIndex) {
        String validationFormat = params.get("format");
        if (StringUtils.isNotBlank(validationFormat)) {
            return field.getValue().matches(field.getDatasetFieldType().getValidationFormat())
                    ? ValidationResult.ok()
                    : ValidationResult.invalid(field, BundleUtil.getStringFromBundle("isNotValidEntry",
                    field.getDatasetFieldType().getDisplayName()));
        }
        return ValidationResult.ok();
    }
}
