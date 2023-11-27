package org.broadinstitute.ddp.transformers;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;

public class DateTimeFormatUtils {

    public static final String DEFAULT_DATE_PATTERN = "MM/dd/yyyy";
    private static final String MM_YYYY_SLASH_SEPARATED_FORMAT = "%d/%d";
    private static final String MM_DD_YYYY_DASH_SEPARATED_FORMAT = "%02d-%02d-%d";
    private static final String YYYY_MM_DASH_SEPARATED_FORMAT = "%04d-%02d";
    private static final String YYYY_MM_DD_DASH_SEPARATED_FORMAT = "%04d-%02d-%02d";

    public static final DateTimeFormatter UTC_ISO8601_DATE_TIME_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;
    public static final DateTimeFormatter DEFAULT_DATE_FORMATTER = DateTimeFormatter.ofPattern(DEFAULT_DATE_PATTERN);
    public static final DateTimeFormatter DEFAULT_MONTH_YEAR_FORMATTER = DateTimeFormatter.ofPattern("MM/yyyy");
    public static final DateTimeFormatter DEFAULT_MONTH_DAY_FORMATTER = DateTimeFormatter.ofPattern("MM/dd");

    public static ZonedDateTime convertUtcMillisToUtcZonedDateTime(long timeInMillis) {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(timeInMillis), ZoneId.of("UTC"));
    }

    public static String convertUtcMillisToDefaultDateString(long timeInMillis) {
        return DEFAULT_DATE_FORMATTER.format(convertUtcMillisToUtcZonedDateTime(timeInMillis));
    }

    public static String convertUtcMillisToUtcIso8601String(long timeInMillis) {
        return UTC_ISO8601_DATE_TIME_FORMATTER.format(convertUtcMillisToUtcZonedDateTime(timeInMillis));
    }

    // DSM-specific format
    public static String convertDateToSlashSeparatedDsmFormat(DateValue dateValue) {
        checkDateValue(dateValue);
        return String.format(MM_YYYY_SLASH_SEPARATED_FORMAT, dateValue.getMonth(), dateValue.getYear());
    }

    // DSM-specific format
    public static String convertDateToDashSeparatedDsmFormat(DateValue dateValue) {
        checkDateValue(dateValue);
        return String.format(MM_DD_YYYY_DASH_SEPARATED_FORMAT, dateValue.getMonth(), dateValue.getDay(), dateValue.getYear());
    }

    public static String convertDateToYearMonthDashSeparatedFormat(DateValue dateValue) {
        checkDateValue(dateValue);
        return String.format(YYYY_MM_DASH_SEPARATED_FORMAT, dateValue.getYear(), dateValue.getMonth());
    }

    public static String convertDateToYearMonthDayDashSeparatedFormat(DateValue dateValue) {
        checkDateValue(dateValue);
        return String.format(YYYY_MM_DD_DASH_SEPARATED_FORMAT, dateValue.getYear(), dateValue.getMonth(), dateValue.getDay());
    }

    public static void checkDateValue(DateValue dateValue) {
        if (dateValue == null) {
            throw new IllegalArgumentException("dateValue cannot be NULL");
        }
    }

    public static class IllegalDateFormatException extends IllegalArgumentException {
        public IllegalDateFormatException(String message) {
            super(message);
        }
    }
}
