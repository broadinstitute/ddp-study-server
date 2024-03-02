package org.broadinstitute.dsm.util.proxy.jackson;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.exception.DsmInternalError;

public class ObjectMapperSingleton {

    private ObjectMapperSingleton() {
    }

    public static ObjectMapper instance() {
        return Helper.objectMapperInstance;
    }

    public static <T> T readValue(String content, TypeReference<T> typeReference) {
        try {
            content = ObjectMapperSingleton.getContent(content, typeReference);
            return Helper.objectMapperInstance.readValue(content, typeReference);
        } catch (com.fasterxml.jackson.core.JsonParseException e) {
            throw new JsonParseException(e.getMessage());
        } catch (Exception e) {
            throw new DsmInternalError(e);
        }
    }

    private static <T> String getContent(String content, TypeReference<T> typeReference) throws ClassNotFoundException {
        if (StringUtils.isNotBlank(content)) {
            return content;
        }
        String className = typeReference.getType().getTypeName().split("<")[0];
        Class<?> clazz = Class.forName(className);
        if (List.class.isAssignableFrom(clazz)) {
            return "[{}]";
        }
        return "{}";
    }


    public static String writeValueAsString(Object value) {
        value = Objects.isNull(value) ? Map.of() : value;
        try {
            return Helper.objectMapperInstance.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new JsonProcessingException(e.getMessage());
        }
    }

    private static class Helper {
        private static final ObjectMapper objectMapperInstance = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }


}
