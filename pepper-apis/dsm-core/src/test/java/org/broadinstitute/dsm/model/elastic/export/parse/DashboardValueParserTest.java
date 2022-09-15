package org.broadinstitute.dsm.model.elastic.export.parse;

import static org.junit.Assert.assertEquals;

import org.junit.BeforeClass;
import org.junit.Test;

public class DashboardValueParserTest {

    private static DashboardValueParser dashboardValueParser;

    @BeforeClass
    public static void setUp() {
        dashboardValueParser = new DashboardValueParser();
    }


    @Test
    public void parseNumber() {
        String numberInString = "0";
        assertEquals(0L, dashboardValueParser.parse(numberInString));
    }

    @Test
    public void parseBoolean() {
        String trueValue = "true";
        String falseValue = "false";
        assertEquals(true, dashboardValueParser.parse(trueValue));
        assertEquals(false, dashboardValueParser.parse(falseValue));
    }

    @Test
    public void parseDate() {
        String date = "2022-09-15";
        assertEquals(date, dashboardValueParser.parse(date));
        String dateTime = "2022-09-15T14:22:00";
        assertEquals(dateTime, dashboardValueParser.parse(dateTime));
    }

    @Test
    public void parseText() {
        String text = "random text here";
        assertEquals(text, dashboardValueParser.parse(text));
    }
}
