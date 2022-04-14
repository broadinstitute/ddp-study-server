package org.broadinstitute.ddp.transformers;

import static org.junit.Assert.assertEquals;

import java.time.Instant;


import com.google.gson.Gson;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import org.junit.Test;

public class UnixTimestampToInstantAdapterTest {

    static class TimestampHolder {
        @JsonAdapter(UnixTimestampToInstantAdapter.class)
        @SerializedName("timestamp")
        Instant timestamp;
    }

    @Test
    public void testGenerationOfTimestampString() {
        TimestampHolder timeHolder = new TimestampHolder();
        Instant instant = Instant.now();
        timeHolder.timestamp = instant;
        Gson gson = new Gson();
        String json = gson.toJson(timeHolder);
        TimestampHolder readObj = gson.fromJson(json, TimestampHolder.class);
        assertEquals(instant.getEpochSecond(), readObj.timestamp.getEpochSecond());
    }

    @Test
    public void testReadTimestampInMillis() {
        long theTimeValue = 1513299569;
        String sampleJson = "{\"timestamp\":1513299569}";
        Gson gson = new Gson();
        TimestampHolder readObj = gson.fromJson(sampleJson, TimestampHolder.class);
        assertEquals(theTimeValue, readObj.timestamp.getEpochSecond());
    }
}
