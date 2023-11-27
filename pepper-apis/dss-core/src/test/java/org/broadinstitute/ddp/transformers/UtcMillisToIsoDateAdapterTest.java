package org.broadinstitute.ddp.transformers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.time.Instant;

import com.google.gson.Gson;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import org.junit.Test;

public class UtcMillisToIsoDateAdapterTest {
    static class TimeInMillisHolder {
        @JsonAdapter(UtcMillisToIsoDateAdapter.class)
        @SerializedName("timeInFormattedString")
        Long timeInMillis;

        Long timeInMillisNotFormatted;
    }

    @Test
    public void testGenerationOfTimeStampString() {
        TimeInMillisHolder timeHolder = new TimeInMillisHolder();
        long timeInMillis = Instant.now().toEpochMilli();
        timeHolder.timeInMillis = timeInMillis;
        timeHolder.timeInMillisNotFormatted = timeInMillis;
        Gson gson = new Gson();
        System.out.println(gson.toJson(timeHolder));

    }

    @Test
    public void testReadTimestampInMillis() {
        long theTimeValue = 1541169959582L;
        long roundedMillisTimeValue = 1541169959000L;
        String sampleJson = "{\"timeInFormattedString\":\"2018-11-02T14:45:59Z\",\"timeInMillisNotFormatted\":" + theTimeValue + "}";
        Gson gson = new Gson();
        TimeInMillisHolder readObj = gson.fromJson(sampleJson, TimeInMillisHolder.class);
        assertEquals((Long) theTimeValue, readObj.timeInMillisNotFormatted);
        assertEquals((Long) roundedMillisTimeValue, readObj.timeInMillis);
    }

    @Test
    public void testReadNullValueInFormattedMillis() {
        long theTimeValue = 1541169959582L;
        String sampleJson = "{\"timeInFormattedString\":null,\"timeInMillisNotFormatted\":" + theTimeValue + "}";
        Gson gson = new Gson();
        TimeInMillisHolder readObj = gson.fromJson(sampleJson, TimeInMillisHolder.class);
        assertNull(readObj.timeInMillis);
        assertEquals((Long) theTimeValue, (Long) readObj.timeInMillisNotFormatted);
    }

    @Test
    public void testReadUndefinedInFormattedMillis() {
        long theTimeValue = 1541169959582L;
        String sampleJson = "{\"timeInSecondsNotFormatted\":" + theTimeValue + "}";
        Gson gson = new Gson();
        TimeInMillisHolder readObj = gson.fromJson(sampleJson, TimeInMillisHolder.class);
        assertNull(readObj.timeInMillis);
    }


}
