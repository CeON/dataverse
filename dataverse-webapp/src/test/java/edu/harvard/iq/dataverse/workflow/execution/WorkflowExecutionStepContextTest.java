package edu.harvard.iq.dataverse.workflow.execution;

import edu.harvard.iq.dataverse.persistence.workflow.Workflow;
import edu.harvard.iq.dataverse.persistence.workflow.WorkflowExecution;
import edu.harvard.iq.dataverse.persistence.workflow.WorkflowExecutionRepository;
import edu.harvard.iq.dataverse.persistence.workflow.WorkflowExecutionStepRepository;
import edu.harvard.iq.dataverse.workflow.WorkflowStepRegistry;
import edu.harvard.iq.dataverse.workflow.step.Success;
import edu.harvard.iq.dataverse.workflow.step.WorkflowStepParams;
import edu.harvard.iq.dataverse.workflow.step.WorkflowStepResult;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Stream;

import static edu.harvard.iq.dataverse.persistence.workflow.WorkflowMother.givenWorkflow;
import static edu.harvard.iq.dataverse.persistence.workflow.WorkflowMother.givenWorkflowExecution;
import static edu.harvard.iq.dataverse.persistence.workflow.WorkflowMother.givenWorkflowStep;
import static edu.harvard.iq.dataverse.workflow.execution.WorkflowContextMother.givenWorkflowExecutionContext;
import static java.time.ZoneOffset.UTC;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class WorkflowExecutionStepContextTest {

    WorkflowStepRegistry steps = mock(WorkflowStepRegistry.class);
    WorkflowExecutionRepository executions = mock(WorkflowExecutionRepository.class);
    WorkflowExecutionStepRepository stepExecutions = mock(WorkflowExecutionStepRepository.class);

    long datasetId = 1L;
    Workflow workflow = givenWorkflow(1L,
            givenWorkflowStep("step1")
    );

    WorkflowExecution execution = givenWorkflowExecution(datasetId, workflow.getId());
    WorkflowExecutionContext context = givenWorkflowExecutionContext(workflow, execution);

    Clock clock = Clock.fixed(Instant.parse("2020-06-01T09:10:20.00Z"), UTC);

    @BeforeEach
    void setUp() {
        context.start(executions);
    }

    @Test
    void shouldNotBeStartedUponCreation() {
        // when
        WorkflowExecutionStepContext stepContext = context.nextStepToExecute();
        // then
        assertThat(stepContext.getStepExecution().isStarted()).isFalse();
    }

    @Test
    void shouldStartStepExecution() {
        // given
        givenImmediateWorkflowStep();
        WorkflowExecutionStepContext stepContext = context.nextStepToExecute();
        Map<String, String> params = Stream.of(
                Tuple.of("test", "value"),
                Tuple.of("param", "value")
        ).collect(toMap(Tuple2::_1, Tuple2::_2));
        // when
        WorkflowStepResult result = stepContext.start(singletonMap("test", "value"), steps);
        // then
        assertThat(stepContext.getStepExecution().isStarted()).isTrue();
        assertThat(stepContext.getStepExecution().getInputParams()).containsExactlyEntriesOf(params);
        assertThat(result).isEqualTo(new Success(params));
    }

    @Test
    void shouldPauseStartedExecution() {
        // given
        givenPausingWorkflowStep();
        WorkflowExecutionStepContext stepContext = context.nextStepToExecute();
        stepContext.start(emptyMap(), steps);
        // when
        stepContext.paused(singletonMap("test", "value"), stepExecutions);
        // then
        assertThat(stepContext.getStepExecution().isPaused()).isTrue();
        assertThat(stepContext.getStepExecution().getPausedData())
                .containsExactlyEntriesOf(singletonMap("test", "value"));
    }

    @Test
    void shouldResumePausedExecution() {
        // given
        givenPausingWorkflowStep();
        WorkflowExecutionStepContext stepContext = context.nextStepToExecute();
        stepContext.start(emptyMap(), steps);
        stepContext.paused(singletonMap("test", "value"), stepExecutions);
        // when
        WorkflowStepResult result = stepContext.resume("test", steps);
        // then
        assertThat(stepContext.getStepExecution().isResumed()).isTrue();
        assertThat(stepContext.getStepExecution().getResumedData()).isEqualTo("test");
        assertThat(result).isEqualTo(new Success(singletonMap("test", "value")));
    }

    @Test
    void shouldFinishSuccessfully() {
        // given
        givenPausingWorkflowStep();
        WorkflowExecutionStepContext stepContext = context.nextStepToExecute();
        stepContext.start(emptyMap(), steps);
        // when
        stepContext.succeeded(singletonMap("test", "value"), stepExecutions);
        // then
        assertThat(stepContext.getStepExecution().isFinished()).isTrue();
        assertThat(stepContext.getStepExecution().getFinishedSuccessfully()).isTrue();
        assertThat(stepContext.getStepExecution().getOutputParams())
                .containsExactlyEntriesOf(singletonMap("test", "value"));
    }

    @Test
    void shouldFinishWithError() {
        // given
        givenPausingWorkflowStep();
        WorkflowExecutionStepContext stepContext = context.nextStepToExecute();
        stepContext.start(emptyMap(), steps);
        // when
        stepContext.failed(singletonMap("test", "value"), stepExecutions);
        assertThat(stepContext.getStepExecution().isFinished()).isTrue();
        assertThat(stepContext.getStepExecution().getFinishedSuccessfully()).isFalse();
        assertThat(stepContext.getStepExecution().getOutputParams())
                .containsExactlyEntriesOf(singletonMap("test", "value"));
    }

    private void givenImmediateWorkflowStep() {
        doAnswer(invocation -> new TestWorkflowStep(new WorkflowStepParams(invocation.getArgument(2))))
                .when(steps).getStep(anyString(), anyString(), anyMap());
    }

    private void givenPausingWorkflowStep() {
        doAnswer(invocation -> new TestWorkflowStep(new WorkflowStepParams(invocation.getArgument(2)))
                        .pausingAndResumingSuccessfully())
                .when(steps).getStep(anyString(), anyString(), anyMap());
    }
}
