package edu.harvard.iq.dataverse.importer.metadata;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ResultField {
    private String name;
    private String value;
    private List<ResultField> children;

    // -------------------- CONSTRUCTORS --------------------

    private ResultField(String name, String value) {
        this.name = name;
        this.value = value;
        this.children = Collections.emptyList();
    }

    private ResultField(String name, ResultField... children) {
        this.name = name;
        this.value = StringUtils.EMPTY;
        this.children = children == null || children.length == 0
                ? Collections.emptyList()
                : Arrays.stream(children).collect(Collectors.toList());
    }

    // -------------------- GETTERS --------------------

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public List<ResultField> getChildren() {
        return children;
    }

    // -------------------- LOGIC --------------------

    public static ResultField of(String name, String value) {
        return new ResultField(name, value);
    }

    public static ResultField of(String name, ResultField... children) {
        return new ResultField(name, children);
    }

    // -------------------- SETTERS --------------------

    public void setValue(String value) {
        this.value = value;
    }
}
