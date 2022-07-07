package org.broadinstitute.dsm.model.dashboard;

import java.util.List;

public class DonutData extends DashboardData {

    private List<Integer> values;
    private List<String> labels;

    public DonutData(DisplayType type, List<String> color, Size size, List<Integer> values, List<String> labels, String title) {
        super(type, color, size, title);
        this.values = values;
        this.labels = labels;
    }
}
