package org.broadinstitute.dsm.model.elastic.converters;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import org.broadinstitute.dsm.model.elastic.converters.camelcase.CamelCaseConverter;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;

public class TestResultConverter extends BaseConverter {

    TestResultConverter() {
    }

    @Override
    public Map<String, Object> convert() {
        Map<String, Object> finalResult;
        List<Map<String, Object>> testResult =
                ObjectMapperSingleton.readValue(String.valueOf(fieldValue), new TypeReference<List<Map<String, Object>>>() {
                });
        finalResult = !testResult.isEmpty() ? Map.of(CamelCaseConverter.of(fieldName).convert(), testResult) : Map.of();
        return finalResult;
    }
}
