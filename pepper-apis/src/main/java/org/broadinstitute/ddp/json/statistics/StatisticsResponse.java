package org.broadinstitute.ddp.json.statistics;

import java.util.List;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.statistics.StatisticsConfiguration;

public class StatisticsResponse {

    @SerializedName("configuration")
    private final StatisticsConfiguration configuration;

    @SerializedName("statistics")
    private final List<StatisticsResponseItem> statistics;

    public StatisticsResponse(StatisticsConfiguration configuration, List<StatisticsResponseItem> statistics) {
        this.configuration = configuration;
        this.statistics = statistics;
    }

    public StatisticsConfiguration getConfiguration() {
        return configuration;
    }

    public List<StatisticsResponseItem> getStatistics() {
        return statistics;
    }
}
