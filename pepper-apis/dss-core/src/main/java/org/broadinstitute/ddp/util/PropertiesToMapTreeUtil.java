package org.broadinstitute.ddp.util;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.lang3.StringEscapeUtils;

/**
 * Converts properties to hierarchy of maps: properties names are split into parts which separated by '.'
 * and loaded to hierarchy of maps.
 *
 * <p>For example a template:
 * <pre>
 *     "Sections: $prequal.sect1.name, $prequal.sect2.name"
 * </pre>
 * and properties:
 * <pre>
 * Properties p = new Properties();
 * p.put("prequal.sect1.name", "Sect_One");
 * p.put("prequal.sect2.name", "Sect_Two");
 * </pre>
 * And the properties can be converted to a Map hierarchy and loaded to a Velocity context to evaluate a template.
 */
public class PropertiesToMapTreeUtil {

    public static Map<String, Object> propertiesToMap(Map<String, Object> properties) {
        Map<String, Object> map = new TreeMap<>();
        for (Object key : properties.keySet()) {
            List<String> keyList = Arrays.asList(((String) key).split("\\."));
            Map<String, Object> valueMap = createTree(keyList, map);
            Object value = properties.get(key);
            if (value instanceof String) {
                // todo arz double check this
                value = StringEscapeUtils.unescapeHtml3((String)value);
            }
            valueMap.put(keyList.get(keyList.size() - 1), value);
        }
        return map;
    }

    private static Map<String, Object> createTree(List<String> keys, Map<String, Object> map) {
        Map<String, Object> valueMap = (Map<String, Object>) map.get(keys.get(0));
        if (valueMap == null) {
            valueMap = new HashMap<>();
        }
        map.put(keys.get(0), valueMap);
        Map<String, Object> out = valueMap;
        if (keys.size() > 2) {
            out = createTree(keys.subList(1, keys.size()), valueMap);
        }
        return out;
    }
}
