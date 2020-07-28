package org.broadinstitute.ddp.util;

import java.time.Duration;
import java.time.Period;

public class DateTimeUtils {

    public static Period parseTimeAmountPeriod(String timeAmount) {
        if (timeAmount == null || timeAmount.isBlank() || timeAmount.startsWith("PT")) {
            return Period.ZERO;
        }
        if (timeAmount.contains("T")) {
            timeAmount = timeAmount.split("T")[0];
        }
        return Period.parse(timeAmount);
    }

    public static Duration parseTimeAmountDuration(String timeAmount) {
        if (timeAmount == null || timeAmount.isBlank() || !timeAmount.contains("T")) {
            return Duration.ZERO;
        }
        if (!timeAmount.startsWith("PT")) {
            timeAmount = "PT" + timeAmount.split("T")[1];
        }
        return Duration.parse(timeAmount);
    }
}
