package org.broadinstitute.ddp.transformers;

import static org.broadinstitute.ddp.transformers.DateTimeFormatUtils.UTC_ISO8601_DATE_TIME_FORMATTER;

import java.io.IOException;
import java.time.LocalDateTime;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

/**
 * Adaptor for LocalDate objects. Use the `nullSafe()` wrapper to make this handle nulls.
 */
public class LocalDateTimeAdapter extends TypeAdapter<LocalDateTime> {
    @Override
    public void write(JsonWriter writer, LocalDateTime dateTime) throws IOException {
        if (dateTime == null) {
            writer.nullValue();
        } else {
            writer.value(UTC_ISO8601_DATE_TIME_FORMATTER.format(dateTime));
        }
    }

    @Override
    public LocalDateTime read(final JsonReader jsonReader) throws IOException {
        if (jsonReader.peek() == JsonToken.NULL) {
            jsonReader.nextNull();
            return null;
        } else {
            return LocalDateTime.parse(jsonReader.nextString(), UTC_ISO8601_DATE_TIME_FORMATTER);
        }
    }

}
