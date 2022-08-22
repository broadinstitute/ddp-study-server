package org.broadinstitute.dsm.model.elastic.converters;

import java.util.Map;

import org.broadinstitute.dsm.statics.DBConstants;

public class ConverterFactory {

    private String fieldName;
    private Object fieldValue;
    private String realm;

    private static final Map<String, BaseConverter> generators = Map.of(
            DBConstants.FOLLOW_UP_REQUESTS, new FollowUpsConverter(),
            DBConstants.KIT_TEST_RESULT, new TestResultConverter(),
            DBConstants.ADDITIONAL_VALUES_JSON, new DynamicFieldsConverter(),
            DBConstants.ADDITIONAL_TISSUE_VALUES, new DynamicFieldsConverter(),
            DBConstants.DATA, new DynamicFieldsConverter()
    );

    public ConverterFactory(String fieldName, Object fieldValue, String realm) {
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
        this.realm = realm;
    }


    public Converter<Map<String, Object>> of() {
        BaseConverter converter = generators.getOrDefault(fieldName, new DefaultConverter());
        converter.fieldName = fieldName;
        converter.fieldValue = fieldValue;
        converter.realm = realm;
        return converter;
    }

}
