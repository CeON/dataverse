package edu.harvard.iq.dataverse.api.dto;

public class ApiErrorDTO {

    String message;

    // -------------------- CONSTRUCTORS --------------------

    public ApiErrorDTO() { }

    public ApiErrorDTO(String message) {
        this.message = message;
    }

    // -------------------- GETTERS --------------------

    public String getMessage() {
        return message;
    }

    // -------------------- SETTERS --------------------

    public void setMessage(String message) {
        this.message = message;
    }
}
