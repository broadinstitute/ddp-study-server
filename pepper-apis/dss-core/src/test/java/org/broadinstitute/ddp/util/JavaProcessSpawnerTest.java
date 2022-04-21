package org.broadinstitute.ddp.util;

import org.junit.Assert;
import org.junit.Test;
import org.zeroturnaround.exec.ProcessResult;

import java.util.concurrent.Future;

public class JavaProcessSpawnerTest {
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
        System.out.print(TEST_OUTPUT);
    }
}
