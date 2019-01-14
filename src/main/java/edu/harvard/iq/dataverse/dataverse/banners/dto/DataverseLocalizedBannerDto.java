package edu.harvard.iq.dataverse.dataverse.banners.dto;

import java.util.Arrays;
import java.util.Objects;

public class DataverseLocalizedBannerDto {

    public DataverseLocalizedBannerDto(String locale, String language) {
        this.locale = locale;
        this.language = language;
    }

    public DataverseLocalizedBannerDto(Long id, String locale, byte[] image, String language) {
        this.id = id;
        this.locale = locale;
        this.language = language;
        this.image = image;
    }

    private Long id;

    private String locale;

    private String language;

    private byte[] image;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public byte[] getImage() {
        return image;
    }

    public void setImage(byte[] image) {
        this.image = image;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataverseLocalizedBannerDto that = (DataverseLocalizedBannerDto) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(locale, that.locale) &&
                Objects.equals(language, that.language) &&
                Arrays.equals(image, that.image);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(id, locale, language);
        result = 31 * result + Arrays.hashCode(image);
        return result;
    }
}
