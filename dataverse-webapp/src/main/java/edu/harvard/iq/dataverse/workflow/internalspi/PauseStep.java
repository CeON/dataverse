package edu.harvard.iq.dataverse.workflow.internalspi;

import edu.harvard.iq.dataverse.workflow.execution.WorkflowExecutionStepContext;
import edu.harvard.iq.dataverse.workflow.step.Failure;
import edu.harvard.iq.dataverse.workflow.step.Pending;
import edu.harvard.iq.dataverse.workflow.step.Success;
import edu.harvard.iq.dataverse.workflow.step.WorkflowStep;
import edu.harvard.iq.dataverse.workflow.step.WorkflowStepParams;
import edu.harvard.iq.dataverse.workflow.step.WorkflowStepResult;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A sample step that pauses the workflow.
 *
 * @author michael
 */
public class PauseStep implements WorkflowStep {

    private static final Logger logger = Logger.getLogger(PauseStep.class.getName());

    /**
     * Constant used by testing to simulate a failed step.
     */
    public static final String FAILURE_RESPONSE = "fail";

    private final WorkflowStepParams params;

    public PauseStep(WorkflowStepParams params) {
        this.params = params;
    }

    @Override
    public WorkflowStepResult run(WorkflowExecutionStepContext context) {
        return new Pending(params.asMap());
    }

    @Override
    public WorkflowStepResult resume(WorkflowExecutionStepContext context, Map<String, String> internalData, String externalData) {
        logger.log(Level.INFO, "local parameters match: {0}", internalData.equals(params.asMap()));
        logger.log(Level.INFO, "externalData: \"{0}\"", externalData);
        return externalData.trim().equals(FAILURE_RESPONSE) ? new Failure("Simulated fail") : new Success();
    }

    @Override
    public void rollback(WorkflowExecutionStepContext context, Failure reason) {
        // nothing to roll back
    }
}
