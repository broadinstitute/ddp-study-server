package org.broadinstitute.dsm.service.onchistory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

@Slf4j
public class ColumnValidatorTest {

    private static ColumnValidator validator;

    @BeforeClass
    public static void setup() {
        Map<String, List<String>> colValues = new HashMap<>();
        List<String> values = new ArrayList<>();
        values.add("attr1");
        values.add("attr2");
        colValues.put("pick1", values);

        List<String> values2 = new ArrayList<>(values);
        values2.add("attr3");
        colValues.put("pick2", values2);
        validator = new ColumnValidator(colValues);
    }

    @Test
    public void testPickLists() {
        ColumnValidatorResponse res = validator.validate("attr1", "pick1", "o");
        Assert.assertTrue(res.valid);
        res = validator.validate("attr3", "pick2", "o");
        Assert.assertTrue(res.valid);
        res = validator.validate("attr3", "pick1", "o");
        Assert.assertFalse(res.valid);
        Assert.assertTrue(res.errorMessage.contains("Invalid value for column"));
        try {
            validator.validate("attr1", "pick3", "o");
            Assert.fail("Column does not have picklist should throw");
        } catch (DsmInternalError e) {
            Assert.assertTrue(e.getMessage().contains("Possible values not found for column"));
        }
    }

    @Test
    public void testDates() {
        ColumnValidatorResponse res = validator.validate("12/5/2022", "date_col", "d");
        Assert.assertTrue(res.valid);
        Assert.assertEquals("2022-12-05", res.newValue);
        res = validator.validate("12-15-2022", "date_col", "d");
        Assert.assertTrue(res.valid);
        Assert.assertEquals("2022-12-15", res.newValue);
        res = validator.validate("2022/3/11", "date_col", "d");
        Assert.assertTrue(res.valid);
        Assert.assertEquals("2022-03-11", res.newValue);
        res = validator.validate("2022-11-20", "date_col", "d");
        Assert.assertTrue(res.valid);
        Assert.assertEquals("2022-11-20", res.newValue);
        res = validator.validate("12/2022", "date_col", "d");
        Assert.assertTrue(res.valid);
        Assert.assertEquals("2022-12", res.newValue);
        res = validator.validate("2022", "date_col", "d");
        Assert.assertTrue(res.valid);
        Assert.assertEquals("2022", res.newValue);
        res = validator.validate("abc", "date_col", "d");
        Assert.assertFalse(res.valid);
        Assert.assertTrue(res.errorMessage.contains("Invalid date"));
    }

    @Test
    public void testNumbers() {
        try {
            // bad validation type
            validator.validate("2", "int_col", "x");
            Assert.fail("Bad validation type should throw");
        } catch (DsmInternalError e) {
            Assert.assertTrue(e.getMessage().contains("Invalid column validation type"));
        }
        ColumnValidatorResponse res = validator.validate("2", "int_col", "i");
        Assert.assertTrue(res.valid);
        res = validator.validate("abc", "int_col", "i");
        Assert.assertFalse(res.valid);
        Assert.assertTrue(res.errorMessage.contains("Invalid number"));
    }
}
