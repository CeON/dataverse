package edu.harvard.iq.dataverse;

import io.vavr.control.Try;
import org.apache.commons.lang.StringUtils;

import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.convert.Converter;
import javax.faces.convert.FacesConverter;

@FacesConverter(value = "strToLongConverter")
public class StringToLongParamConverter implements Converter {
    private Long longVal = null;

    @Override
    public Object getAsObject(FacesContext context, UIComponent component, String value) {
        Try.of(() -> Long.parseLong(value))
                .onFailure(throwable -> longVal = null)
                .onSuccess(pid -> longVal = pid);
        return longVal;
    }

    @Override
    public String getAsString(FacesContext context, UIComponent component, Object value) {
        return value == null ? StringUtils.EMPTY : String.valueOf(value);
    }
}
