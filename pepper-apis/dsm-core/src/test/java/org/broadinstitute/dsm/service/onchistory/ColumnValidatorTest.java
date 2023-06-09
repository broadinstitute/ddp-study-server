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
        StringBuilder sb = new StringBuilder();
        boolean valid = validator.validate("attr1", "pick1", "o", sb);
        Assert.assertTrue(valid);
        valid = validator.validate("attr3", "pick2", "o", sb);
        Assert.assertTrue(valid);
        valid = validator.validate("attr3", "pick1", "o", sb);
        Assert.assertFalse(valid);
        Assert.assertTrue(sb.toString().contains("Invalid value for column"));
        try {
            validator.validate("attr1", "pick3", "o", sb);
            Assert.fail("Column does not have picklist should throw");
        } catch (DsmInternalError e) {
            Assert.assertTrue(e.getMessage().contains("Possible values not found for column"));
        }
    }

    @Test
    public void testDates() {
        StringBuilder sb = new StringBuilder();
        boolean valid = validator.validate("12/5/2022", "date_col", "d", sb);
        Assert.assertTrue(valid);
        valid = validator.validate("2022/12/15", "date_col", "d", sb);
        Assert.assertTrue(valid);
        valid = validator.validate("12/2022", "date_col", "d", sb);
        Assert.assertFalse(valid);
        Assert.assertTrue(sb.toString().contains("Invalid date"));
        sb.setLength(0);
        valid = validator.validate("abc", "date_col", "d", sb);
        Assert.assertFalse(valid);
        Assert.assertTrue(sb.toString().contains("Invalid date"));
    }

    @Test
    public void testNumbers() {
        StringBuilder sb = new StringBuilder();
        try {
            // bad validation type
            validator.validate("2", "int_col", "x", sb);
            Assert.fail("Bad validation type should throw");
        } catch (DsmInternalError e) {
            Assert.assertTrue(e.getMessage().contains("Invalid column validation type"));
        }
        boolean valid = validator.validate("2", "int_col", "i", sb);
        Assert.assertTrue(valid);
        valid = validator.validate("abc", "int_col", "i", sb);
        Assert.assertFalse(valid);
        Assert.assertTrue(sb.toString().contains("Invalid number"));
    }
}
