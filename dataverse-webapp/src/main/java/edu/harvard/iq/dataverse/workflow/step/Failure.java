package edu.harvard.iq.dataverse.workflow.step;

import edu.harvard.iq.dataverse.persistence.workflow.WorkflowArtifactSource;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Result returned when step execution fails.
 *
 * @author michael
 */
public class Failure implements WorkflowStepResult {

    public static final String REASON_PARAM_NAME = "reason";
    public static final String MESSAGE_PARAM_NAME = "message";

    private final String reason;
    private final String message;
    private transient final List<WorkflowArtifactSource> artifacts;

    public Failure(String reason, WorkflowArtifactSource...artifacts) {
        this(reason, reason, artifacts);
    }

    public Failure(String reason, List<WorkflowArtifactSource> artifacts) {
        this(reason, reason, artifacts);
    }

    public Failure(String reason, String message, WorkflowArtifactSource...artifacts) {
        this(reason, message, Arrays.asList(artifacts));
    }

    /**
     * Constructs a new failure message.
     *
     * @param reason  Technical reason (for logs etc.).
     * @param message Human readable reason.
     * @param artifacts artifacts to persist.
     */
    public Failure(String reason, String message, List<WorkflowArtifactSource> artifacts) {
        this.reason = reason;
        this.message = message;
        this.artifacts = artifacts;
    }

    /**
     * Holds the technical reason for the failure, useful for debugging the problem.
     *
     * @return the technical reason for the problem.
     */
    public String getReason() {
        return reason;
    }

    /**
     * Holds the user-friendly message explaining the failure.
     *
     * @return user-friendly message for the failure.
     */
    public String getMessage() {
        return message;
    }

    @Override
    public Map<String, String> getData() {
        Map<String, String> data = new HashMap<>();
        data.put(REASON_PARAM_NAME, reason);
        data.put(MESSAGE_PARAM_NAME, message);
        return data;
    }

    @Override
    public List<WorkflowArtifactSource> getArtifacts() {
        return artifacts;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Failure failure = (Failure) o;

        if (!Objects.equals(reason, failure.reason)) return false;
        return Objects.equals(message, failure.message);
    }

    @Override
    public int hashCode() {
        int result = reason != null ? reason.hashCode() : 0;
        result = 31 * result + (message != null ? message.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "WorkflowStepResult.Failure{" + "reason=" + reason + ", message=" + message + '}';
    }
}
