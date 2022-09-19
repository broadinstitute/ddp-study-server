package org.broadinstitute.dsm.model.dashboard;

import java.util.List;

import lombok.Getter;

@Getter
class DonutData extends DashboardData {

    private List<Object> values;
    private List<Object> labels;

    public DonutData(DisplayType type, List<String> color, Size size, List<Object> values,
                     List<Object> labels, String title, Integer ordering) {
        super(type, color, size, title, ordering);
        this.values = values;
        this.labels = labels;
    }
}
