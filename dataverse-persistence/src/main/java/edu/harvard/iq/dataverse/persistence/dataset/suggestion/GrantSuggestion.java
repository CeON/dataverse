package edu.harvard.iq.dataverse.persistence.dataset.suggestion;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import java.util.Locale;

@Entity
public class GrantSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    public String grantAgency;

    private String grantAgencyAcronym;

    private String fundingProgram;

    private String suggestionName;

    private Locale suggestionNameLocale;

    // -------------------- CONSTRUCTORS --------------------

    @Deprecated
    public GrantSuggestion() {
    }

    public GrantSuggestion(String grantAgency, String grantAgencyAcronym, String fundingProgram, String suggestionName, Locale suggestionNameLocale) {
        this.grantAgency = grantAgency;
        this.grantAgencyAcronym = grantAgencyAcronym;
        this.fundingProgram = fundingProgram;
        this.suggestionName = suggestionName;
        this.suggestionNameLocale = suggestionNameLocale;
    }

    // -------------------- GETTERS --------------------

    public Long getId() {
        return id;
    }

    public String getGrantAgency() {
        return grantAgency;
    }

    public String getGrantAgencyAcronym() {
        return grantAgencyAcronym;
    }

    public String getFundingProgram() {
        return fundingProgram;
    }

    public String getSuggestionName() {
        return suggestionName;
    }

    public Locale getSuggestionNameLocale() {
        return suggestionNameLocale;
    }

    // -------------------- LOGIC --------------------

    public static String getFundingProgramFieldName() {
        return "fundingProgram";
    }

    public static String getSuggestionNameFieldName() {
        return "suggestionName";
    }

    public static String getGrantAgencyAcronymFieldName() {
        return "grantAgencyAcronym";
    }

}
