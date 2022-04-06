package org.broadinstitute.ddp.transformers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.gson.Gson;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import org.junit.Test;

public class UtcSecondsToIsoDateAdapterTest {
    static class TimeInSecondsHolder {
        @JsonAdapter(UtcSecondsToIsoDateAdapter.class)
        @SerializedName("timeInFormattedString")
        public Long timeInSeconds;

        public Long timeInSecondsNotFormatted;
    }

    @Test
    public void testGenerationOfTimeStampString() {
        TimeInSecondsHolder timeHolder = new TimeInSecondsHolder();
        long timeInSeconds = System.currentTimeMillis() / 1000;
        timeHolder.timeInSeconds = timeInSeconds;
        timeHolder.timeInSecondsNotFormatted = timeInSeconds;
        Gson gson = new Gson();
        System.out.println(gson.toJson(timeHolder));

    }

    @Test
    public void testReadTimestampInSeconds() {
        long theTimeValue = 1541088008;
        String sampleJson = "{\"timeInFormattedString\":\"2018-11-01T16:00:08Z\",\"timeInSecondsNotFormatted\":" + theTimeValue + "}";
        Gson gson = new Gson();
        TimeInSecondsHolder readObj = gson.fromJson(sampleJson, TimeInSecondsHolder.class);
        assertEquals((Long) theTimeValue, (Long) readObj.timeInSeconds);
        assertEquals((Long) theTimeValue, (Long) readObj.timeInSecondsNotFormatted);
    }

    @Test
    public void testReadNullValueInFormattedSeconds() {
        long theTimeValue = 1541088008;
        String sampleJson = "{\"timeInFormattedString\":null,\"timeInSecondsNotFormatted\":" + theTimeValue + "}";
        Gson gson = new Gson();
        TimeInSecondsHolder readObj = gson.fromJson(sampleJson, TimeInSecondsHolder.class);
        assertNull(readObj.timeInSeconds);
        assertEquals((Long) theTimeValue, (Long) readObj.timeInSecondsNotFormatted);
    }

    @Test
    public void testReadUndefinedInFormattedSeconds() {
        long theTimeValue = 1541088008;
        String sampleJson = "{\"timeInSecondsNotFormatted\":" + theTimeValue + "}";
        Gson gson = new Gson();
        TimeInSecondsHolder readObj = gson.fromJson(sampleJson, TimeInSecondsHolder.class);
        assertNull(readObj.timeInSeconds);
        assertEquals((Long) theTimeValue, (Long) readObj.timeInSecondsNotFormatted);
    }


}
