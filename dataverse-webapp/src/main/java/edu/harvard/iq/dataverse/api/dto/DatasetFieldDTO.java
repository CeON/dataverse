package edu.harvard.iq.dataverse.api.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class DatasetFieldDTO {
    private String typeName;
    private Boolean multiple;
    private String typeClass;
    private Object value;

    @JsonIgnore
    private boolean emailType;

    // -------------------- CONSTRUCTORS --------------------

    public DatasetFieldDTO() { }

    public DatasetFieldDTO(String typeName, Boolean multiple, String typeClass, Object value) {
        this.typeName = typeName;
        this.multiple = multiple;
        this.typeClass = typeClass;
        this.value = value;
    }

    // -------------------- GETTERS --------------------

    public String getTypeName() {
        return typeName;
    }

    public Boolean getMultiple() {
        return multiple;
    }

    public String getTypeClass() {
        return typeClass;
    }

    public Object getValue() {
        return value;
    }

    @JsonIgnore
    public boolean isEmailType() {
        return emailType;
    }

    // -------------------- LOGIC --------------------

    public String getSinglePrimitive() {
        return value == null ? "" : (String) value;
    }

    public String getSingleVocabulary() {
        return getSinglePrimitive();
    }

    public Set<DatasetFieldDTO> getSingleCompound() {
        return value != null
                ? new LinkedHashSet<>(((Map<String, DatasetFieldDTO>) value).values())
                : Collections.emptySet();
    }

    public List<String> getMultiplePrimitive() {
        return value != null
                ? (List<String>) value : Collections.emptyList();
    }

    public List<String> getMultipleVocabulary() {
        return getMultiplePrimitive();
    }

    public List<Set<DatasetFieldDTO>> getMultipleCompound() {
        if (value == null) {
            return Collections.emptyList();
        }
        List<Map<String, DatasetFieldDTO>> fieldList = (List<Map<String, DatasetFieldDTO>>) value;
        return fieldList.stream()
                .map(v -> new LinkedHashSet<>(v.values()))
                .collect(Collectors.toList());
    }

    // -------------------- SETTERS --------------------

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public void setMultiple(Boolean multiple) {
        this.multiple = multiple;
    }

    public void setTypeClass(String typeClass) {
        this.typeClass = typeClass;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public void setEmailType(boolean emailType) {
        this.emailType = emailType;
    }

    // -------------------- hashCode & equals --------------------

    @Override
    public int hashCode() {
        return Objects.hash(typeName, multiple, typeClass, value);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        DatasetFieldDTO that = (DatasetFieldDTO) other;
        return Objects.equals(typeName, that.typeName) &&
                Objects.equals(multiple, that.multiple) &&
                Objects.equals(typeClass, that.typeClass) &&
                Objects.equals(value, that.value);
    }
}
