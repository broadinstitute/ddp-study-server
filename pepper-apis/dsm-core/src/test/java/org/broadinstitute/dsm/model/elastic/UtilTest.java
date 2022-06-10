package org.broadinstitute.dsm.model.elastic;

import static org.junit.Assert.assertEquals;

import java.util.Optional;

import org.broadinstitute.dsm.db.dao.settings.FieldSettingsDao;
import org.broadinstitute.dsm.db.dto.settings.FieldSettingsDto;
import org.junit.Assert;
import org.junit.Test;

public class UtilTest {


    public static FieldSettingsDao getMockFieldSettingsDao() {
        class FieldSettingsDaoMock extends FieldSettingsDao {

            @Override
            public Optional<FieldSettingsDto> getFieldSettingsByInstanceNameAndColumnName(String instanceName, String columnName) {
                FieldSettingsDto.Builder builder = new FieldSettingsDto.Builder(0);
                if ("BOOLEAN_VAL".equals(columnName)) {
                    builder.withDisplayType("CHECKBOX");
                } else if ("LONG_VAL".equals(columnName)) {
                    builder.withDisplayType("NUMBER");
                }
                return Optional.of(builder.build());
            }
        }

        return new FieldSettingsDaoMock();
    }

    @Test
    public void underscoresToCamelCase() {
        String fieldName = "column_name";
        String fieldName2 = "COLUMN_NAME";
        String fieldName3 = "column";
        String fieldName4 = "COLUMN";
        String fieldName5 = "columnName";
        String transformed = Util.underscoresToCamelCase(fieldName);
        String transformed2 = Util.underscoresToCamelCase(fieldName2);
        String transformed3 = Util.underscoresToCamelCase(fieldName3);
        String transformed4 = Util.underscoresToCamelCase(fieldName4);
        String transformed5 = Util.underscoresToCamelCase(fieldName5);
        assertEquals("columnName", transformed);
        assertEquals("columnName", transformed2);
        assertEquals("column", transformed3);
        assertEquals("column", transformed4);
        assertEquals("columnName", transformed5);
    }

    @Test
    public void normalizeOptionalInteger() {
        Optional<Integer> one = Optional.of(1);
        Optional<Integer> integerDefaultValue = Optional.of(0);
        Assert.assertEquals(1, (Object) Util.orElseNull(one, 0));
        Assert.assertNull(Util.orElseNull(integerDefaultValue, 0));
    }

    @Test
    public void normalizeOptionalDouble() {
        Optional<Double> one = Optional.of(1.0);
        Optional<Double> doubleDefaultValue = Optional.of(0.0);
        Assert.assertEquals(1.0, (Object) Util.orElseNull(one, 0.0));
        Assert.assertNull(Util.orElseNull(doubleDefaultValue, 0.0));
    }
}
