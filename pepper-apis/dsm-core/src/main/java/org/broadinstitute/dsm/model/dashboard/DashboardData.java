package org.broadinstitute.dsm.model.dashboard;

import java.util.List;

public class DashboardData {
    protected DisplayType type;
    protected List<String> color;
    protected Size size;
    protected String title;

    public DashboardData(DisplayType type, List<String> color, Size size, String title) {
        this.type = type;
        this.color = color;
        this.size = size;
        this.title = title;
    }
}
