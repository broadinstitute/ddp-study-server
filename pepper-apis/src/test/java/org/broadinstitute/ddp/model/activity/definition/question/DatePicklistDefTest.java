package org.broadinstitute.ddp.model.activity.definition.question;

import static org.junit.Assert.assertEquals;

import java.time.Year;

import org.junit.Test;

public class DatePicklistDefTest {

    @Test
    public void testUseAnchorYear() {
        DatePicklistDef config = new DatePicklistDef(null, 2, 50, 2018, null, true);
        assertEquals(1968, config.getStartYear());
        assertEquals(2020, config.getEndYear());
    }

    @Test
    public void testUseCurrentYear() {
        int current = Year.now().getValue();
        DatePicklistDef config = new DatePicklistDef(null, 2, 50, null, null, true);
        assertEquals(current - 50, config.getStartYear());
        assertEquals(current + 2, config.getEndYear());
    }

    @Test
    public void testDoesNotAllowFutureYears() {
        int current = Year.now().getValue();
        DatePicklistDef config = new DatePicklistDef(null, 10, 50, current, null, false);
        assertEquals(current, config.getEndYear());
    }

    @Test
    public void testUseDefaultYearsForward() {
        DatePicklistDef config = new DatePicklistDef(null, null, 50, 2000, null, true);
        assertEquals(1950, config.getStartYear());
        assertEquals(2000 + DatePicklistDef.DEFAULT_YEARS_FORWARD, config.getEndYear());
    }

    @Test
    public void testUseDefaultYearsBack() {
        DatePicklistDef config = new DatePicklistDef(null, 2, null, 2000, null, true);
        assertEquals(2000 - DatePicklistDef.DEFAULT_YEARS_BACK, config.getStartYear());
        assertEquals(2002, config.getEndYear());
    }
}
