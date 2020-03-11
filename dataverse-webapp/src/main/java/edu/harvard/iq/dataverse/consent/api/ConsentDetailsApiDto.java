package edu.harvard.iq.dataverse.consent.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.vavr.control.Option;

import java.util.Locale;

public class ConsentDetailsApiDto {

    private Long id;
    private Locale language;
    private String text;

    // -------------------- CONSTRUCTORS --------------------

    public ConsentDetailsApiDto(@JsonProperty(value = "id") Long id,
                                @JsonProperty(value = "language", required = true) Locale language,
                                @JsonProperty(value = "text", required = true) String text) {
        this.id = id;
        this.language = language;
        this.text = text;
    }

    // -------------------- GETTERS --------------------

    public Option<Long> getId() {
        return Option.of(id);
    }

    public Locale getLanguage() {
        return language;
    }

    public String getText() {
        return text;
    }

}
