package edu.harvard.iq.dataverse.locale;

public enum Locale {

    ENGLISH("en"),
    POLISH("pl");

    Locale(String locale) {
        this.locale = locale;
    }

    private String locale;

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

}
