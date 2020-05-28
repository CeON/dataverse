package edu.harvard.iq.dataverse.workflow.internalspi;

import edu.harvard.iq.dataverse.workflow.WorkflowContext;
import edu.harvard.iq.dataverse.workflow.step.Failure;
import edu.harvard.iq.dataverse.workflow.step.Success;
import edu.harvard.iq.dataverse.workflow.step.WorkflowStep;
import edu.harvard.iq.dataverse.workflow.step.WorkflowStepResult;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.createTempDirectory;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static org.apache.commons.collections4.ListUtils.union;

public class SystemProcessStep implements WorkflowStep {

    private static final Logger log = LoggerFactory.getLogger(SystemProcessStep.class);

    static final String STEP_ID = "system-process";

    /**
     * The binary to run. Used as is, so consider absolute path if necessary.
     */
    public static final String COMMAND_PARAM_NAME = "command";
    /**
     * Comma separated list of command arguments.
     */
    public static final String ARGUMENTS_PARAM_NAME = "arguments";
    /**
     * Directory to run the command in. If not defined a temporary directory will be created for that purpose.
     */
    public static final String WORK_DIR_PARAM_NAME = "workDir";

    private final String command;
    private final List<String> arguments;
    private final String workDir;

    private ExecutorService executorService;

    public SystemProcessStep(Map<String, String> inputParams) {
        command = ofNullable(inputParams.get(COMMAND_PARAM_NAME))
                .orElseThrow(() -> new IllegalArgumentException("Command parameter is required"));
        arguments = ofNullable(inputParams.get(ARGUMENTS_PARAM_NAME))
                .map(args -> asList(args.split(",")))
                .orElseGet(Collections::emptyList);
        workDir = inputParams.get(WORK_DIR_PARAM_NAME);
    }

    @Override
    public WorkflowStepResult run(WorkflowContext context) {
        try {
            executorService = newFixedThreadPool(2);

            Path workDir = getWorkDir();
//            Path outLog = workDir.resolve("out.log");
//            Path errLog = workDir.resolve("err.log");

            ProcessBuilder builder = new ProcessBuilder(union(singletonList(command), arguments))
                    .directory(workDir.toFile())
//                    .redirectOutput(outLog.toFile())
//                    .redirectError(errLog.toFile())
                    ;

            Process process = builder.start();
            Future<String> stdOut = executorService.submit(streamGobbler(process::getInputStream));
            Future<String> stdErr = executorService.submit(streamGobbler(process::getErrorStream));
            int exitCode = process.waitFor();

            Map<String, String> outputParams = new HashMap<>();
            outputParams.put("stdOut", stdOut.get());
            outputParams.put("stdErr", stdErr.get());
            outputParams.put("exitCode", Integer.toString(exitCode));

            return new Success(outputParams);
        } catch (Exception e) {
            log.error("Failed system process workflow step", e);
            return new Failure(e.getMessage());
        } finally {
            executorService.shutdown();
        }
    }

    private Path getWorkDir() throws IOException {
        if (workDir != null) {
            return createDirectories(Paths.get(workDir));
        } else {
            return createTempDirectory("mxrdr");
        }
    }

    private Callable<String> streamGobbler(Supplier<InputStream> inputSupplier) {
        return () -> {
            try (InputStream in = inputSupplier.get()) {
                return IOUtils.toString(in, UTF_8);
            }
        };
    }

    @Override
    public WorkflowStepResult resume(WorkflowContext context, Map<String, String> internalData, String externalData) {
        return null;
    }

    @Override
    public void rollback(WorkflowContext context, Failure reason) {
    }
}
