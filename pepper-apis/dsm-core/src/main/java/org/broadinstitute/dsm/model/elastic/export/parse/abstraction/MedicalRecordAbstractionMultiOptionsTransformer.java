package org.broadinstitute.dsm.model.elastic.export.parse.abstraction;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;

/**
 * A class which is responsible for building a map for `multi_options` data type
 */
public class MedicalRecordAbstractionMultiOptionsTransformer extends MedicalRecordAbstractionTransformer {

    public static final String OTHER  = "other";
    public static final String VALUES = "values";

    /**
     * Transforms field-value pair into map representation
     * @param fieldName a field name of a concrete record
     * @param value     a value of a concrete record
     */
    @Override
    public Map<String, Object> toMap(String fieldName, String value) {
        String upperCaseFieldName  = columnNameBuilder.apply(fieldName);
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> innerValues = ObjectMapperSingleton.readValue(value, new TypeReference<Map<String, Object>>() {});
            Object other  = innerValues.get(OTHER);
            Object values = innerValues.get(fieldName);
            result.put(upperCaseFieldName, Map.of(OTHER, other, VALUES, values));
        } catch (Exception e) {
            String innerValues  = value.substring(1, value.length() - 1);
            List<String> values = Arrays.stream(innerValues.split(",")).map(String::trim).collect(Collectors.toList());
            result.put(upperCaseFieldName, Map.of(VALUES, values));
        }
        return result;
    }
}
