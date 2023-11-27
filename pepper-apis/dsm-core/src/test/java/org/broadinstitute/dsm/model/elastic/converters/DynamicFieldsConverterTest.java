package org.broadinstitute.dsm.model.elastic.converters;

import static org.junit.Assert.assertEquals;

import java.util.Map;

import org.broadinstitute.dsm.db.dao.settings.FieldSettingsDao;
import org.broadinstitute.dsm.model.elastic.UtilTest;
import org.broadinstitute.dsm.model.elastic.export.parse.DynamicFieldsParser;
import org.broadinstitute.dsm.model.elastic.export.parse.ValueParser;
import org.junit.Before;
import org.junit.Test;

public class DynamicFieldsConverterTest {

    @Before
    public void setUp() {
        FieldSettingsDao.setInstance(UtilTest.getMockFieldSettingsDao());
    }

    @Test
    public void convert() {
        String fieldName = "data";
        String json = "{\"REGISTRATION_TYPE\":\"1\",\"REGISTRATION_STATUS\":\"7\"}";


        DynamicFieldsConverter dynamicFieldsConverter = new DynamicFieldsConverter();
        DynamicFieldsParser dynamicFieldsParser = new DynamicFieldsParser();
        dynamicFieldsParser.setHelperParser(new ValueParser());
        dynamicFieldsConverter.setParser(dynamicFieldsParser);
        dynamicFieldsConverter.fieldName = fieldName;
        dynamicFieldsConverter.fieldValue = json;
        Map<String, Object> actualMap = dynamicFieldsConverter.convert();

        Map<String, String> registration = Map.of(
                "registrationType", "1",
                "registrationStatus", "7"
        );

        Map<String, Map<String, String>> expectedMap = Map.of("dynamicFields", registration);

        assertEquals(actualMap, expectedMap);
    }
}
