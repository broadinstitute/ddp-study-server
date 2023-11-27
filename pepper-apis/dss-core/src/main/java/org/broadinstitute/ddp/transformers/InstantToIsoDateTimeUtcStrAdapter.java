package org.broadinstitute.ddp.transformers;

import java.io.IOException;
import java.time.Instant;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

/**
 * Adapter to convert an instant to/from a string. This is null-safe so no need to call `nullSafe()` wrapper.
 */
public class InstantToIsoDateTimeUtcStrAdapter extends TypeAdapter<Instant> {

    @Override
    public Instant read(JsonReader jsonReader) throws IOException {
        if (jsonReader.peek() == JsonToken.NULL) {
            jsonReader.nextNull();
            return null;
        } else {
            return Instant.parse(jsonReader.nextString());
        }
    }

    @Override
    public void write(JsonWriter jsonWriter, Instant instant) throws IOException {
        if (instant == null) {
            jsonWriter.nullValue();
        } else {
            jsonWriter.value(instant.toString());
        }
    }
}
