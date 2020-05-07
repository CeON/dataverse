package edu.harvard.iq.dataverse.search.advanced;

import edu.harvard.iq.dataverse.common.BundleUtil;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.time.DateUtils;

import javax.faces.application.FacesMessage;
import javax.faces.component.UIComponent;
import javax.faces.component.UIInput;
import javax.faces.context.FacesContext;
import javax.faces.validator.FacesValidator;
import javax.faces.validator.Validator;
import javax.faces.validator.ValidatorException;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@FacesValidator(value = "dateRangeValidator")
public class DateRangeValidator implements Validator {

    private static final String[] DATE_FORMATS = {"yyyy", "-yyyy", "yyyy-MM", "-yyyy-MM", "yyyy-MM-dd", "-yyyy-MM-dd"};
    private static final String DATE_PATTERN = "^(\\-?)[0-9]{4}((\\-)([0-9]{2})){0,2}$";

    // -------------------- LOGIC --------------------
    @Override
    public void validate(FacesContext context, UIComponent component, Object value) throws ValidatorException {
        UIInput dateFromInput = (UIInput) component.getAttributes().get("dateFrom");
        UIInput dateToInput = (UIInput) component.getAttributes().get("dateTo");

        Object dateFromValue = dateFromInput.getSubmittedValue();
        Object dateToValue = dateToInput.getSubmittedValue();

        LocalDate dateFrom = validateDateField(context, dateFromInput, dateFromValue);
        LocalDate dateTo = validateDateField(context, dateToInput, dateToValue);

        boolean isFromAfterTo = false;
        if(dateFrom != null && dateTo != null) {
            isFromAfterTo = dateFrom.isAfter(movePartialDateToUpperLimit(dateTo, dateToValue.toString()));
        }

        if(isFromAfterTo) {
            dateFromInput.setValid(false);
            dateToInput.setValid(false);
            FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR, "", BundleUtil.getStringFromBundle("advanced.search.wrong.daterange.badRange"));
            context.addMessage(dateFromInput.getClientId(context), message);
            context.addMessage(dateToInput.getClientId(context), message);
        }
    }

    // -------------------- PRIVATE ---------------------
    private LocalDate validateDateField(FacesContext context, UIComponent comp, Object value) {
        if(value == null || value.toString().isEmpty()) {
            return null;
        }

        if(!value.toString().matches(DATE_PATTERN)) {
            ((UIInput) comp).setValid(false);
            FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR, "", BundleUtil.getStringFromBundle("advanced.search.wrong.daterange.format"));
            context.addMessage(comp.getClientId(context), message);
            return null;
        }

        try {
            DateUtils.parseDateStrictly(value.toString(), DATE_FORMATS);
        } catch(ParseException pe) {
            ((UIInput) comp).setValid(false);
            FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR, "", BundleUtil.getStringFromBundle("advanced.search.wrong.daterange.format"));
            context.addMessage(comp.getClientId(context), message);
        }


        return LocalDate.parse(getFullDateLiteral(value.toString()), DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private LocalDate movePartialDateToUpperLimit(LocalDate date, String partialDateString) {
        if(date == null) {
            return null;
        }
        int formatMode = StringUtils.countMatches(partialDateString.substring(1), "-"); // we can skip counting first '-' indicating BC date

        if(formatMode == 1) { // partial date YYYY-MM
            return moveToLastDayOfMonth(date);
        } else if(formatMode == 0) { // partial date YYYY
            return moveToLastDayOfMonth(moveToLastMonthOfYear(date));
        }

        return date; // full date YYYY-MM-DD
    }

    private LocalDate moveToLastMonthOfYear(LocalDate date) {
        return date.plus(1, ChronoUnit.YEARS).minus(1, ChronoUnit.MONTHS);
    }

    private LocalDate moveToLastDayOfMonth(LocalDate date) {
        return date.plus(1, ChronoUnit.MONTHS).minus(1, ChronoUnit.DAYS);
    }

    private String getFullDateLiteral(String partialStringLiteral) {
        int formatMode = StringUtils.countMatches(partialStringLiteral.substring(1), "-");

        for(int i=formatMode; i<2; i++) {
            partialStringLiteral += "-01";
        }
        return partialStringLiteral;
    }
}
