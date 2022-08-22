package org.broadinstitute.dsm.model.elastic;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.broadinstitute.dsm.db.structure.ColumnName;
import org.broadinstitute.dsm.model.elastic.converters.Converter;
import org.broadinstitute.dsm.model.elastic.converters.ConverterFactory;
import org.broadinstitute.dsm.model.elastic.export.parse.BaseParser;

public class ObjectTransformer {
    private BaseParser parser;
    private String realm;

    public ObjectTransformer(String realm, BaseParser parser) {
        this(realm);
        this.parser = parser;
    }

    public ObjectTransformer(String realm) {
        this.realm = realm;
    }

    public List<Map<String, Object>> transformObjectCollectionToCollectionMap(List<Object> values) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object obj : values) {
            result.add(transformObjectToMap(obj));
        }
        return result;
    }

    public Map<String, Object> transformObjectToMap(Object obj) {
        Map<String, Object> result = new HashMap<>();
        List<Field> declaredFields = getDeclaredFieldsIncludingSuperClasses(obj.getClass());
        declaredFields.stream()
                .filter(field -> field.isAnnotationPresent(ColumnName.class))
                .forEach(field -> result.putAll(extractAndConvertObjectToMap(obj, field)));
        return result;
    }

    private Map<String, Object> extractAndConvertObjectToMap(Object obj, Field declaredField) {
        try {
            ColumnName annotation = declaredField.getAnnotation(ColumnName.class);
            declaredField.setAccessible(true);
            Object fieldValue = declaredField.get(obj);
            Map<String, Object> result = Map.of();
            if (Objects.nonNull(fieldValue)) {
                result = convertToMap(annotation.value(), fieldValue);
            }
            return result;
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    List<Field> getDeclaredFieldsIncludingSuperClasses(Class<?> clazz) {
        String objClassName = clazz.getSimpleName();
        if (Object.class.getSimpleName().equals(objClassName)) {
            return Collections.emptyList();
        }
        List<Field> result = new ArrayList<>();
        result.addAll(new ArrayList<>(List.of(clazz.getDeclaredFields())));
        result.addAll(getDeclaredFieldsIncludingSuperClasses(clazz.getSuperclass()));
        return result;
    }

    private Map<String, Object> convertToMap(String fieldName, Object fieldValue) {
        ConverterFactory converterFactory = new ConverterFactory(fieldName, fieldValue, realm);
        Converter<Map<String, Object>> converter = converterFactory.of();
        if (Objects.nonNull(parser)) {
            converter.setParser(parser);
        }
        return converter.convert();
    }

}
