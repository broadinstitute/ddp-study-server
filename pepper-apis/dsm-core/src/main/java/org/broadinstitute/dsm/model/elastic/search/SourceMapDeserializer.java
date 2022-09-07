package org.broadinstitute.dsm.model.elastic.search;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import org.broadinstitute.dsm.model.elastic.Dsm;
import org.broadinstitute.dsm.model.elastic.converters.camelcase.CamelCaseConverter;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;

public class SourceMapDeserializer implements Deserializer {

    private static final Pattern UPPER_CASE_REGEX = Pattern.compile("(?=\\p{Upper})");
    String outerProperty;

    public Optional<ElasticSearchParticipantDto> deserialize(Map<String, Object> sourceMap) {
        Map<String, Object> dsmLevel = (Map<String, Object>) sourceMap.get(ESObjectConstants.DSM);

        if (Objects.isNull(dsmLevel)) {
            return Optional.of(ObjectMapperSingleton.instance().convertValue(sourceMap, ElasticSearchParticipantDto.class));
        }

        Map<String, Object> updatedPropertySourceMap = updatePropertySourceMapIfSpecialCases(dsmLevel);
        if (!updatedPropertySourceMap.isEmpty()) {
            dsmLevel.putAll(updatedPropertySourceMap);
        }

        return Optional.of(ObjectMapperSingleton.instance().convertValue(sourceMap, ElasticSearchParticipantDto.class));
    }

    private Map<String, Object> updatePropertySourceMapIfSpecialCases(Map<String, Object> dsmLevel) {
        Map<String, Object> updatedPropertySourceMap = new HashMap<String, Object>();
        for (Map.Entry<String, Object> entry : dsmLevel.entrySet()) {
            outerProperty = entry.getKey();
            Object outerPropertyValue = entry.getValue();
            if (!hasSpecialCases(outerProperty)) {
                continue;
            }
            if (outerPropertyValue instanceof List) {
                List<Map<String, Object>> outerPropertyValues = (List<Map<String, Object>>) outerPropertyValue;
                List<Map<String, Object>> updatedOuterPropertyValues = handleSpecialCases(outerPropertyValues);
                ;
                if (!updatedOuterPropertyValues.isEmpty()) {
                    updatedPropertySourceMap.put(outerProperty, updatedOuterPropertyValues);
                }
            } else {
                Map<String, Object> singleOuterPropertyValue = (Map<String, Object>) outerPropertyValue;
                Map<String, Object> updatedSingleOuterPropertyValue = new HashMap<>(singleOuterPropertyValue);
                if (singleOuterPropertyValue.containsKey(ESObjectConstants.DYNAMIC_FIELDS)) {
                    updatedSingleOuterPropertyValue.put(ESObjectConstants.DYNAMIC_FIELDS,
                            getDynamicFieldsValueAsJson(updatedSingleOuterPropertyValue));
                }

                updatedPropertySourceMap.put(outerProperty, updatedSingleOuterPropertyValue);
            }
        }
        return updatedPropertySourceMap;
    }

    private List<Map<String, Object>> handleSpecialCases(List<Map<String, Object>> outerPropertyValues) {
        List<Map<String, Object>> updatedOuterPropertyValues = new ArrayList<>();
        for (Map<String, Object> object : outerPropertyValues) {
            Map<String, Object> clonedMap = new HashMap<>(object);
            if (object.containsKey(ESObjectConstants.DYNAMIC_FIELDS)) {
                clonedMap.put(ESObjectConstants.DYNAMIC_FIELDS, getDynamicFieldsValueAsJson(clonedMap));
            }
            if (object.containsKey(ESObjectConstants.FOLLOW_UPS)) {
                clonedMap.put(ESObjectConstants.FOLLOW_UPS, convertFollowUpsJsonToList(clonedMap));
            }
            if (object.containsKey(ESObjectConstants.KIT_TEST_RESULT)) {
                clonedMap.put(ESObjectConstants.KIT_TEST_RESULT, convertTestResultValueAsJson(clonedMap));
            }
            updatedOuterPropertyValues.add(clonedMap);
        }
        return updatedOuterPropertyValues;
    }

    private List<Map<String, Object>> convertFollowUpsJsonToList(Map<String, Object> clonedMap) {
        String followUps = (String) clonedMap.get(ESObjectConstants.FOLLOW_UPS);
        return ObjectMapperSingleton.readValue(followUps, new TypeReference<List<Map<String, Object>>>() {
        });
    }

    String getDynamicFieldsValueAsJson(Map<String, Object> clonedMap) {
        Map<String, Object> dynamicFields = (Map<String, Object>) clonedMap.get(ESObjectConstants.DYNAMIC_FIELDS);
        if (ESObjectConstants.PARTICIPANT_DATA.equals(outerProperty)) {
            dynamicFields = convertDynamicFieldsFromCamelCaseToPascalCase(dynamicFields);
        }
        return ObjectMapperSingleton.writeValueAsString(dynamicFields);
    }

    String convertTestResultValueAsJson(Map<String, Object> clonedMap) {
        return ObjectMapperSingleton.writeValueAsString(clonedMap.get(ESObjectConstants.KIT_TEST_RESULT));
    }

    protected Map<String, Object> convertDynamicFieldsFromCamelCaseToPascalCase(Map<String, Object> dynamicFields) {
        Map<String, Object> updatedParticipantDataDynamicFields = new HashMap<>();
        for (Map.Entry<String, Object> entry : dynamicFields.entrySet()) {
            updatedParticipantDataDynamicFields.put(camelCaseToPascalSnakeCase(entry.getKey()), entry.getValue());
        }
        dynamicFields = updatedParticipantDataDynamicFields;
        return dynamicFields;
    }


    public String camelCaseToPascalSnakeCase(String camelCase) {
        String[] words = camelCase.split(UPPER_CASE_REGEX.toString());
        String pascalSnakeCase =
                Arrays.stream(words).map(String::toUpperCase).collect(Collectors.joining(CamelCaseConverter.UNDERSCORE_SEPARATOR));
        return pascalSnakeCase;
    }

    private boolean hasSpecialCases(String outerProperty) {
        try {
            Field property = Dsm.class.getDeclaredField(outerProperty);
            Class<?> propertyType = getParameterizedType(property.getGenericType());
            Field[] declaredFields = propertyType.getDeclaredFields();
            return Arrays.stream(declaredFields).anyMatch(field -> isDynamicField(field) || isTestResult(field));
        } catch (NoSuchFieldException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    Class<?> getParameterizedType(Type genericType) throws ClassNotFoundException {
        String typeAsString = genericType.toString();
        String[] types = typeAsString.contains("<") ? typeAsString.split("<") : typeAsString.split("\\[L");
        if (types.length < 2) {
            return (Class) genericType;
        }
        String parameterizedType = types[1];
        parameterizedType = parameterizedType.replace(">", "");
        parameterizedType = parameterizedType.replace(";", "");
        return Class.forName(parameterizedType);
    }

    private boolean isDynamicField(Field field) {
        JsonProperty jsonProperty = field.getAnnotation(JsonProperty.class);
        if (Objects.isNull(jsonProperty)) {
            return false;
        } else {
            return jsonProperty.value().equals(ESObjectConstants.DYNAMIC_FIELDS);
        }
    }

    private boolean isTestResult(Field field) {
        String fieldName = field.getName();
        return ESObjectConstants.KIT_TEST_RESULT.equals(fieldName);
    }

}
