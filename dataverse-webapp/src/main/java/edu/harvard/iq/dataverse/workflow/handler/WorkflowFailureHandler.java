package edu.harvard.iq.dataverse.workflow.handler;

import edu.harvard.iq.dataverse.workflow.WorkflowExecutionContext;

public interface WorkflowFailureHandler {

    public void handleFailure(WorkflowExecutionContext workflowExecutionContext);
}
