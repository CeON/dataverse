package edu.harvard.iq.dataverse.workflow.internalspi;

import edu.harvard.iq.dataverse.persistence.workflow.Workflow;
import edu.harvard.iq.dataverse.workflow.execution.WorkflowExecutionContext;
import edu.harvard.iq.dataverse.workflow.step.Failure;
import edu.harvard.iq.dataverse.workflow.step.Success;
import edu.harvard.iq.dataverse.workflow.step.WorkflowStepParams;
import edu.harvard.iq.dataverse.workflow.step.WorkflowStepResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static edu.harvard.iq.dataverse.persistence.workflow.WorkflowMother.givenWorkflow;
import static edu.harvard.iq.dataverse.persistence.workflow.WorkflowMother.givenWorkflowStep;
import static edu.harvard.iq.dataverse.workflow.execution.WorkflowContextMother.givenWorkflowExecutionContext;
import static edu.harvard.iq.dataverse.workflow.execution.WorkflowContextMother.nextStepContextToExecute;
import static edu.harvard.iq.dataverse.workflow.internalspi.SystemProcessStep.ARGUMENTS_PARAM_NAME;
import static edu.harvard.iq.dataverse.workflow.internalspi.SystemProcessStep.COMMAND_PARAM_NAME;
import static edu.harvard.iq.dataverse.workflow.internalspi.SystemProcessStep.PROCESS_ID_PARAM_NAME;
import static edu.harvard.iq.dataverse.workflow.step.Failure.REASON_PARAM_NAME;
import static edu.harvard.iq.dataverse.workflow.step.FilesystemAccessingWorkflowStep.WORK_DIR_PARAM_NAME;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.ZoneOffset.UTC;
import static org.apache.commons.io.FileUtils.readFileToString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SystemProcessStepTest {

    long datasetId = 1L;
    Workflow workflow = givenWorkflow(1L,
            givenWorkflowStep(SystemProcessStep.STEP_ID)
    );

    WorkflowStepParams inputParams;

    Path tmpDir;

    Clock clock = Clock.fixed(Instant.parse("2020-06-01T09:10:20.00Z"), UTC);

    @BeforeEach
    void setUp() throws IOException {
        tmpDir = Files.createTempDirectory("test");
        tmpDir.toFile().deleteOnExit();
        inputParams = new WorkflowStepParams(WORK_DIR_PARAM_NAME, tmpDir.toAbsolutePath().toString());
    }

    @Test
    void shouldFailOnMissingCommand() {
        assertThatThrownBy(() -> new SystemProcessStep(inputParams))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Command parameter is required");
    }

    @Test
    void shouldRunSimpleProcessSuccessfully() throws IOException {
        // given
        inputParams = inputParams.with(COMMAND_PARAM_NAME, "echo")
                .with(ARGUMENTS_PARAM_NAME, "test");
        SystemProcessStep step = new SystemProcessStep(inputParams);
        WorkflowExecutionContext context = givenWorkflowExecutionContext(datasetId, workflow);
        context.getExecution().start("test", "127.0.1.1", clock);
        // when
        WorkflowStepResult result = step.run(nextStepContextToExecute(context));
        // then
        assertThat(result).isInstanceOf(Success.class);
        // and
        String processId = result.getData().get(PROCESS_ID_PARAM_NAME);
        assertThat(processId).isNotBlank();
        assertThat(readFileToString(step.outLogPath(processId, tmpDir).toFile(), UTF_8)).isEqualTo("test\n");
    }

    @Test
    @Disabled
    void shouldFailOnUnknownCommand() {
        // given
        inputParams = inputParams.with(COMMAND_PARAM_NAME, UUID.randomUUID().toString());
        SystemProcessStep step = new SystemProcessStep(inputParams);
        WorkflowExecutionContext context = givenWorkflowExecutionContext(datasetId, workflow);
        context.getExecution().start("test", "127.0.1.1", clock);
        // when
        WorkflowStepResult result = step.run(nextStepContextToExecute(context));
        // then
        assertThat(result).isInstanceOf(Failure.class);
        assertThat(result.getData().get(REASON_PARAM_NAME)).endsWith("No such file or directory");
    }
}