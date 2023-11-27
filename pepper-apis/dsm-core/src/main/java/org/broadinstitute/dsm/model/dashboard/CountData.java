package org.broadinstitute.dsm.model.dashboard;

import java.util.List;

import lombok.Getter;

@Getter
public class CountData extends DashboardData {

    private Long count;

    public CountData(DisplayType type, List<String> color, Size size, String title, Integer ordering, Long count) {
        super(type, color, size, title, ordering);
        this.count = count;
    }
}
