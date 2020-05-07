package edu.harvard.iq.dataverse.importer.metadata;

public interface ImporterFieldKey {
    ImporterFieldKey IRRELEVANT = () -> "IRRELEVANT";

    String getName();

    default boolean isRelevant() {
        return !IRRELEVANT.equals(this);
    }
}
