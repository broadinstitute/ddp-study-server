package org.broadinstitute.ddp.util;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.exec.ProcessResult;

import java.util.concurrent.Future;

public class JavaProcessSpawnerTest {

    private static final Logger logger = LoggerFactory.getLogger(JavaProcessSpawner.class);

    public static final String TEST_OUTPUT = "Blah!";

    @Test
    public void testIt() throws Exception {
        Future<ProcessResult> processResultFuture = JavaProcessSpawner.spawnMainInSeparateProcess(this.getClass(), this.getClass(), 1);
        ProcessResult processResult = processResultFuture.get();

        String stdoutOfProcess = processResult.getOutput().getUTF8();
        Assert.assertTrue(stdoutOfProcess.endsWith(TEST_OUTPUT));
    }

    /**
     * This is the main() that will be called by the process spawner
     */
    public static void main(String[] args) {
        System.out.printf(TEST_OUTPUT);
    }
}
