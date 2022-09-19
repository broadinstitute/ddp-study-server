package org.broadinstitute.dsm.db.dto.dashboard;

import java.util.List;
import java.util.stream.Collectors;

import lombok.Data;
import org.broadinstitute.dsm.model.dashboard.DisplayType;
import org.broadinstitute.dsm.model.dashboard.Size;

@Data
public class DashboardDto {

    Integer dashboardId;
    Integer ddpInstanceId;
    String displayText;
    DisplayType displayType;
    Size size;
    transient Integer order;
    List<DashboardLabelDto> labels;

    private DashboardDto(Builder builder) {
        this.dashboardId = builder.dashboardId;
        this.ddpInstanceId = builder.ddpInstanceId;
        this.displayText = builder.displayText;
        this.displayType = builder.displayType;
        this.labels = builder.labels;
        this.size = builder.size;
        this.order = builder.order;
    }

    public List<String> getColors() {
        return labels.stream().map(DashboardLabelDto::getColor).collect(Collectors.toList());
    }

    public static class Builder {

        private Integer order;
        private Size size;
        private Integer dashboardId;
        private Integer ddpInstanceId;
        private String displayText;
        private DisplayType displayType;
        private List<DashboardLabelDto> labels;

        public Builder withDashboardId(int dashboardId) {
            this.dashboardId = dashboardId;
            return this;
        }

        public Builder withDdpInstanceId(int ddpInstanceId) {
            this.ddpInstanceId = ddpInstanceId;
            return this;
        }

        public Builder withDisplayText(String displayText) {
            this.displayText = displayText;
            return this;
        }

        public Builder withDisplayType(DisplayType displayType) {
            this.displayType = displayType;
            return this;
        }

        public Builder withLabels(List<DashboardLabelDto> labels) {
            this.labels = labels;
            return this;
        }

        public Builder withSize(Size size) {
            this.size = size;
            return this;
        }

        public Builder withOrder(int order) {
            this.order = order;
            return this;
        }

        public DashboardDto build() {
            return new DashboardDto(this);
        }
    }
}
