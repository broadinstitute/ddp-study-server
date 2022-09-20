
package org.broadinstitute.dsm.model.elastic.export.parse;

import static org.junit.Assert.assertEquals;

import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dao.settings.FieldSettingsDao;
import org.broadinstitute.dsm.db.dto.settings.FieldSettingsDto;
import org.broadinstitute.dsm.model.elastic.export.generate.MappingGenerator;
import org.broadinstitute.dsm.util.TestUtil;
import org.junit.Assert;
import org.junit.Test;

public class DynamicFieldsParserTest {

    @Test
    public void parse() {
        String possibleValuesJson = "[{\"value\":\"CONSENT.completedAt\",\"type\":\"DATE\"}]";
        String displayType = "ACTIVITY_STAFF";
        DynamicFieldsParser dynamicFieldsParser = new DynamicFieldsParser();
        dynamicFieldsParser.setDisplayType(displayType);
        dynamicFieldsParser.setPossibleValuesJson(possibleValuesJson);
        dynamicFieldsParser.setHelperParser(new TypeParser());
        Map<String, Object> mapping = (Map<String, Object>) dynamicFieldsParser.parse(displayType);
        Object date = mapping.get(MappingGenerator.TYPE);
        assertEquals(TypeParser.DATE, date);
    }

    @Test
    public void checkFieldSettingsDtoByColumnName() {

        DynamicFieldsParser dynamicFieldsParser = new DynamicFieldsParser();
        dynamicFieldsParser.setHelperParser(new ValueParser());
        dynamicFieldsParser.setFieldSettingsDao(new FieldSettingsDaoMock());

        int quantity = 1_000_000;

        for (int i = 0; i < quantity; i++) {
            String fieldName = TestUtil.generateRandomString();
            dynamicFieldsParser.setFieldName(fieldName);
            dynamicFieldsParser.parse(StringUtils.EMPTY);
        }

        Assert.assertTrue(DynamicFieldsParser.fieldSettingsDtoByColumnName.size() != quantity);
    }

    private static class FieldSettingsDaoMock extends FieldSettingsDao {
        @Override
        public Optional<FieldSettingsDto> getFieldSettingsByInstanceNameAndColumnName(String instanceName, String columnName) {
            return Optional.of(new FieldSettingsDto.Builder(1).build());
        }
    }

}
