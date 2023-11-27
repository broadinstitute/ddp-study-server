package org.broadinstitute.dsm.model.elastic.filter.splitter;

import org.broadinstitute.dsm.model.elastic.export.parse.BaseParser;
import org.broadinstitute.dsm.model.elastic.filter.FilterParser;
import org.junit.Assert;
import org.junit.Test;

public class FilterParserTest {

    @Test
    public void parse() {

        String trueValue = "'1'";
        String falseValue = "NOT'1'";
        String str = "'string'";
        String number = "'5'";
        String date = "'1999-05-22'";
        String number2 = "5";

        BaseParser filterParser = new FilterParser();
        Assert.assertEquals(true, filterParser.parse(trueValue));
        Assert.assertEquals(false, filterParser.parse(falseValue));
        Assert.assertEquals("string", filterParser.parse(str));
        Assert.assertEquals("5", filterParser.parse(number));
        Assert.assertEquals(5L, filterParser.parse(number2));
        Assert.assertEquals("1999-05-22", filterParser.parse(date));
    }

    @Test
    public void parseValueArray() {

        String trueValue = "'1'";
        String falseValue = "NOT'1'";
        String str = "'string'";
        String number = "'5'";
        String date = "'1999-05-22'";
        String number2 = "5";

        String[] valuesArray = new String[] {trueValue, falseValue, str, number, date, number2};

        BaseParser filterParser = new FilterParser();
        Object[] parsedObjecs = filterParser.parse(valuesArray);

        Assert.assertEquals(true, parsedObjecs[0]);
        Assert.assertEquals(false, parsedObjecs[1]);
        Assert.assertEquals("string", parsedObjecs[2]);
        Assert.assertEquals("5", parsedObjecs[3]);
        Assert.assertEquals("1999-05-22", parsedObjecs[4]);
        Assert.assertEquals(5L, parsedObjecs[5]);
    }
}
