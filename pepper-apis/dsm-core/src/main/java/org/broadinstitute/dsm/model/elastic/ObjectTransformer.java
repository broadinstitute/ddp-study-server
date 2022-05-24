package org.broadinstitute.dsm.model.elastic;

import java.lang.reflect.Field;
import java.util.ArrayList;
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

    public ObjectTransformer(BaseParser parser) {
        this.parser = parser;
    }

    public ObjectTransformer() {
    }

    Map<String, Object> convertToMap(String fieldName, Object fieldValue, String realm) {
        ConverterFactory converterFactory = new ConverterFactory(fieldName, fieldValue, realm);
        Converter converter = converterFactory.of();
        if (Objects.nonNull(parser)) {
            converter.setParser(parser);
        }
        return converter.convert();
    }

    public List<Map<String, Object>> transformObjectCollectionToCollectionMap(List<Object> values, String realm) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object obj : values) {
            result.add(transformObjectToMap(obj, realm));
        }
        return result;
    }

    public Map<String, Object> transformObjectToMap(Object obj, String realm) {
        Map<String, Object> map = new HashMap<>();
        List<Field> declaredFields = new ArrayList<>(List.of(obj.getClass().getDeclaredFields()));
        List<Field> declaredFieldsSuper = new ArrayList<>(List.of(obj.getClass().getSuperclass().getDeclaredFields()));
        declaredFields.addAll(declaredFieldsSuper);
        for (Field declaredField : declaredFields) {
            ColumnName annotation = declaredField.getAnnotation(ColumnName.class);
            if (annotation == null) {
                continue;
            }
            try {
                declaredField.setAccessible(true);
                Object fieldValue = declaredField.get(obj);
                if (Objects.isNull(fieldValue)) {
                    continue;
                }
                map.putAll(convertToMap(annotation.value(), fieldValue, realm));
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        return map;
    }

}
