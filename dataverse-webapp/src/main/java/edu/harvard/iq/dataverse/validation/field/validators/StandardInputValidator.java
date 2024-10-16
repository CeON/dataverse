package edu.harvard.iq.dataverse.validation.field.validators;

import edu.harvard.iq.dataverse.common.BundleUtil;
import edu.harvard.iq.dataverse.persistence.dataset.ValidatableField;
import edu.harvard.iq.dataverse.validation.field.FieldValidationResult;
import org.apache.commons.lang.StringUtils;
import org.omnifaces.cdi.Eager;

import javax.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Map;

@Eager
@ApplicationScoped
public class StandardInputValidator extends MultiValueValidatorBase {

    @Override
    public String getName() {
        return "standard_input";
    }

    @Override
    public FieldValidationResult validateValue(String value, ValidatableField field, Map<String, Object> params,
                                               Map<String, ? extends List<? extends ValidatableField>> fieldIndex) {
        String validationFormat = (String) params.get("format");
        if (StringUtils.isNotBlank(validationFormat)) {
            return value.matches(validationFormat)
                    ? FieldValidationResult.ok()
                    : FieldValidationResult.invalid(field, BundleUtil.getStringFromBundle("isNotValidEntry",
                    field.getDatasetFieldType().getDisplayName()));
        }
        return FieldValidationResult.ok();
    }
}
