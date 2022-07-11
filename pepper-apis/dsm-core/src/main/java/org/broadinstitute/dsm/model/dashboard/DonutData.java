package org.broadinstitute.dsm.model.dashboard;

import java.util.List;

import lombok.Getter;

@Getter
public class DonutData extends DashboardData {

    private List<Long> values;
    private List<String> labels;

    public DonutData(DisplayType type, List<String> color, Size size, List<Long> values, List<String> labels, String title, Integer ordering) {
        super(type, color, size, title, ordering);
        this.values = values;
        this.labels = labels;
    }
}
