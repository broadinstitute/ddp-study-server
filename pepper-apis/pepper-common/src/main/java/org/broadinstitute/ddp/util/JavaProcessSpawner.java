package org.broadinstitute.ddp.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.stream.slf4j.Slf4jStream;

/**
 * Sometimes you just want to spawn a new java process, using the current classpath
 * and version of java.  This utility attempts to provide a way to do that
 * in a cross-platform way, using standard java system properties.
 */
public class JavaProcessSpawner {

    private static final Logger LOG = LoggerFactory.getLogger(JavaProcessSpawner.class);

    /**
     * Starts up a wholly separate java process.  May not be suitable for production code,
     * but good enough for spinning up simulators for testing
     *
     * @param classWithMainMethod The main class you want to run
     * @param sendLogsTo          class that will appear in the logs as the source of log statements
     * @param bootTimeInSeconds   initial pause (in seconds), needed because spinning up
     *                            an app takes a moment
     * @param dashDVars           key/value pairs of -D variables
     * @return The future result of the job
     */
    public static Future<ProcessResult> spawnMainInSeparateProcess(Class classWithMainMethod,
                                                                   Class sendLogsTo,
                                                                   int bootTimeInSeconds,
                                                                   Integer debugPort,
                                                                   Map<String, String> dashDVars) throws IOException {
        if (classWithMainMethod == null) {
            throw new IllegalArgumentException("class to spawn is required");
        }
        if (sendLogsTo == null) {
            throw new IllegalArgumentException("logging class required.  "
                    + "Otherwise your logs will go into outer space.");
        }
        if (bootTimeInSeconds < 1) {
            LOG.warn("With a boot wait time of " + bootTimeInSeconds + ", clients of " + classWithMainMethod
                    + " may discover that the process the need has not been started yet.");
        }

        String classpath = System.getProperty("java.class.path");
        String separator = System.getProperty("file.separator");
        String pathToJava = System.getProperty("java.home") + separator + "bin" + separator + "java";
        StringBuilder envVarsBuilder = new StringBuilder();
        String debugLine = "";
        if (debugPort != null) {
            debugLine = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=" + debugPort + " ";
        }

        List<String> commandLineStrings = new ArrayList<>();
        commandLineStrings.add(pathToJava);
        if (dashDVars != null) {
            for (Map.Entry<String, String> envVar : dashDVars.entrySet()) {
                commandLineStrings.add("-D" + envVar.getKey() + "=" + envVar.getValue());
            }
        }
        commandLineStrings.add("-cp");
        commandLineStrings.add(classpath);
        if (debugPort != null) {
            commandLineStrings.add(debugLine);
            LOG.info("Launching " + classWithMainMethod.getName() + " with debug port on "
                    + debugPort + "; waiting for debug connection...");
        }
        commandLineStrings.add(classWithMainMethod.getName());

        Future<ProcessResult> processResultFuture = new ProcessExecutor()
                .command(commandLineStrings)
                .redirectOutput(Slf4jStream.of(sendLogsTo).asInfo())
                .redirectError(Slf4jStream.of(sendLogsTo).asError())
                .readOutput(true)
                .destroyOnExit().timeout(bootTimeInSeconds, TimeUnit.SECONDS).start().getFuture();

        try {
            LOG.info("Pausing for " + bootTimeInSeconds + " seconds while starting " + classWithMainMethod.getName());
            Thread.sleep(bootTimeInSeconds * 1000);
        } catch (InterruptedException e) {
            LOG.info("Spawned java class " + classWithMainMethod + " interrupted", e);
        }
        return processResultFuture;
    }
}
