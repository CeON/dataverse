package edu.harvard.iq.dataverse.license.dto;

import edu.harvard.iq.dataverse.license.License;
import org.primefaces.model.StreamedContent;

public class LicenseIconDto {

    private Long id;

    private StreamedContent content;

    private License license;

    public LicenseIconDto(Long id, StreamedContent content, License license) {
        this.id = id;
        this.content = content;
        this.license = license;
    }

    public LicenseIconDto(StreamedContent content) {
        this.content = content;
    }

    // -------------------- GETTERS --------------------

    public Long getId() {
        return id;
    }

    public StreamedContent getContent() {
        return content;
    }

    public License getLicense() {
        return license;
    }
}
