package org.broadinstitute.dsm.model.dashboard;

import java.util.List;

import lombok.Getter;

@Getter
public class BarChartData extends DashboardData {

    private List<?> x;
    private List<?> y;

    public BarChartData(DisplayType type, List<String> color, Size size, List<?> x, List<?> y, String title, Integer ordering) {
        super(type, color, size, title, ordering);
        this.x = x;
        this.y = y;
    }
}
