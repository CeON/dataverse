package edu.harvard.iq.dataverse.importers.ui;

import com.amazonaws.services.dynamodbv2.xspec.L;

import java.util.ArrayList;
import java.util.List;

public enum ProcessingType {
    FILL_IF_EMPTY("metadata.import.processing.type.fill.if.empty"),
    OVERWRITE("metadata.import.processing.type.overwrite"),
    MULTIPLE_OVERWRITE("metadata.import.processing.type.multiple.overwrite"),
    MULTIPLE_CREATE_NEW("metadata.import.processing.type.create.new")
    ;

    private String key;

    // -------------------- CONSTRUCTORS --------------------

    ProcessingType(String key) {
        this.key = key;
    }

    // -------------------- GETTERS --------------------

    public String getKey() {
        return key;
    }
}
