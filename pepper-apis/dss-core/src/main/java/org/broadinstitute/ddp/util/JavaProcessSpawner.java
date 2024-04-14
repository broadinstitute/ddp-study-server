package org.broadinstitute.ddp.util;

import lombok.extern.slf4j.Slf4j;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.stream.slf4j.Slf4jStream;

import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Sometimes you just want to spawn a new java process, using the current classpath
 * and version of java.  This utility attempts to provide a way to do that
 * in a cross-platform way, using standard java system properties.
 */
@Slf4j
public class JavaProcessSpawner {
    /**
     * Starts up a wholly separate java process.  May not be suitable for production code,
     * but good enough for spinning up simulators for testing
     *
     * @param classWithMainMethod The main class you want to run
     * @param sendLogsTo          class that will appear in the logs as the source of log statements
     * @param bootTimeInMillis   initial pause (in seconds), needed because spinning up
     *                            an app takes a moment
     * @return The future result of the job
     * @throws IOException if there is an IOException
     */
    public static Future<ProcessResult> spawnMainInSeparateProcess(Class classWithMainMethod, Class sendLogsTo, long bootTimeInMillis)
                throws IOException {
        if (classWithMainMethod == null) {
            throw new IllegalArgumentException("class to spawn is required");
        }
        if (sendLogsTo == null) {
            throw new IllegalArgumentException("logging class required.  Otherwise your logs will go into outer space.");
        }
        if (bootTimeInMillis < 1) {
            log.warn("With a boot wait time of " + bootTimeInMillis + ", clients of " + classWithMainMethod + " may discover that the"
                    + " process the need has not been started yet.");
        }

        String classpath = System.getProperty("java.class.path");
        String separator = System.getProperty("file.separator");
        String pathToJava = System.getProperty("java.home") + separator + "bin" + separator + "java";

        Future<ProcessResult> processResultFuture = new ProcessExecutor()
                .command(pathToJava, "-cp", classpath, classWithMainMethod.getName())
                .redirectOutput(Slf4jStream.of(sendLogsTo).asInfo())
                .redirectError(Slf4jStream.of(sendLogsTo).asError())
                .readOutput(true)
                .destroyOnExit().timeout(bootTimeInMillis, TimeUnit.MILLISECONDS).start().getFuture();

        try {
            log.info("Pausing for " + bootTimeInMillis + " ms while starting " + classWithMainMethod.getName());
            Thread.sleep(bootTimeInMillis);
        } catch (InterruptedException e) {
            log.info("Spawned java class " + classWithMainMethod + " interrupted", e);
        }
        return processResultFuture;
    }
}
