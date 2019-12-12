package org.broadinstitute.ddp.model.activity.instance.answer;

import java.beans.ConstructorProperties;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.MonthDay;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.util.Objects;
import java.util.Optional;
import javax.validation.constraints.Positive;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.transformers.DateTimeFormatUtils;
import org.hibernate.validator.constraints.Range;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A date value composed of the individual fields, part of a date answer.
 *
 * <p> The month is limited to the inclusive range [1, 12], and the day of month is limited to the
 * inclusive range [1, 31]. The year represents the numeric year in the AD calendar scheme. Note
 * there is no year zero. </p>
 */
public class DateValue {

    private static final Logger LOG = LoggerFactory.getLogger(DateValue.class);

    @Positive
    @SerializedName("year")
    private Integer year;

    @Range(min = 1, max = 12)
    @SerializedName("month")
    private Integer month;

    @Range(min = 1, max = 31)
    @SerializedName("day")
    private Integer day;

    @ConstructorProperties({"year", "month", "day"})
    public DateValue(Integer year, Integer month, Integer day) {
        this.year = year;
        this.month = month;
        this.day = day;
    }

    public Integer getYear() {
        return year;
    }

    public static DateValue fromMillisSinceEpoch(long millisSinceEpoch) {
        // We use a Local Date Time here because EpochMillis could have time information. That time information is thrown away so ZoneId
        // is irrelevant
        LocalDateTime localDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millisSinceEpoch), ZoneId.of("UTC"));
        return new DateValue(localDateTime.getYear(), localDateTime.getMonthValue(), localDateTime.getDayOfMonth());
    }

    public Integer getMonth() {
        return month;
    }

    public Integer getDay() {
        return day;
    }

    public boolean isFullDate() {
        return year != null && month != null && day != null;
    }

    public boolean isYearMonth() {
        return year != null && month != null && day == null;
    }

    public boolean isYearDay() {
        return year != null && month == null && day != null;
    }

    public boolean isMonthDay() {
        return year == null && month != null && day != null;
    }

    public boolean isBlank() {
        return year == null && month == null && day == null;
    }

    /**
     * Convert to a LocalDate instance when a full date is available.
     *
     * @return the date if successful, otherwise empty
     */
    public Optional<LocalDate> asLocalDate() {
        try {
            if (isFullDate()) {
                return Optional.of(LocalDate.of(year, month, day));
            }
        } catch (DateTimeException e) {
            LOG.warn("Conversion to LocalDate failed", e);
        }
        return Optional.empty();
    }

    public Optional<YearMonth> asYearMonth() {
        try {
            if (isYearMonth()) {
                return Optional.of(YearMonth.of(year, month));
            }
        } catch (DateTimeException e) {
            LOG.warn("Conversion to YearMonth failed", e);
        }
        return Optional.empty();
    }

    public Optional<MonthDay> asMonthDay() {
        try {
            if (isMonthDay()) {
                return Optional.of(MonthDay.of(month, day));
            }
        } catch (DateTimeException e) {
            LOG.warn("Conversion to MonthDay failed", e);
        }
        return Optional.empty();
    }

    /**
     * Calculates the number of years between this date and another
     * @param timeUnit the time unit for the difference
     * @param otherDate the other date
     * @return number of years between this date and the other.  Positive
     *         otherDate is after this date; negative if otherDate is before this date.
     */
    public long between(ChronoUnit timeUnit, Temporal otherDate) {
        Optional<LocalDate> localDate = asLocalDate();
        if (localDate.isPresent()) {
            return timeUnit.between(localDate.get(), otherDate);
        } else {
            throw new DDPException("Incomplete date value " + toExportString() + ".  Cannot compute difference between this and "
                    + otherDate);
        }
    }

    /**
     * Verify that date fields are compatible with each other, i.e. day is within limit for a
     * certain month and this is valid for a certain year.
     *
     * @return failure message if incompatible, otherwise empty
     */
    public Optional<String> checkFieldCompatibility() {
        if (isFullDate()) {
            try {
                LocalDate.of(year, month, day);
            } catch (DateTimeException e) {
                LOG.warn("Full date check failed", e);
                String msg = "day " + day + " is invalid for month " + month + " year " + year;
                return Optional.of(msg);
            }
        } else if (isMonthDay()) {
            try {
                MonthDay.of(month, day);
            } catch (DateTimeException e) {
                LOG.warn("Month day check failed", e);
                String msg = "day " + day + " is invalid for month " + month;
                return Optional.of(msg);
            }
        }
        return Optional.empty();
    }

    @Override
    public int hashCode() {
        return Objects.hash(month, day, year);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DateValue value = (DateValue) o;
        return Objects.equals(month, value.month)
                && Objects.equals(day, value.day)
                && Objects.equals(year, value.year);
    }

    /**
     * Takes day, month, and year values and returns string in i18n formatting.
     */
    public String toExportString() {
        String exportMonth = "";
        String exportDay = "";
        String exportYear = "";

        if (year != null) {
            exportYear = year.toString();
            if (month != null || day != null) {
                exportYear += "-";
            }
        }

        if (month != null) {
            exportMonth = String.format("%02d", month);
            if (day != null) {
                exportMonth += "-";
            }
        }

        if (day != null) {
            exportDay = String.format("%02d", day);
        }

        return exportYear + exportMonth + exportDay;
    }

    /**
     * Format the date value into a date string using the default format commonly seen in the United States.
     *
     * @return the date string, or empty if value has no data
     */
    public String toDefaultDateFormat() {
        if (isFullDate()) {
            return DateTimeFormatUtils.DEFAULT_DATE_FORMATTER.format(LocalDate.of(year, month, day));
        } else if (isYearMonth()) {
            return DateTimeFormatUtils.DEFAULT_MONTH_YEAR_FORMATTER.format(YearMonth.of(year, month));
        } else if (isMonthDay()) {
            return DateTimeFormatUtils.DEFAULT_MONTH_DAY_FORMATTER.format(MonthDay.of(month, day));
        } else if (isYearDay()) {
            // FIXME: not sure how to best handle this case
            return DateTimeFormatter.ofPattern("dd/yyyy").format(LocalDate.of(year, 1, day));
        } else if (year != null) {
            return DateTimeFormatter.ofPattern("yyyy").format(YearMonth.of(year, 1));
        } else if (month != null) {
            return DateTimeFormatter.ofPattern("MM").format(MonthDay.of(month, 1));
        } else if (day != null) {
            return DateTimeFormatter.ofPattern("dd").format(MonthDay.of(1, day));
        } else {
            return ""; // FIXME: could present issues in data export
        }
    }

    @Override
    public String toString() {
        return toDefaultDateFormat();
    }
}
