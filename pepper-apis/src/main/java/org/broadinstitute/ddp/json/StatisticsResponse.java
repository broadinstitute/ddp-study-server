package org.broadinstitute.ddp.json;

import java.util.Map;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.statistics.StatisticsConfiguration;

public class StatisticsResponse {

    @SerializedName("configuration")
    private final StatisticsConfiguration configuration;

    @SerializedName("values")
    private final Map<String, Object> values;

    public StatisticsResponse(StatisticsConfiguration configuration, Map<String, Object> values) {
        this.configuration = configuration;
        this.values = values;
    }

    public StatisticsConfiguration getConfiguration() {
        return configuration;
    }

    public Map<String, Object> getValues() {
        return values;
    }
}
