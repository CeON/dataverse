package edu.harvard.iq.dataverse.consent.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public class ConsentDetailsApiDto {

    private Long id;
    private Locale language;
    private String text;

    // -------------------- CONSTRUCTORS --------------------

    public ConsentDetailsApiDto(@JsonProperty(value = "id") Long id,
                                @JsonProperty(value = "language", required = true) Locale language,
                                @JsonProperty(value = "text", required = true) String text) {
        Objects.requireNonNull(language);
        Objects.requireNonNull(text);

        this.id = id;
        this.language = language;
        this.text = text;
    }

    // -------------------- GETTERS --------------------

    public Optional<Long> getId() {
        return Optional.ofNullable(id);
    }

    public Locale getLanguage() {
        return language;
    }

    public String getText() {
        return text;
    }

}
