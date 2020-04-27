package edu.harvard.iq.dataverse.importer.metadata;

public interface ImporterFieldKey {
    static final ImporterFieldKey IRRELEVANT = () -> "IRRELEVANT";

    String getName();
}
