package org.broadinstitute.dsm.util.proxy.jackson;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;

public class ObjectMapperSingleton {

    private ObjectMapperSingleton() {}

    public static ObjectMapper instance() {
        return Helper.objectMapperInstance;
    }

    public static <T> T readValue(String content, TypeReference<?> typeReference) {
        content = StringUtils.isBlank(content) ? "{}" : content;
        try {
            return Helper.objectMapperInstance.readValue(content, typeReference);
        } catch (com.fasterxml.jackson.core.JsonParseException e) {
            throw new JsonParseException(e.getMessage());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
        private static final ObjectMapper objectMapperInstance = new ObjectMapper();
    }


}
