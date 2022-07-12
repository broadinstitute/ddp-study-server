package org.broadinstitute.dsm.model.dashboard;

import java.util.List;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

@Getter
class BarChartData extends DashboardData {
    @SerializedName("x")
    private List<?> valuesX;

    @SerializedName("y")
    private List<?> valuesY;

    public BarChartData(DisplayType type, List<String> color, Size size, List<?> valuesX, List<?> valuesY, String title, Integer ordering) {
        super(type, color, size, title, ordering);
        this.valuesX = valuesX;
        this.valuesY = valuesY;
    }
}
