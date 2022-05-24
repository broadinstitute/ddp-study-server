package org.broadinstitute.dsm.model.elastic.converters;

import java.util.HashMap;
import java.util.Map;

import org.broadinstitute.dsm.model.elastic.Util;
import org.broadinstitute.dsm.model.elastic.export.parse.BaseParser;
import org.broadinstitute.dsm.model.elastic.export.parse.DynamicFieldsParser;
import org.broadinstitute.dsm.model.elastic.export.parse.ValueParser;
import org.broadinstitute.dsm.statics.ESObjectConstants;

public class DynamicFieldsConverter extends BaseConverter {

    private BaseParser parser;

    DynamicFieldsConverter() {
        parser = new DynamicFieldsParser();
        parser.setHelperParser(new ValueParser());

    }

    @Override
    public Map<String, Object> convert() {
        Map<String, Object> finalResult;
        Map<String, Object> objectMap = Util.dynamicFieldsSpecialCase(fieldValue);
        Map<String, Object> transformedMap = new HashMap<>();
        for (Map.Entry<String, Object> object : objectMap.entrySet()) {
            String field = object.getKey();
            parser.setFieldName(field);
            parser.setRealm(realm);
            String elementValue = String.valueOf(object.getValue());
            Object parsedValue = parser.parse(elementValue);
            String camelCaseField = Util.underscoresToCamelCase(field);
            transformedMap.put(camelCaseField, parsedValue);
        }
        finalResult = Map.of(ESObjectConstants.DYNAMIC_FIELDS, transformedMap);
        return finalResult;
    }

    @Override
    public void setParser(BaseParser parser) {
        this.parser = parser;
    }
}
