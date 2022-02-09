package org.broadinstitute.ddp.util;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Util methods helping to create simple Json docs
 */
public class GsonCreateUtil {

    public static String createJsonIgnoreNulls(String name1, Object value1, String name2, Object value2) {
        Map<String, Object> map = new HashMap<>();
        map.put(name1, value1);
        map.put(name2, value2);
        return  GsonUtil.standardGson().toJson(excludeNullValues(map));
    }

    public static Map<String, Object> excludeNullValues(Map<String, Object> map) {
        return map.entrySet().stream().filter(x -> x.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
