package org.broadinstitute.dsm.model.elastic.export.parse;

import static org.broadinstitute.dsm.model.elastic.export.parse.TypeParser.TEXT_KEYWORD_MAPPING;
import static org.junit.Assert.*;

import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.model.elastic.export.generate.BaseGenerator;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class BaseParserTest {

    static BaseParser valueParser;

    @BeforeClass
    public static void setUp() {
        valueParser = new ValueParser();
    }

    @Test
    public void isBoolean() {
        String falseValue = "'0'";
        String trueValue = "'1'";
        assertTrue(valueParser.isBoolean(falseValue));
        assertTrue(valueParser.isBoolean(trueValue));
    }

    @Test
    public void convertString() {
        String value = "'15'";
        String value1 = "'ASCProject_PZ8GJC_SALIVA'";
        String value2 = "'2015-01-01'";
        String convertedValue = valueParser.convertString(value);
        String convertedValue1 = valueParser.convertString(value1);
        String convertedValue2 = valueParser.convertString(value2);
        Assert.assertEquals("15", convertedValue);
        Assert.assertEquals("ASCProject_PZ8GJC_SALIVA", convertedValue1);
        Assert.assertEquals("2015-01-01", convertedValue2);
    }

}