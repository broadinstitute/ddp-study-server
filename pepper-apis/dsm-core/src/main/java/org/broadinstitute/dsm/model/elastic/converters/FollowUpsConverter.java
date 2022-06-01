package org.broadinstitute.dsm.model.elastic.converters;

import java.util.HashMap;
import java.util.Map;

import org.broadinstitute.dsm.model.elastic.Util;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;

public class FollowUpsConverter extends BaseConverter {

    FollowUpsConverter() {
    }

    @Override
    public Map<String, Object> convert() {
        Map<String, Object> finalResult;
        finalResult = new HashMap<>(Map.of(Util.underscoresToCamelCase(fieldName),
                ObjectMapperSingleton.writeValueAsString(fieldValue)));
        return finalResult;
    }
}
