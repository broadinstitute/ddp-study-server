package org.broadinstitute.dsm.db.dto.dashboard;

import java.util.Optional;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
public class DashboardLabelDto {

    Integer labelId;
    Integer dashboardId;
    String labelName;
    String color;
    DashboardLabelFilterDto dashboardFilterDto;

    private DashboardLabelDto(Builder builder) {
        this.labelId = builder.labelId;
        this.dashboardId = builder.dashboardId;
        this.labelName = builder.labelName;
        this.color = builder.color;
        this.dashboardFilterDto = builder.dashboardLabelFilterDto;
    }

    public String getEsNestedPath() {
        return Optional.ofNullable(dashboardFilterDto).map(DashboardLabelFilterDto::getEsNestedPath).orElse(StringUtils.EMPTY);
    }

    public static class Builder {

        private Integer labelId;
        private Integer dashboardId;
        private String labelName;
        private String color;
        private DashboardLabelFilterDto dashboardLabelFilterDto;

        public Builder withLabelId(int labelId) {
            this.labelId = labelId;
            return this;
        }

        public Builder withDashboardId(int dashboardId) {
            this.dashboardId = dashboardId;
            return this;
        }

        public Builder withLabelName(String labelName) {
            this.labelName = labelName;
            return this;
        }

        public Builder withColor(String color) {
            this.color = color;
            return this;
        }

        public Builder withDashboardLabelFilter(DashboardLabelFilterDto dashboardLabelFilterDto) {
            this.dashboardLabelFilterDto = dashboardLabelFilterDto;
            return this;
        }

        public DashboardLabelDto build() {
            return new DashboardLabelDto(this);
        }
    }

}
