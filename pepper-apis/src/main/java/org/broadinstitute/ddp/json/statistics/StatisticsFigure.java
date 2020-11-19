package org.broadinstitute.ddp.json.statistics;

import java.util.List;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.statistics.StatisticsConfiguration;

public class StatisticsFigure {

    @SerializedName("configuration")
    private final StatisticsConfiguration configuration;

    @SerializedName("statistics")
    private final List<StatisticsFigureItem> statistics;

    public StatisticsFigure(StatisticsConfiguration configuration, List<StatisticsFigureItem> statistics) {
        this.configuration = configuration;
        this.statistics = statistics;
    }

    public StatisticsConfiguration getConfiguration() {
        return configuration;
    }

    public List<StatisticsFigureItem> getStatistics() {
        return statistics;
    }
}
