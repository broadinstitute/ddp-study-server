package org.broadinstitute.dsm.model.elastic.export.parse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.Tissue;
import org.broadinstitute.dsm.model.elastic.export.generate.PropertyInfo;
import org.junit.Assert;
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

    @Test
    public void parseDateValue() {
        BaseParser parser = new ValueParser();
        PropertyInfo propertyInfo = new PropertyInfo(Tissue.class, true);
        parser.setPropertyInfo(propertyInfo);
        parser.setFieldName("expectedReturn");
        Object result = parser.parse("N/A");
        assertEquals(ValueParser.N_A_SYMBOLIC_DATE, result);
    }

    @Test
    public void parseStringValue() {
        BaseParser parser = new ValueParser();
        PropertyInfo propertyInfo = new PropertyInfo(Tissue.class, true);
        parser.setPropertyInfo(propertyInfo);
        parser.setFieldName("tumorType");
        Object result = parser.parse("typeOfTumor");
        assertEquals("typeOfTumor", result);
    }

    @Test
    public void parseNumericValue() {
        BaseParser parser = new ValueParser();
        PropertyInfo propertyInfo = new PropertyInfo(Tissue.class, true);
        parser.setPropertyInfo(propertyInfo);
        parser.setFieldName("oncHistoryDetailId");
        Object result = parser.parse("2022");
        assertEquals(2022L, result);
    }

    @Test
    public void parseBooleanValue() {
        BaseParser parser = new ValueParser();
        PropertyInfo propertyInfo = new PropertyInfo(MedicalRecord.class, true);
        parser.setPropertyInfo(propertyInfo);
        parser.setFieldName("duplicate");
        Object result = parser.parse("true");
        assertEquals(true, result);
    }

}
