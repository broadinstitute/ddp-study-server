package org.broadinstitute.ddp.model.activity.instance.answer;

import static java.time.temporal.ChronoUnit.YEARS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.junit.Test;

public class DateValueTest {

    @Test
    public void testEquals() {
        DateValue d1 = new DateValue(null, null, null);
        DateValue d2 = new DateValue(null, null, null);
        assertEquals(d1, d2);

        d1 = new DateValue(2018, 1, null);
        d2 = new DateValue(null, 1, 23);
        assertNotEquals(d1, d2);

        d1 = new DateValue(2018, 3, 14);
        d2 = new DateValue(2018, 3, 14);
        assertEquals(d1, d2);
    }

    @Test
    public void testBetween() {
        int year = 2019;
        int month = 3;
        int day = 19;
        DateValue d1 = new DateValue(year, month, day);
        assertEquals(0, d1.between(YEARS, d1.asLocalDate().get()));
        assertEquals(1, d1.between(YEARS, new DateValue(year + 1, month, day).asLocalDate().get()));
        assertEquals(0, d1.between(YEARS, new DateValue(year + 1, month, day - 1).asLocalDate().get()));

        assertEquals(-4, d1.between(YEARS, new DateValue(year - 5, month, day + 1).asLocalDate().get()));
        assertEquals(-5, d1.between(YEARS, new DateValue(year - 5, month, day - 1).asLocalDate().get()));

        // now try with local clock and a few different chrono types
        LocalDate now = LocalDate.now();
        DateValue nowDateValue = DateValue.fromMillisSinceEpoch(Instant.now().toEpochMilli());
        assertEquals("This may fail when running at the moment of a new day", 0, nowDateValue.between(ChronoUnit.DAYS, now));
        assertEquals("This may fail when running at the moment of the new week", 0, nowDateValue.between(ChronoUnit.WEEKS, now));
        assertEquals("This may fail when running at the moment of the new month", 0, nowDateValue.between(ChronoUnit.MONTHS, now));
        assertEquals("This may fail when running at the moment of the new year", 0, nowDateValue.between(ChronoUnit.YEARS, now));


        assertEquals(18, new DateValue(now.getYear() - 18, now.getMonthValue(), now.getDayOfMonth()).between(YEARS, now));
    }

    @Test
    public void testIsFullDate() {
        assertFalse(new DateValue(null, null, null).isFullDate());
        assertFalse(new DateValue(1, null, null).isFullDate());
        assertFalse(new DateValue(null, 1, null).isFullDate());
        assertFalse(new DateValue(null, null, 1).isFullDate());
        assertFalse(new DateValue(null, 1, 23).isFullDate());
        assertFalse(new DateValue(2014, 1, null).isFullDate());
        assertFalse(new DateValue(2014, null, 23).isFullDate());
        assertTrue(new DateValue(2014, 1, 23).isFullDate());
    }

    @Test
    public void testIsMonthDay() {
        assertFalse(new DateValue(null, null, null).isMonthDay());
        assertFalse(new DateValue(1, null, null).isMonthDay());
        assertFalse(new DateValue(null, 1, null).isMonthDay());
        assertFalse(new DateValue(null, null, 1).isMonthDay());
        assertTrue(new DateValue(null, 1, 23).isMonthDay());
        assertFalse(new DateValue(2014, 1, null).isMonthDay());
        assertFalse(new DateValue(2014, null, 23).isMonthDay());
        assertFalse(new DateValue(2014, 1, 23).isMonthDay());
    }

    @Test
    public void testAsLocalDate_notFullDate() {
        Optional<LocalDate> date = new DateValue(2014, 1, null).asLocalDate();
        assertNotNull(date);
        assertFalse(date.isPresent());
        date = new DateValue(2018, null, 23).asLocalDate();
        assertFalse(date.isPresent());
        date = new DateValue(null, 1, 23).asLocalDate();
        assertFalse(date.isPresent());
    }

    @Test
    public void testAsLocalDate_failConversion() {
        Optional<LocalDate> date = new DateValue(2018, 2, 29).asLocalDate();
        assertNotNull(date);
        assertFalse(date.isPresent());
    }

    @Test
    public void testAsLocalDate() {
        Optional<LocalDate> date = new DateValue(2018, 3, 14).asLocalDate();
        assertNotNull(date);
        assertTrue(date.isPresent());
        assertEquals("2018-03-14", date.get().toString());
    }

    @Test
    public void testCheckFieldCompatibility() {
        Optional<String> failure = new DateValue(2018, 1, 31).checkFieldCompatibility();
        assertNotNull(failure);
        assertFalse(failure.isPresent());
    }

    @Test
    public void testCheckFieldCompatibility_leapYear() {
        Optional<String> failure = new DateValue(2018, 2, 29).checkFieldCompatibility();
        assertTrue(failure.isPresent());

        failure = new DateValue(2016, 2, 29).checkFieldCompatibility();
        assertFalse(failure.isPresent());
    }

    @Test
    public void testCheckFieldCompatibility_monthDayLimit() {
        Optional<String> failure = new DateValue(null, 11, 31).checkFieldCompatibility();
        assertTrue(failure.isPresent());

        failure = new DateValue(2018, 3, 31).checkFieldCompatibility();
        assertFalse(failure.isPresent());
    }

    @Test
    public void testToDefaultDateFormat_fullDate() {
        assertEquals("03/04/2018", new DateValue(2018, 3, 4).toDefaultDateFormat());
        assertEquals("12/13/2020", new DateValue(2020, 12, 13).toDefaultDateFormat());
    }

    @Test
    public void testToDefaultDateFormat_yearMonth() {
        assertEquals("03/2018", new DateValue(2018, 3, null).toDefaultDateFormat());
        assertEquals("12/2020", new DateValue(2020, 12, null).toDefaultDateFormat());
    }

    @Test
    public void testToDefaultDateFormat_monthDay() {
        assertEquals("03/04", new DateValue(null, 3, 4).toDefaultDateFormat());
        assertEquals("12/13", new DateValue(null, 12, 13).toDefaultDateFormat());
    }

    @Test
    public void testToDefaultDateFormat_yearDay() {
        assertEquals("03/2018", new DateValue(2018, null, 3).toDefaultDateFormat());
        assertEquals("30/2020", new DateValue(2020, null, 30).toDefaultDateFormat());
    }

    @Test
    public void testToDefaultDateFormat_singleField() {
        assertEquals("2018", new DateValue(2018, null, null).toDefaultDateFormat());
        assertEquals("11", new DateValue(null, 11, null).toDefaultDateFormat());
        assertEquals("31", new DateValue(null, null, 31).toDefaultDateFormat());
    }

    @Test
    public void testToDefaultDateFormat_noFields() {
        assertEquals("", new DateValue(null, null, null).toDefaultDateFormat());
    }
}
