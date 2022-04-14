package org.broadinstitute.ddp.json.statistics;

import java.util.Map;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class StatisticsFigureItem {
    @SerializedName("name")
    String name;
    
    @SerializedName("data")
    Map<String, Object> data;
}
