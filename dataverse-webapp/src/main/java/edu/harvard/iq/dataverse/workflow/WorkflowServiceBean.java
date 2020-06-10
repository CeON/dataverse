package edu.harvard.iq.dataverse.workflow;

import edu.harvard.iq.dataverse.EjbDataverseEngine;
import edu.harvard.iq.dataverse.RoleAssigneeServiceBean;
import edu.harvard.iq.dataverse.engine.command.CommandContext;
import edu.harvard.iq.dataverse.engine.command.DataverseRequest;
import edu.harvard.iq.dataverse.engine.command.exception.CommandException;
import edu.harvard.iq.dataverse.engine.command.impl.FinalizeDatasetPublicationCommand;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetLock;
import edu.harvard.iq.dataverse.persistence.group.IpAddress;
import edu.harvard.iq.dataverse.persistence.user.ApiToken;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.persistence.user.User;
import edu.harvard.iq.dataverse.persistence.workflow.PendingWorkflowInvocation;
import edu.harvard.iq.dataverse.persistence.workflow.Workflow;
import edu.harvard.iq.dataverse.persistence.workflow.WorkflowStepData;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import edu.harvard.iq.dataverse.workflow.WorkflowContext.TriggerType;
import edu.harvard.iq.dataverse.workflow.step.Failure;
import edu.harvard.iq.dataverse.workflow.step.Pending;
import edu.harvard.iq.dataverse.workflow.step.Success;
import edu.harvard.iq.dataverse.workflow.step.WorkflowStep;
import edu.harvard.iq.dataverse.workflow.step.WorkflowStepResult;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.Asynchronous;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service bean for managing and executing {@link Workflow}s
 *
 * @author michael
 */
@Stateless
public class WorkflowServiceBean {

    private static final Logger log = LoggerFactory.getLogger(WorkflowServiceBean.class);

    private static final String WORKFLOW_ID_KEY = "WorkflowServiceBean.WorkflowId:";

    @PersistenceContext(unitName = "VDCNet-ejbPU")
    EntityManager em;

    @Inject
    SettingsServiceBean settings;

    @EJB
    RoleAssigneeServiceBean roleAssignees;

    @EJB
    EjbDataverseEngine engine;

    @Inject
    WorkflowStepRegistry stepRegistry;

    /**
     * Starts executing workflow {@code wf} under the passed context.
     *
     * @param wf   the workflow to execute.
     * @param ctxt the context in which the workflow is executed.
     * @throws CommandException If the dataset could not be locked.
     */
    @Asynchronous
    public void start(Workflow wf, WorkflowContext ctxt)  {
        ctxt = refresh(ctxt, retrieveRequestedSettings(wf.getRequiredSettings()), getCurrentApiToken(ctxt.getRequest().getAuthenticatedUser()));
        lockDataset(ctxt);
        forward(wf, ctxt);
    }

    private ApiToken getCurrentApiToken(AuthenticatedUser au) {
        if (au != null) {
            CommandContext ctxt = engine.getContext();
            ApiToken token = ctxt.authentication().findApiTokenByUser(au);
            if ((token == null) || (token.getExpireTime().before(new Date()))) {
                token = ctxt.authentication().generateApiTokenForUser(au);
            }
            return token;
        }
        return null;
    }

    private Map<String, Object> retrieveRequestedSettings(Map<String, String> requiredSettings) {
        Map<String, Object> retrievedSettings = new HashMap<String, Object>();
        for (String setting : requiredSettings.keySet()) {
            String settingType = requiredSettings.get(setting);
            switch (settingType) {
                case "string": {
                    retrievedSettings.put(setting, settings.get(setting));
                    break;
                }
                case "boolean": {
                    retrievedSettings.put(setting, settings.isTrue(settingType));
                    break;
                }
                case "long": {
                    retrievedSettings.put(setting, settings.getValueForKeyAsLong(SettingsServiceBean.Key.valueOf(setting)));
                    break;
                }
            }
        }
        return retrievedSettings;
    }

    /**
     * Starting the resume process for a pending workflow. We first delete the
     * pending workflow to minimize double invocation, and then asynchronously
     * resume the work.
     *
     * @param pending The workflow to resume.
     * @param body    the response from the remote system.
     * @see #doResume(edu.harvard.iq.dataverse.persistence.workflow.PendingWorkflowInvocation,
     * java.lang.String)
     */
    @Asynchronous
    public void resume(PendingWorkflowInvocation pending, String body) {
        em.remove(em.merge(pending));
        doResume(pending, body);
    }

