package org.broadinstitute.dsm.model.dashboard;

import java.util.List;

public class BarChartData extends DashboardData {

    private List<?> x;
    private List<?> y;

    public BarChartData(DisplayType type, List<String> color, Size size, List<?> x, List<?> y, String title) {
        super(type, color, size, title);
        this.x = x;
        this.y = y;
    }
}
