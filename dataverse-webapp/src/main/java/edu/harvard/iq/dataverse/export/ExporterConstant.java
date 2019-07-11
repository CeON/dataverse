package edu.harvard.iq.dataverse.export;

import java.util.Optional;

public enum ExporterConstant {

    DDI,
    DATA_CITE,
    DC_TERMS,
    DUBLIN_CORE,
    JSON,
    OAI_DDI,
    OAI_ORE,
    SCHEMA_DOT_ORG;

    public static Optional<ExporterConstant> fromString(String enumValue) {

        try {
            return Optional.of(ExporterConstant.valueOf(enumValue.toUpperCase()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