    @Asynchronous
    private void forward(Workflow wf, WorkflowContext ctxt) {
        executeSteps(wf, ctxt, 0);
    }

    private void doResume(PendingWorkflowInvocation pending, String body) {
        Workflow wf = pending.getWorkflow();
        List<WorkflowStepData> stepsLeft = wf.getSteps().subList(pending.getPendingStepIdx(), wf.getSteps().size());

        WorkflowStepData wsd = stepsLeft.get(0);
        WorkflowStep pendingStep = stepRegistry.getStep(wsd.getProviderId(), wsd.getStepType(), wsd.getStepParameters());
        WorkflowContext newCtxt = reCreateContext(pending, roleAssignees);
        
        final WorkflowContext ctxt = refresh(newCtxt, retrieveRequestedSettings(wf.getRequiredSettings()), getCurrentApiToken(newCtxt.getRequest().getAuthenticatedUser()));
        WorkflowStepResult res = pendingStep.resume(ctxt, pending.getLocalData(), body);
        if (res instanceof Failure) {
            rollback(wf, ctxt, (Failure) res, pending.getPendingStepIdx() - 1);
        } else if (res instanceof Pending) {
            pauseAndAwait(wf, ctxt, (Pending) res, pending.getPendingStepIdx());
        } else {
            executeSteps(wf, ctxt, pending.getPendingStepIdx() + 1);
        }
    }

    public WorkflowContext reCreateContext(PendingWorkflowInvocation pending, RoleAssigneeServiceBean roleAssignees) {
        DataverseRequest aRequest = new DataverseRequest((User) roleAssignees.getRoleAssignee(pending.getUserId()), IpAddress.valueOf(pending.getIpAddress()));
        
        WorkflowContext workflowContext = new WorkflowContext(aRequest, pending.getDataset(), pending.getNextVersionNumber(),
                pending.getNextMinorVersionNumber(), WorkflowContext.TriggerType.values()[pending.getTypeOrdinal()], null, null,
                pending.isDatasetExternallyReleased());
        workflowContext.setInvocationId(pending.getInvocationId());
        return workflowContext;
    }
    
    @Asynchronous
    private void rollback(Workflow wf, WorkflowContext ctxt, Failure failure, int lastCompletedStepIdx) {
        ctxt = refresh(ctxt);
        final List<WorkflowStepData> steps = wf.getSteps();

        for (int stepIdx = lastCompletedStepIdx; stepIdx >= 0; --stepIdx) {
            WorkflowStepData wsd = steps.get(stepIdx);
            WorkflowStep step = stepRegistry.getStep(wsd.getProviderId(), wsd.getStepType(), wsd.getStepParameters());

            try {
                log.info("Workflow {} step {}: Rollback", ctxt.getInvocationId(), stepIdx);
                rollbackStep(step, ctxt, failure);
            } catch (Exception e) {
                log.warn("Workflow " + ctxt.getInvocationId()
                        + " step " + stepIdx + ": Rollback error: " + e.getMessage(), e);
            }
        }

        log.info("Removing workflow lock");
        try {
            unlockDataset(ctxt);
        } catch (CommandException ex) {
            log.error("Error restoring dataset locks state after rollback: " + ex.getMessage(), ex);
        }
    }

    /**
     * Execute the passed workflow, starting from {@code initialStepIdx}.
     *
     * @param wf             The workflow to run.
     * @param ctxt           Execution context to run the workflow in.
     * @param initialStepIdx 0-based index of the first step to run.
     */
    private void executeSteps(Workflow wf, WorkflowContext ctxt, int initialStepIdx) {
        final List<WorkflowStepData> steps = wf.getSteps();

        Map<String, String> stepParameters = new HashMap<>();
        for (int stepIdx = initialStepIdx; stepIdx < steps.size(); stepIdx++) {
            WorkflowStepData wsd = steps.get(stepIdx);
            stepParameters.putAll(wsd.getStepParameters());
            WorkflowStep step = stepRegistry.getStep(wsd.getProviderId(), wsd.getStepType(), stepParameters);

            try {
                WorkflowStepResult stepResult = runStep(step, ctxt);

                if (stepResult instanceof Success) {
                    log.info("Workflow {} step {}: OK", ctxt.getInvocationId(), stepIdx);
                    em.merge(ctxt.getDataset());
                    ctxt = refresh(ctxt);
                    stepParameters.clear();
                    stepParameters.putAll(((Success) stepResult).getData());

                } else if (stepResult instanceof Failure) {
                    log.warn("Workflow {} failed: {}", ctxt.getInvocationId(), ((Failure) stepResult).getReason());
                    rollback(wf, ctxt, (Failure) stepResult, stepIdx - 1);
                    return;

                } else if (stepResult instanceof Pending) {
                    pauseAndAwait(wf, ctxt, (Pending) stepResult, stepIdx);
                    return;
                }
            } catch (Exception e) {
                log.warn("Workflow {} step {}: Uncaught exception:", ctxt.getInvocationId(), e.getMessage());
                log.warn("Trace:", e);
                rollback(wf, ctxt, new Failure(e.getMessage()), stepIdx - 1);
                return;
            }
        }

        workflowCompleted(wf, ctxt);

    }

