package org.broadinstitute.dsm.model.elastic.export.generate;

import java.util.HashMap;
import java.util.Map;

public class BaseStrategy implements Generator {

    protected String fieldName;
    protected Object value;

    public BaseStrategy(String fieldName, Object value) {
        this.fieldName = fieldName;
        this.value = value;
        if (value instanceof String && ((String) value).isEmpty()) {
            this.value = null;
        }
    }

    @Override
    public Map<String, Object> generate() {
        Map<String, Object> map = new HashMap<>();
        map.put(fieldName, value);
        return map;
    }
}
