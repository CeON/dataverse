package edu.harvard.iq.dataverse.validation.datasetfield.validators;

import edu.harvard.iq.dataverse.common.BundleUtil;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetField;
import edu.harvard.iq.dataverse.validation.datasetfield.ValidationResult;
import org.omnifaces.cdi.Eager;

import javax.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Map;

@Eager
@ApplicationScoped
public class StandardIntegerValidator extends StandardFieldValidatorBase {

    @Override
    public String getName() {
        return "standard_integer";
    }

    @Override
    public ValidationResult validate(DatasetField field, Map<String, String> params, Map<String, List<DatasetField>> fieldIndex) {
        try {
            Integer.parseInt(field.getValue());
            return ValidationResult.ok();
        } catch (NumberFormatException nfe) {
            return ValidationResult.invalid(field, BundleUtil.getStringFromBundle("isNotValidInteger",
                    field.getDatasetFieldType().getDisplayName()));
        }
    }
}
