package org.broadinstitute.ddp.json.sendgrid;

import java.io.IOException;
import java.time.Instant;


import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

/**
 * Convert UNIX timestamp to Java Instant
 */
public class SendGridEventTypeAdapter extends TypeAdapter<Instant> {

    @Override
    public void write(JsonWriter writer, Instant instant) throws IOException {
        // not used
    }

    @Override
    public Instant read(JsonReader reader) throws IOException {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull();
            return null;
        }
        return Instant.ofEpochSecond(reader.nextInt());
    }
}
