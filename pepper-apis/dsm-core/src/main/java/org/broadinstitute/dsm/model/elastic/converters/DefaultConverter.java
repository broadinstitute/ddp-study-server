package org.broadinstitute.dsm.model.elastic.converters;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.elastic.Util;
import org.broadinstitute.dsm.model.elastic.export.parse.ValueParser;

public class DefaultConverter extends BaseConverter {

    DefaultConverter() {
    }

    @Override
    public Map<String, Object> convert() {
        Map<String, Object> finalResult;
        Map<String, Object> result = new HashMap<>();
        if (ValueParser.N_A.equals(fieldValue)) {
            fieldValue = ValueParser.N_A_SYMBOLIC_DATE;
        }
        result.put(Util.underscoresToCamelCase(fieldName), StringUtils.isBlank(String.valueOf(fieldValue)) ? null : fieldValue);
        finalResult = result;
        return finalResult;
    }
}
