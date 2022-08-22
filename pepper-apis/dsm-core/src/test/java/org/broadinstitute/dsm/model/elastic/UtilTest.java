package org.broadinstitute.dsm.model.elastic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.Optional;

import org.broadinstitute.dsm.db.dao.settings.FieldSettingsDao;
import org.broadinstitute.dsm.db.dto.settings.FieldSettingsDto;
import org.broadinstitute.dsm.model.elastic.converters.camelcase.CamelCaseConverter;
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
        String transformed = CamelCaseConverter.of(fieldName).convert();
        String transformed2 = CamelCaseConverter.of(fieldName2).convert();
        String transformed3 = CamelCaseConverter.of(fieldName3).convert();
        String transformed4 = CamelCaseConverter.of(fieldName4).convert();
        String transformed5 = CamelCaseConverter.of(fieldName5).convert();
        assertEquals("columnName", transformed);
        assertEquals("columnName", transformed2);
        assertEquals("column", transformed3);
        assertEquals("column", transformed4);
        assertEquals("columnName", transformed5);
    }

    @Test
    public void orElseNullInteger() {
        Optional<Integer> defaultVal = Optional.of(0);
        Optional<Integer> nonDefaultVal = Optional.of(100);
        Integer shouldBeNull = Util.orElseNull(defaultVal, 0);
        Integer shouldBeNonNull = Util.orElseNull(nonDefaultVal, 0);
        assertNull(shouldBeNull);
        assertNotNull(shouldBeNonNull);
    }

    @Test
    public void orElseNullDouble() {
        Optional<Double> defaultVal = Optional.of(0.0);
        Optional<Double> nonDefaultVal = Optional.of(42.0);
        Double shouldBeNull = Util.orElseNull(defaultVal, 0.0);
        Double shouldBeNonNull = Util.orElseNull(nonDefaultVal, 0.0);
        assertNull(shouldBeNull);
        assertNotNull(shouldBeNonNull);
    }

}
