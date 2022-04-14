package org.broadinstitute.ddp.json.statistics;

import java.util.List;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.broadinstitute.ddp.model.statistics.StatisticsConfiguration;

@Value
@AllArgsConstructor
public class StatisticsFigure {
    @SerializedName("configuration")
    StatisticsConfiguration configuration;

    @SerializedName("statistics")
    List<StatisticsFigureItem> statistics;
}
