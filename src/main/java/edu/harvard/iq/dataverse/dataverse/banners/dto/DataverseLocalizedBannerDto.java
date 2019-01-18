package edu.harvard.iq.dataverse.dataverse.banners.dto;

import org.primefaces.model.StreamedContent;
import org.primefaces.model.UploadedFile;

import java.util.Arrays;
import java.util.Objects;

public class DataverseLocalizedBannerDto {

    public DataverseLocalizedBannerDto() {
    }

    public DataverseLocalizedBannerDto(String locale, String language) {
        this.locale = locale;
        this.language = language;
    }

    public DataverseLocalizedBannerDto(Long id, String locale, String language, byte[] image, String imageLink) {
        this.id = id;
        this.locale = locale;
        this.language = language;
        this.image = image;
        this.imageLink = imageLink;
    }

    private Long id;

    private String locale;

    private String language;

    private byte[] image;

    private String contentType;

    private StreamedContent miniDisplayImage;

    private String imageLink;

    private UploadedFile file;

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

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public StreamedContent getMiniDisplayImage() {
        return miniDisplayImage;
    }

    public void setMiniDisplayImage(StreamedContent miniDisplayImage) {
        this.miniDisplayImage = miniDisplayImage;
    }

    public String getImageLink() {
        return imageLink;
    }

    public void setImageLink(String imageLink) {
        this.imageLink = imageLink;
    }

    public UploadedFile getFile() {
        return file;
    }

    public void setFile(UploadedFile file) {
        this.file = file;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataverseLocalizedBannerDto that = (DataverseLocalizedBannerDto) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(locale, that.locale) &&
                Objects.equals(language, that.language) &&
                Arrays.equals(image, that.image) &&
                Objects.equals(contentType, that.contentType) &&
                Objects.equals(miniDisplayImage, that.miniDisplayImage) &&
                Objects.equals(imageLink, that.imageLink) &&
                Objects.equals(file, that.file);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(id, locale, language, contentType, miniDisplayImage, imageLink, file);
        result = 31 * result + Arrays.hashCode(image);
        return result;
    }
}
