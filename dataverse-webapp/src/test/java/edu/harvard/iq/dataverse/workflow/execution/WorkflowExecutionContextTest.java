package edu.harvard.iq.dataverse.workflow.execution;

import edu.harvard.iq.dataverse.persistence.workflow.Workflow;
import edu.harvard.iq.dataverse.persistence.workflow.WorkflowExecution;
import edu.harvard.iq.dataverse.persistence.workflow.WorkflowExecutionRepository;
import edu.harvard.iq.dataverse.persistence.workflow.WorkflowExecutionStepRepository;
import edu.harvard.iq.dataverse.workflow.WorkflowStepRegistry;
import edu.harvard.iq.dataverse.workflow.WorkflowStepSPI;
import edu.harvard.iq.dataverse.workflow.step.WorkflowStep;
import edu.harvard.iq.dataverse.workflow.step.WorkflowStepParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;

import static edu.harvard.iq.dataverse.persistence.workflow.WorkflowMother.givenWorkflow;
import static edu.harvard.iq.dataverse.persistence.workflow.WorkflowMother.givenWorkflowExecution;
import static edu.harvard.iq.dataverse.persistence.workflow.WorkflowMother.givenWorkflowStep;
import static edu.harvard.iq.dataverse.workflow.execution.WorkflowContextMother.givenWorkflowExecutionContext;
import static java.time.ZoneOffset.UTC;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class WorkflowExecutionContextTest implements WorkflowStepSPI {

    WorkflowStepRegistry steps = new WorkflowStepRegistry();
    WorkflowExecutionRepository executions = mock(WorkflowExecutionRepository.class);

    long datasetId = 1L;
    Workflow workflow = givenWorkflow(1L,
            givenWorkflowStep("test", "step1")
    );

    WorkflowExecution execution = givenWorkflowExecution(datasetId, workflow.getId());
    WorkflowExecutionContext context = givenWorkflowExecutionContext(workflow, execution);

    Clock clock = Clock.fixed(Instant.parse("2020-06-01T09:10:20.00Z"), UTC);

    @BeforeEach
    void setUp() {
        steps.register("test", this);
    }

    @Test
    void shouldHaveFirstStepToExecute() {
        // expect
        assertThat(context.hasMoreStepsToExecute()).isTrue();
    }

    @Test
    void shouldStartExecution() {
        // when
        context.start(executions);
        // then
        assertThat(execution.isStarted()).isTrue();
        assertThat(execution.getStartedAt()).isEqualTo(clock.instant());
        assertThat(execution.getUserId()).isNotBlank();
        assertThat(execution.getIpAddress()).isNotBlank();
    }

    @Test
    void shouldNotGetFirstStepWhenNotStarted() {
        // expect
        assertThatThrownBy(() -> context.nextStepToExecute())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldGetFirstStepToExecute() {
        // given
        context.start(executions);
        // when
        WorkflowExecutionStepContext stepContext = context.nextStepToExecute();
        // then
        assertThat(execution.getSteps()).containsExactly(stepContext.getStepExecution());
        assertThat(stepContext.getStepExecution().isStarted()).isFalse();
    }

    @Test
    void shouldGetSameStepToExecuteWhenNotFinished() {
        // given
        context.start(executions);
        WorkflowExecutionStepContext stepContext = context.nextStepToExecute();
        // when
        WorkflowExecutionStepContext otherContext = context.nextStepToExecute();
        // then
        assertThat(stepContext.getStepExecution()).isSameAs(otherContext.getStepExecution());
    }

    @Test
    void shouldNotGetMoreStepsToExecuteWhenAllFinished() {
        // given
        context.start(executions);
        WorkflowExecutionStepContext stepContext = context.nextStepToExecute();
        stepContext.start(emptyMap(), steps);
        stepContext.succeeded(emptyMap(), mock(WorkflowExecutionStepRepository.class));
        // expect
        assertThatThrownBy(() -> context.nextStepToExecute())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldNotFinishWhenNotStarted() {
        assertThatThrownBy(() -> context.finish(executions))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldFinishExecution() {
        // given
        context.start(executions);
        // when
        context.finish(executions);
        // then
        assertThat(execution.isFinished()).isTrue();
        assertThat(execution.getFinishedAt()).isEqualTo(clock.instant());
    }

    @Test
    void shouldHaveNothingToRollbackWhenNothingExecuted() {
        // expect
        assertThat(context.hasMoreStepsToRollback()).isFalse();
    }

    @Test
    void shouldHaveNothingToRollbackWhenNothingStarted() {
        // given
        context.start(executions);
        context.nextStepToExecute();
        // expect
        assertThat(context.hasMoreStepsToRollback()).isFalse();
    }

    @Test
    void shouldHaveLastStartedStepToRollBack() {
        // given
        context.start(executions);
        context.nextStepToExecute()
                .start(emptyMap(), steps);
        // expect
        assertThat(context.hasMoreStepsToRollback()).isTrue();
    }

    @Test
    void shouldNotGetStepToRollbackWhenNothingExecuted() {
        // expect
        assertThatThrownBy(() -> context.nextStepToRollback())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldNotGetStepToRollbackWhenNotFinished() {
        // given
        context.start(executions);
        context.nextStepToExecute();
        // expect
        assertThatThrownBy(() -> context.nextStepToRollback())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldGetLastStartedStepToRollBack() {
        // given
        context.start(executions);
        WorkflowExecutionStepContext stepContext = context.nextStepToExecute();
        stepContext.start(emptyMap(), steps);
        context.finish(executions);
        // when
        WorkflowExecutionStepContext otherContext = context.nextStepToRollback();
        // then
        assertThat(stepContext.getStepExecution()).isSameAs(otherContext.getStepExecution());
        assertThat(otherContext.getStepExecution().isRollBackNeeded()).isTrue();
    }

    @Override
    public WorkflowStep getStep(String stepType, WorkflowStepParams stepParameters) {
        return new TestWorkflowStep(stepParameters);
    }
}
