package org.broadinstitute.ddp.util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * JsonElement to Map converter.
 * For example the following JSON:
 * <pre>
 *     {"prequal" :
 *       {
 *         "name" : "Name1",
 *         "value" : "Value1"
 *       }
 *     }
 * </pre>
 * ...will be converted to map:
 * <pre>
 *     key="prequal.name" value="Name1"
 *     key="prequal.value" value="Value1"
 * </pre>
 */
public class JsonToMap {

    public static Map<String, String> transformJsonToMapIterative(JsonElement node) {
        Map<String, String> jsonMap = new HashMap<>();
        LinkedList<JsonNodeWrapper> queue = new LinkedList<>();

        JsonNodeWrapper root = new JsonNodeWrapper(node, "");
        queue.offer(root);

        while (queue.size() != 0) {
            JsonNodeWrapper curElement = queue.poll();
            if (curElement.node.isJsonObject()) {
                JsonObject jsonObject = (JsonObject) curElement.node;
                Iterator<Map.Entry<String, JsonElement>> fieldIterator = jsonObject.entrySet().iterator();
                while (fieldIterator.hasNext()) {
                    Map.Entry<String, JsonElement> field = fieldIterator.next();
                    String prefix = (curElement.prefix == null || curElement.prefix.trim().length() == 0) ? ""  : curElement.prefix + ".";
                    queue.offer(new JsonNodeWrapper(field.getValue(), prefix + field.getKey()));
                }
            } else if (curElement.node.isJsonArray()) {
                int i = 0;
                for (JsonElement arrayElement : curElement.node.getAsJsonArray()) {
                    queue.offer(new JsonNodeWrapper(arrayElement, curElement.prefix + "[" + i + "]"));
                    i++;
                }
            } else {
                jsonMap.put(curElement.prefix, curElement.node.getAsString());
            }
        }

        return jsonMap;
    }

    static class JsonNodeWrapper {
        public JsonElement node;
        public String prefix;

        public JsonNodeWrapper(JsonElement node, String prefix) {
            this.node = node;
            this.prefix = prefix;
        }
    }
}
