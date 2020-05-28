package edu.harvard.iq.dataverse.workflow.step;

import java.util.Map;

import static java.util.Collections.emptyMap;

/**
 * Result returned when a {@link WorkflowStep} is finished successfully.
 *
 * @author kaczynskid
 */
public class Success implements WorkflowStepResult {

    private final Map<String, String> data;

    public Success() {
        this(emptyMap());
    }

    public Success(Map<String, String> data) {
        this.data = data;
    }

    public Map<String, String> getData() {
        return data;
    }

    @Override
    public String toString() {
        return "WorkflowStepResult.Success{" + "data=" + data + '}';
    }
}
