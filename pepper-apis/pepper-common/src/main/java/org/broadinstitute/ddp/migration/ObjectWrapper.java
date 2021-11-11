package org.broadinstitute.ddp.migration;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;

public class ObjectWrapper {

    protected final JsonObject inner;

    ObjectWrapper(JsonObject inner) {
        this.inner = inner;
    }

    // public Set<Map.Entry<String, JsonElement>> entrySet() {
    //     return inner.entrySet();
    // }
    //
    // public JsonElement get(String name) {
    //     var value = inner.get(name);
    //     return value == null || value.isJsonNull() ? null : value;
    // }
    //
    // public JsonArray getArray(String name) {
    //     var value = inner.getAsJsonArray(name);
    //     return value == null || value.isJsonNull() ? null : value;
    // }

    public boolean has(String name) {
        return inner.has(name);
    }

    public Boolean getBool(String name) {
        var value = inner.get(name);
        return value == null || value.isJsonNull() ? null : value.getAsBoolean();
    }

    public Integer getInt(String name) {
        var value = inner.get(name);
        return value == null || value.isJsonNull() ? null : value.getAsInt();
    }

    public Long getLong(String name) {
        var value = inner.get(name);
        return value == null || value.isJsonNull() ? null : value.getAsLong();
    }

    public String getString(String name) {
        var value = inner.get(name);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    public ObjectWrapper getObject(String name) {
        var value = inner.get(name);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        return new ObjectWrapper(value.getAsJsonObject());
    }

    public List<String> getStringList(String name) {
        var value = inner.get(name);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        var array = value.getAsJsonArray();
        List<String> strings = new ArrayList<>();
        for (var item : array) {
            if (item != null && !item.isJsonNull()) {
                strings.add(item.getAsString());
            } else {
                strings.add(null);
            }
        }
        return strings;
    }

    public List<ObjectWrapper> getObjectList(String name) {
        var value = inner.get(name);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        var array = value.getAsJsonArray();
        List<ObjectWrapper> objects = new ArrayList<>();
        for (var item : array) {
            if (item != null && !item.isJsonNull()) {
                objects.add(new ObjectWrapper(item.getAsJsonObject()));
            } else {
                objects.add(null);
            }
        }
        return objects;
    }
}
