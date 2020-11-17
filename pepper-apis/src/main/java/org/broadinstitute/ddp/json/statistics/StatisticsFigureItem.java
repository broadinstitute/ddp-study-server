package org.broadinstitute.ddp.json.statistics;

import java.util.Map;

import com.google.gson.annotations.SerializedName;

public class StatisticsFigureItem {
    @SerializedName("name")
    private final String name;
    @SerializedName("data")
    private final Map<String, Object> data;

    public StatisticsFigureItem(String name, Map<String, Object> data) {
        this.name = name;
        this.data = data;
    }

    public String getName() {
        return name;
    }

    public Map<String, Object> getData() {
        return data;
    }
}
