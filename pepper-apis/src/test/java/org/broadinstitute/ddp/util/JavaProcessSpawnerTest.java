package org.broadinstitute.ddp.util;

import java.util.concurrent.Future;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.exec.ProcessResult;

public class JavaProcessSpawnerTest {

    public static final String TEST_OUTPUT = "Blah!";
    private static final Logger LOG = LoggerFactory.getLogger(JavaProcessSpawner.class);

    /**
     * This is the main() that will be called by the process spawner.
     */
    public static void main(String[] args) {
        System.out.printf(TEST_OUTPUT);
    }

    @Test
    public void testIt() throws Exception {
        Future<ProcessResult> processResultFuture = JavaProcessSpawner.spawnMainInSeparateProcess(this.getClass(),
                this.getClass(), 1, null, null);
        ProcessResult processResult = processResultFuture.get();

        String stdoutOfProcess = processResult.getOutput().getUTF8();
        Assert.assertTrue(stdoutOfProcess.endsWith(TEST_OUTPUT));
    }
}
