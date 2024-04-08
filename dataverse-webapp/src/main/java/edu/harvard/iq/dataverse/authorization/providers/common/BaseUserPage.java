package edu.harvard.iq.dataverse.authorization.providers.common;

import edu.harvard.iq.dataverse.common.BundleUtil;
import edu.harvard.iq.dataverse.dataset.metadata.inputRenderer.Suggestion;
import edu.harvard.iq.dataverse.dataset.metadata.inputRenderer.suggestion.SuggestionHandler;
import edu.harvard.iq.dataverse.validation.OrcIdValidator;
import edu.harvard.iq.dataverse.validation.RorValidator;
import edu.harvard.iq.dataverse.validation.field.ValidationResult;

import javax.ejb.EJB;
import javax.faces.application.FacesMessage;
import javax.faces.component.UIComponent;
import javax.faces.component.UIInput;
import javax.faces.context.FacesContext;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * Provides common functionality for the user account details page.
 */
public abstract class BaseUserPage  implements Serializable  {

    @EJB
    RorValidator rorValidator;

    @EJB
    OrcIdValidator orcIdValidator;

    @EJB(beanName = "RorSuggestionHandler")
    SuggestionHandler suggestionHandler;

    public void validateOrcId(FacesContext context, UIComponent toValidate, Object value) {
        String orcid = (String) value;
        if (org.apache.commons.lang.StringUtils.isEmpty(orcid)) {
            return;
        }
        ValidationResult result = orcIdValidator.validate(orcid);
        if (!result.isOk()) {
            ((UIInput) toValidate).setValid(false);
            context.addMessage(toValidate.getClientId(context), new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    BundleUtil.getStringFromBundle("user." + result.getErrorCode()), null));
        }
    }

    public void validateAffiliationRor(FacesContext context, UIComponent toValidate, Object value) {
        String ror = (String) value;
        if (org.apache.commons.lang.StringUtils.isEmpty(ror)) {
            return;
        }

        ValidationResult result = rorValidator.validate(ror);
        if (!result.isOk()) {
            ((UIInput) toValidate).setValid(false);
            context.addMessage(toValidate.getClientId(context), new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    BundleUtil.getStringFromBundle("user.affiliationror." + result.getErrorCode()), null));
        }
    }

    public List<Suggestion> processAffiliationRorSuggestions(String query) {
        return suggestionHandler.generateSuggestions(Collections.emptyMap(), query);
    }
}