    //////////////////////////////////////////////////////////////
    // Internal methods to run each step in its own transaction.
    //

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    WorkflowStepResult runStep(WorkflowStep step, WorkflowContext ctxt) {
        return step.run(ctxt);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    WorkflowStepResult resumeStep(WorkflowStep step, WorkflowContext ctxt, Map<String, String> localData, String externalData) {
        return step.resume(ctxt, localData, externalData);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    void rollbackStep(WorkflowStep step, WorkflowContext ctxt, Failure reason) {
        step.rollback(ctxt, reason);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    void lockDataset(WorkflowContext ctxt)  {
        final DatasetLock datasetLock = new DatasetLock(DatasetLock.Reason.Workflow, ctxt.getRequest().getAuthenticatedUser());
        /* Note that this method directly adds a lock to the database rather than adding it via
         * engine.submit(new AddLockCommand(ctxt.getRequest(), ctxt.getDataset(), datasetLock));
         * which would update the dataset's list of locks, etc.
         * An em.find() for the dataset would get a Dataset that has an updated list of locks, but this copy would not have any changes
         * made in a calling command (e.g. for a PostPublication workflow, the fact that the latest version is 'released' is not yet in the
         * database.
         */
        datasetLock.setDataset(ctxt.getDataset());
        em.persist(datasetLock);
        em.flush();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    void unlockDataset(WorkflowContext ctxt)  {
        /* Since the lockDataset command above directly persists a lock to the database,
         * the ctxt.getDataset() is not updated and its list of locks can't be used. Using the named query below will find the workflow
         * lock and remove it (actually all workflow locks for this Dataset but only one workflow should be active).
         */
        TypedQuery<DatasetLock> lockCounter = em.createNamedQuery("DatasetLock.getLocksByDatasetId", DatasetLock.class);
        lockCounter.setParameter("datasetId", ctxt.getDataset().getId());
        List<DatasetLock> locks = lockCounter.getResultList();
        for (DatasetLock lock : locks) {
            if (lock.getReason() == DatasetLock.Reason.Workflow) {
                log.trace("Removing lock");
                em.remove(lock);
            }
        }
        em.flush();
    }

    //
    //
    //////////////////////////////////////////////////////////////

    private void pauseAndAwait(Workflow wf, WorkflowContext ctxt, Pending pendingRes, int idx) {
        PendingWorkflowInvocation pending = new PendingWorkflowInvocation(wf, new HashMap<>(pendingRes.getData()));
        
        pending.setInvocationId(ctxt.getInvocationId());
        pending.setDataset(ctxt.getDataset());
        pending.setNextVersionNumber(ctxt.getNextVersionNumber());
        pending.setNextMinorVersionNumber(ctxt.getNextMinorVersionNumber());
        pending.setUserId(ctxt.getRequest().getUser().getIdentifier());
        pending.setIpAddress(ctxt.getRequest().getUser().getIdentifier());
        pending.setTypeOrdinal(ctxt.getType().ordinal());
        pending.setDatasetExternallyReleased(ctxt.getDatasetExternallyReleased());
        
        pending.setPendingStepIdx(idx);
        em.persist(pending);
    }

    private void workflowCompleted(Workflow wf, WorkflowContext ctxt) {
        log.info("Workflow {} completed.", ctxt.getInvocationId());

        try {
            if (ctxt.getType() == TriggerType.PrePublishDataset) {
                unlockDataset(ctxt);
                engine.submit(new FinalizeDatasetPublicationCommand(ctxt.getDataset(), ctxt.getRequest(), ctxt.getDatasetExternallyReleased()));
            } else {
                log.trace("Removing workflow lock");
                unlockDataset(ctxt);
            }
        } catch (CommandException ex) {
            log.error("Exception finalizing workflow " + ctxt.getInvocationId() + ": " + ex.getMessage(), ex);
            rollback(wf, ctxt, new Failure("Exception while finalizing the publication: " + ex.getMessage()), wf.getSteps().size() - 1);
        }
    }

    public List<Workflow> listWorkflows() {
        return em.createNamedQuery("Workflow.listAll", Workflow.class).getResultList();
    }

    public Optional<Workflow> getWorkflow(long workflowId) {
        return Optional.ofNullable(em.find(Workflow.class, workflowId));
    }

    public Workflow save(Workflow workflow) {
        if (workflow.getId() == null) {
            em.persist(workflow);
            em.flush();
            return workflow;
        } else {
            return em.merge(workflow);
        }
    }

    /**
     * Deletes the workflow with the passed id (if possible).
     *
     * @param workflowId id of the workflow to be deleted.
     * @return {@code true} iff the workflow was deleted, {@code false} if it was not found.
     * @throws IllegalArgumentException iff the workflow is a default workflow.
     */
    public boolean deleteWorkflow(long workflowId) {
        Optional<Workflow> doomedOpt = getWorkflow(workflowId);
        if (doomedOpt.isPresent()) {
            // validate that this is not the default workflow
            for (WorkflowContext.TriggerType tp : WorkflowContext.TriggerType.values()) {
                String defaultWorkflowId = settings.get(workflowSettingKey(tp));
                if (StringUtils.isNotEmpty(defaultWorkflowId)
                        && Long.parseLong(defaultWorkflowId) == doomedOpt.get().getId()) {
                    throw new IllegalArgumentException("Workflow " + workflowId + " cannot be deleted as it is the default workflow for trigger " + tp.name());
                }
            }

            em.remove(doomedOpt.get());
            return true;
        } else {
            return false;
        }
    }

    public List<PendingWorkflowInvocation> listPendingInvocations() {
        return em.createNamedQuery("PendingWorkflowInvocation.listAll")
                .getResultList();
    }

    public PendingWorkflowInvocation getPendingWorkflow(String invocationId) {
        return em.find(PendingWorkflowInvocation.class, invocationId);
    }

    public Optional<Workflow> getDefaultWorkflow(WorkflowContext.TriggerType type) {
        String defaultWorkflowId = settings.get(workflowSettingKey(type));
        if (StringUtils.isEmpty(defaultWorkflowId)) {
            return Optional.empty();
        }
        return getWorkflow(Long.parseLong(defaultWorkflowId));
    }

    /**
     * Sets the workflow of the default it.
     *
     * @param id   Id of the default workflow, or {@code null}, for disabling the
     *             default workflow.
     * @param type type of the workflow.
     */
    public void setDefaultWorkflowId(WorkflowContext.TriggerType type, Long id) {
        String workflowKey = workflowSettingKey(type);
        if (id == null) {
            settings.delete(workflowKey);
        } else {
            settings.set(workflowKey, id.toString());
        }
    }

    private String workflowSettingKey(WorkflowContext.TriggerType type) {
        return WORKFLOW_ID_KEY + type.name();
    }

    private WorkflowContext refresh(WorkflowContext ctxt) {
        return refresh(ctxt, ctxt.getSettings(), ctxt.getApiToken());
    }

    private WorkflowContext refresh(WorkflowContext ctxt, Map<String, Object> settings, ApiToken apiToken) {
        /* An earlier version of this class used em.find() to 'refresh' the Dataset in the context.
         * For a PostPublication workflow, this had the consequence of hiding/removing changes to the Dataset
         * made in the FinalizeDatasetPublicationCommand (i.e. the fact that the draft version is now released and
         * has a version number). It is not clear to me if the em.merge below is needed or if it handles the case of
         * resumed workflows. (The overall method is needed to allow the context to be updated in the start() method with the
         * settings and APItoken retrieved by the WorkflowServiceBean) - JM - 9/18.
         */
        return new WorkflowContext(ctxt.getRequest(),
                                   em.merge(ctxt.getDataset()), ctxt.getNextVersionNumber(),
                                   ctxt.getNextMinorVersionNumber(), ctxt.getType(), settings, apiToken, ctxt.getDatasetExternallyReleased());
    }

}
