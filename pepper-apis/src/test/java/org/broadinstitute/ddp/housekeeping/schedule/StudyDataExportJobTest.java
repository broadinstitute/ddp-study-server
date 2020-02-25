package org.broadinstitute.ddp.housekeeping.schedule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.io.IOUtils;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;

public class StudyDataExportJobTest {

    @Test
    public void testStreamingOfData() {
        StudyDataExportJob job = mock(StudyDataExportJob.class);

        int numberOfChunks = 100;
        int chunkSize = 1023; //yes. 1023
        char[] testData = new char[chunkSize];
        Arrays.fill(testData, 'x');
        //Simulate running the DataExporter
        //mocking the method that does the writing and the one that does reading. Writing first:
        when(job.buildExportToCsvRunnable(any(StudyDto.class), isNull(), any(Writer.class), anyList(), anyList())).thenAnswer(
                (InvocationOnMock invocation) ->
                        (Runnable) () -> {
                            Writer writer = invocation.getArgument(2);
                            for (int i = 0; i < numberOfChunks; i++) {
                                try {
                                    writer.write(testData);
                                } catch (IOException e) {
                                    fail("Error writing test data");
                                    throw new RuntimeException(e);
                                }
                                // To make this a little more interesting!
                                try {
                                    Thread.sleep(5);
                                } catch (InterruptedException e) {
                                    fail("Thread sleep failed");
                                    throw new RuntimeException(e);
                                }

                            }
                            try {
                                // turns out we need to close the writer or we don't see the
                                // end of stream on the reader. Need to do this in actual code too!
                                writer.close();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }

        );
        AtomicBoolean saveToGoogleBucketRanOk = new AtomicBoolean(false);

        // We read the data back. Presumably this is what the Google Bucket would be doing
        when(job.saveToGoogleBucket(any(InputStream.class), anyString(), anyString(), any()))
                .thenAnswer((InvocationOnMock invocation) -> {
                    InputStream stream = invocation.getArgument(0);
                    char[] charsRead = IOUtils.toCharArray(stream, "utf-8");

                    // our asserts. Did we get all the data?
                    assertEquals(numberOfChunks * chunkSize, charsRead.length);

                    // and is all the data the same as what we sent?
                    for (char currentChar : charsRead) {
                        if (currentChar != 'x') {
                            fail("Found a character in input stream that did not match the output stream");
                        }
                    }
                    saveToGoogleBucketRanOk.set(true);
                    return true;
                });

        // minimal studyDto that will make this work. Really irrelevant for our tests except needed to avoid null pointer exceptions.
        StudyDto testStudyDto = new StudyDto(123, "theguid", "studyname", null,
                "http://blah.boo.com", 2, 1, null, false, null, true);

        // run the real thing when we call this
        when(job.exportStudyToGoogleBucket(testStudyDto, null, null, List.of(), List.of())).thenCallRealMethod();

        // run it!
        job.exportStudyToGoogleBucket(testStudyDto, null, null, List.of(), List.of());
        assertTrue(saveToGoogleBucketRanOk.get());
    }
}
