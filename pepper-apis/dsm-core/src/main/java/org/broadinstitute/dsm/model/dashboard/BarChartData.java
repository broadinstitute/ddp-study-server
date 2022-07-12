package org.broadinstitute.dsm.model.dashboard;

import java.util.List;

import lombok.Getter;

@Getter
class BarChartData extends DashboardData {
    private List<?> valuesX;
    private List<?> valuesY;

    public BarChartData(DisplayType type, List<String> color, Size size, List<?> valuesX, List<?> valuesY, String title, Integer ordering) {
        super(type, color, size, title, ordering);
        this.valuesX = valuesX;
        this.valuesY = valuesY;
    }
}
