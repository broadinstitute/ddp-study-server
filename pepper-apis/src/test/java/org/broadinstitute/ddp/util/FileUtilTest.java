package org.broadinstitute.ddp.util;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static org.junit.Assert.*;

public class FileUtilTest {

    FileUtil fileUtil = new FileUtil();

    static final String TEST_STREAM_CONTENTS = "this is\n a test\t";

    static final ByteArrayOutputStream testStream = new ByteArrayOutputStream();

    private File testFile;

    final int PARTIAL_START_INDEX = 4;

    final int PARTIAL_LENGTH = 7;

    @Before
    public void setUp() throws IOException {
        testFile = File.createTempFile(getClass().getCanonicalName(),Long.toString(System.currentTimeMillis()));
        testStream.reset();

        FileWriter fileWriter = new FileWriter(testFile);
        fileWriter.write(TEST_STREAM_CONTENTS);
        fileWriter.flush();
    }

    @Test
    public void testSendPartialFile() throws Exception {
        long numBytesSent = fileUtil.sendFile(testFile, PARTIAL_START_INDEX,PARTIAL_LENGTH,testStream);
        String expectedString = TEST_STREAM_CONTENTS.substring(PARTIAL_START_INDEX,PARTIAL_START_INDEX+PARTIAL_LENGTH);

        assertEquals(PARTIAL_LENGTH, numBytesSent);

        assertEquals("Wrong data sent from file to stream",expectedString,testStream.toString());
        // todo arz check for leaks somehow
    }

    @Test
    public void testSendPartialFileTooLong() throws Exception {
        long numBytesSent = fileUtil.sendFile(testFile, PARTIAL_START_INDEX,999999999,testStream);
        String expectedString = TEST_STREAM_CONTENTS.substring(PARTIAL_START_INDEX);

        assertEquals(TEST_STREAM_CONTENTS.length() - PARTIAL_START_INDEX, numBytesSent);

        assertEquals("Wrong data sent from file to stream",expectedString,testStream.toString());
        // todo arz check for leaks somehow
    }

    @Test
    public void testSendPartialNegativeStart() throws Exception {
        try{
            fileUtil.sendFile(testFile, -3,999999999,testStream);
            Assert.fail("Should have exploded because of negative values");
        }
        catch(IllegalArgumentException e) {
            // as expected
        }
    }

    @Test
    public void testSendPartialEnd() throws Exception {
        try{
            fileUtil.sendFile(testFile, 0,-15,testStream);
            Assert.fail("Should have exploded because of negative values");
        }
        catch(IllegalArgumentException e) {
            // as expected
        }
    }

    @Test
    public void testSendFullFile() throws Exception {
        long bytesSent = fileUtil.sendFile(testFile, testStream);

        assertEquals(TEST_STREAM_CONTENTS.getBytes().length, bytesSent);

        assertEquals("Wrong data sent from file to stream",TEST_STREAM_CONTENTS,testStream.toString());
        // todo arz check for leaks somehow
    }
}
