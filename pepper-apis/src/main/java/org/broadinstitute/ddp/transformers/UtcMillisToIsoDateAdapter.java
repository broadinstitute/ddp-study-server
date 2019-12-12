package org.broadinstitute.ddp.transformers;

import static org.broadinstitute.ddp.transformers.DateTimeFormatUtils.UTC_ISO8601_DATE_TIME_FOMATTER;
import static org.broadinstitute.ddp.transformers.DateTimeFormatUtils.convertUtcMillisToUtcIso8601String;

import java.io.IOException;
import java.time.Instant;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

public class UtcMillisToIsoDateAdapter extends TypeAdapter<Long> {
    @Override
    public void write(JsonWriter out, Long millisSinceEpoch) throws IOException {
        if (millisSinceEpoch != null) {
            out.value(convertUtcMillisToUtcIso8601String(millisSinceEpoch));
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
        return Instant.from(UTC_ISO8601_DATE_TIME_FOMATTER.parse(in.nextString())).toEpochMilli();
    }

}
