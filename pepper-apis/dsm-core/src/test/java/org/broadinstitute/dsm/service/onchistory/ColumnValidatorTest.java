package org.broadinstitute.dsm.service.onchistory;

import java.util.Arrays;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.dto.settings.FieldSettingsDto;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

@Slf4j
public class ColumnValidatorTest {

    private static ColumnValidator validator;

    @BeforeClass
    public static void setup() {
        JsonArray pv1 = new JsonArray();
        JsonObject val1 = new JsonObject();
        val1.addProperty("value", "attr1");
        pv1.add(val1);
        JsonObject val2 = new JsonObject();
        val2.addProperty("value", "attr2");
        pv1.add(val2);
        FieldSettingsDto.Builder builder = new FieldSettingsDto.Builder(0);
        FieldSettingsDto fieldSettingsDto1 = builder.withColumnName("pick1")
                .withPossibleValues(pv1.toString()).build();

        JsonObject val3 = new JsonObject();
        val3.addProperty("value", "attr3");
        JsonArray pv2 = new JsonArray();
        pv2.add(val1);
        pv2.add(val2);
        pv2.add(val3);

        FieldSettingsDto fieldSettingsDto2 = builder.withColumnName("pick2")
                .withPossibleValues(pv2.toString()).build();

        List<FieldSettingsDto> picklists = Arrays.asList(fieldSettingsDto1, fieldSettingsDto2);
        validator = new ColumnValidator(picklists);
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
        res = validator.validate("12/2022", "date_col", "d");
        Assert.assertFalse(res.valid);
        Assert.assertTrue(res.errorMessage.contains("Invalid date"));
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
