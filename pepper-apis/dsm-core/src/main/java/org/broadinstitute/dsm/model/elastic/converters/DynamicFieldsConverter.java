package org.broadinstitute.dsm.model.elastic.converters;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.elastic.converters.camelcase.CamelCaseConverter;
import org.broadinstitute.dsm.model.elastic.export.parse.BaseParser;
import org.broadinstitute.dsm.model.elastic.export.parse.DynamicFieldsParser;
import org.broadinstitute.dsm.model.elastic.export.parse.ValueParser;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;

public class DynamicFieldsConverter extends BaseConverter {

    private BaseParser parser;

    DynamicFieldsConverter() {
        parser = new DynamicFieldsParser();
        parser.setHelperParser(new ValueParser());

    }

    @Override
    public void setParser(BaseParser parser) {
        this.parser = parser;
    }

    @Override
    public Map<String, Object> convert() {
        Map<String, Object> finalResult;
        Map<String, Object> objectMap = dynamicFieldsSpecialCase(fieldValue);
        Map<String, Object> transformedMap = new HashMap<>();
        for (Map.Entry<String, Object> object : objectMap.entrySet()) {
            String field = object.getKey();
            parser.setFieldName(field);
            parser.setRealm(realm);
            String elementValue = String.valueOf(object.getValue());
            Object parsedValue = parser.parse(elementValue);
            String camelCaseField = CamelCaseConverter.of(field).convert();
            transformedMap.put(camelCaseField, parsedValue);
        }
        finalResult = Map.of(ESObjectConstants.DYNAMIC_FIELDS, transformedMap);
        return finalResult;
    }

    private Map<String, Object> dynamicFieldsSpecialCase(Object fieldValue) {
        Map<String, Object> dynamicMap = new HashMap<>();
        if (isJsonInString(fieldValue)) {
            String strValue = (String) fieldValue;
            try {
                dynamicMap = ObjectMapperSingleton.instance().readValue(strValue, Map.class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return dynamicMap;
    }

    private boolean isJsonInString(Object fieldValue) {
        return fieldValue instanceof String && StringUtils.isNotBlank((String) fieldValue) && isJson((String) fieldValue);
    }

    private boolean isJson(String str) {
        return getFirstChar(str) == '{' && getLastChar(str) == '}';
    }

    private char getLastChar(String strValue) {
        if (Objects.isNull(strValue) || strValue.length() == 0) {
            throw new IllegalArgumentException();
        }
        return strValue.charAt(strValue.length() - 1);
    }

    private char getFirstChar(String strValue) {
        if (Objects.isNull(strValue) || strValue.length() == 0) {
            throw new IllegalArgumentException();
        }
        return strValue.charAt(0);
    }
}
