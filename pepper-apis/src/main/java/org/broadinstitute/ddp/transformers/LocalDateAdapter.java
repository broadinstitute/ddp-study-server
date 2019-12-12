package org.broadinstitute.ddp.transformers;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

/**
 * Adaptor for LocalDate objects. Use the `nullSafe()` wrapper to make this handle nulls.
 */
public class LocalDateAdapter extends TypeAdapter<LocalDate> {
    @Override
    public void write(JsonWriter writer, LocalDate date) throws IOException {
        writer.value(date.toString());
    }

    @Override
    public LocalDate read(JsonReader reader) throws IOException {
        try {
            return LocalDate.parse(reader.nextString());
        } catch (DateTimeParseException e) {
            throw new JsonParseException(e);
        }
    }
}
