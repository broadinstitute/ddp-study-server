package org.broadinstitute.ddp.util;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class TimestampUtil {

    /**
     * Returns a {@link Timestamp} truncated to {@link ChronoUnit#MILLIS}
     * to avoid rounding/truncation issues.
     */
    public static Timestamp now() {
        return new Timestamp(Instant.now().truncatedTo(ChronoUnit.MILLIS).toEpochMilli());
    }
}
