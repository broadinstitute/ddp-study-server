package org.broadinstitute.dsm.model.dashboard;

import java.util.List;

import lombok.Getter;

@Getter
public class DashboardData implements Comparable<DashboardData> {
    protected DisplayType type;
    protected List<String> color;
    protected Size size;
    protected String title;
    protected transient Integer ordering;

    public DashboardData(DisplayType type, List<String> color, Size size, String title, Integer ordering) {
        this.type = type;
        this.color = color;
        this.size = size;
        this.title = title;
        this.ordering = ordering;
    }

    @Override
    public int compareTo(DashboardData other) {
        return this.ordering - other.ordering;
    }
}
