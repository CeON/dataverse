package edu.harvard.iq.dataverse.workflow.handler;

import edu.harvard.iq.dataverse.workflow.WorkflowExecutionContext;

public interface WorkflowSuccessHandler {
    
    public void handleSuccess(WorkflowExecutionContext workflowExecutionContext);
}
