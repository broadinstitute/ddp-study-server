package org.broadinstitute.ddp.util;

import java.time.Duration;
import java.time.Period;
import java.time.ZoneId;

import org.broadinstitute.ddp.exception.DDPException;

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

    public static ZoneId parseTimeZone(String givenTimeZone) {
        if (givenTimeZone != null) {
            try {
                return ZoneId.of(givenTimeZone);
            } catch (Exception e) {
                throw new DDPException("Provided timezone '" + givenTimeZone + "' is invalid", e);
            }
        } else {
            return null;
        }
    }
}
