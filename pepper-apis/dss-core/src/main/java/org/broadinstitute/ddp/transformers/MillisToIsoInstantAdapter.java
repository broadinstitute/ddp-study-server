package org.broadinstitute.ddp.transformers;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;

import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

/**
 * Note: annotated field must have boxed {@code Long} type in order for this adapter to work. And by definition, an instant is based on UTC
 * timezone, so the annotated field should be milliseconds since UTC epoch.
 */
public class MillisToIsoInstantAdapter extends TypeAdapter<Long> {

    @Override
    public void write(JsonWriter out, Long millisSinceEpoch) throws IOException {
        if (millisSinceEpoch != null) {
            out.value(Instant.ofEpochMilli(millisSinceEpoch).toString());
        } else {
            out.nullValue();
        }
    }

    @Override
    public Long read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }

        try {
            return Instant.parse(in.nextString()).toEpochMilli();
        } catch (DateTimeParseException e) {
            throw new JsonParseException(e);
        }
    }
}
