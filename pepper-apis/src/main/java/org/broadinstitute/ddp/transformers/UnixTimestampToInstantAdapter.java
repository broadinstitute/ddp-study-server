package org.broadinstitute.ddp.transformers;

import java.io.IOException;
import java.time.Instant;


import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

/**
 * Convert UNIX timestamp to Java Instant
 */
public class UnixTimestampToInstantAdapter extends TypeAdapter<Instant> {

    @Override
    public void write(JsonWriter out, Instant instant) throws IOException {
        if (instant != null) {
            out.value(instant.getEpochSecond());
        } else {
            out.nullValue();
        }
    }

    @Override
    public Instant read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        return Instant.ofEpochSecond(in.nextLong());
    }
}
