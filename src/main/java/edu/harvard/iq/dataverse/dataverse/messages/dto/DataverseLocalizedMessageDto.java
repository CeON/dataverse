package edu.harvard.iq.dataverse.dataverse.messages.dto;

public class DataverseLocalizedMessageDto {

    public DataverseLocalizedMessageDto(String locale, String message, String language) {
        this.locale = locale;
        this.message = message;
        this.language = language;
    }

    private String locale;

    private String message;

    private String language;

    public String getLocale() {
        return locale;
    }

    public String getMessage() {
        return message;
    }

    public String getLanguage() {
        return language;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setLanguage(String language) {
        this.language = language;
    }
}
