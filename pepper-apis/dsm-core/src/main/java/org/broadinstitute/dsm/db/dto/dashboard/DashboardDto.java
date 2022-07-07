package org.broadinstitute.dsm.db.dto.dashboard;

import java.util.List;

import lombok.Data;

@Data
public class DashboardDto {

    Integer dashboardId;
    Integer ddpInstanceId;
    String displayText;
    String displayType;
    String size;
    Integer order;
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

    public static class Builder {

        private Integer order;
        private String size;
        private Integer dashboardId;
        private Integer ddpInstanceId;
        private String displayText;
        private String displayType;
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

        public Builder withDisplayType(String displayType) {
            this.displayType = displayType;
            return this;
        }

        public Builder withLabels(List<DashboardLabelDto> labels) {
            this.labels = labels;
            return this;
        }

        public Builder withSize(String size) {
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
