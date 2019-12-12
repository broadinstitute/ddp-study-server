package org.broadinstitute.ddp.transformers;

import static org.broadinstitute.ddp.transformers.DateTimeFormatUtils.UTC_ISO8601_DATE_TIME_FOMATTER;
import static org.broadinstitute.ddp.transformers.DateTimeFormatUtils.convertUtcMillisToUtcIso8601String;

import java.io.IOException;
import java.time.Instant;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;


public class UtcSecondsToIsoDateAdapter extends TypeAdapter<Long> {

    @Override
    public void write(JsonWriter out, Long secondsSinceEpoch) throws IOException {
        if (secondsSinceEpoch != null) {
            out.value(convertUtcMillisToUtcIso8601String(secondsSinceEpoch * 1000));
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
        return Instant.from(UTC_ISO8601_DATE_TIME_FOMATTER.parse(in.nextString())).getEpochSecond();
    }
}
