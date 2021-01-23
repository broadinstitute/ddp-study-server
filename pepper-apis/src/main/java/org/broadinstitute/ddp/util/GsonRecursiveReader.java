package org.broadinstitute.ddp.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Provides reading of node values from JSON doc.<br>
 * Main method: {@link #readValues(JsonElement, List)}<br>
 * Example:
 * <pre>
 *   var result = GsonRecursiveReader.getValues(rootElem, List.of("log_id", "date", "type"));
 * </pre>
 * The result will contain map with elements found by names (specified by list of strings).
 * Each name searched recursively: if it is not found at top level then try to find it deeper etc..
 * As soon as a name is found - it stopped to search this name in the document.
 */
public class GsonRecursiveReader {

    /**
     * Main method providing reading of {@link JsonElement}-s by it's names.
     * The elements searched recursively (from top to bottom) and as soon as a name found
     * it stops searching of this name (i.e. more deeper elements with the same name not override
     * already detected element).
     *
     * @param rootNode root element of JSON document where to find elements
     * @param names    list of elements' names which to search for
     * @return {@code Map<String, JsonElement>} map with detected node values
     */
    public static Map<String, JsonElement> readValues(JsonElement rootNode, List<String> names) {
        Map<String, JsonElement> results = new HashMap<>();
        if (names != null && names.size() > 0) {
            final List<String> yetNotReadNames = new LinkedList<>(names);
            getValuesRecursively(rootNode, yetNotReadNames, results);
        }
        return results;
    }

    private static void getValuesRecursively(JsonElement node, List<String> yetNotReadNames, Map<String, JsonElement> results) {
        if (node instanceof JsonArray) {
            getValuesFromArray((JsonArray) node, yetNotReadNames, results);
        } else if (node instanceof JsonObject) {
            for (var entry : ((JsonObject) node).entrySet()) {
                getEntryValueIfFoundInReadNames(entry, yetNotReadNames, results);
            }
            for (var entry : ((JsonObject) node).entrySet()) {
                getValuesRecursively(entry.getValue(), yetNotReadNames, results);
            }
        }
    }

    private static void getEntryValueIfFoundInReadNames(Map.Entry<String, JsonElement> entry, List<String> yetNotReadNames,
                                                        Map<String, JsonElement> results) {
        var foundNames = new ArrayList<>();
        for (var name : yetNotReadNames) {
            if (entry.getKey().equals(name)) {
                foundNames.add(name);
                results.put(name, entry.getValue());
            }
        }
        yetNotReadNames.removeAll(foundNames);
    }

    private static void getValuesFromArray(JsonArray nodeArray, List<String> yetNotReadNames, Map<String, JsonElement> results) {
        for (var node : nodeArray) {
            getValuesRecursively(node, yetNotReadNames, results);
        }
    }
}
