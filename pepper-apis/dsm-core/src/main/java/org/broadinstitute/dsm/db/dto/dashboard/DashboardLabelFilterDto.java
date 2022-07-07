package org.broadinstitute.dsm.db.dto.dashboard;

import lombok.Data;

@Data
public class DashboardLabelFilterDto {

    private Integer labelFilterId;
    private String esFilterPath;
    private String esFilterPathValue;
    private String esNestedPath;
    private String additionalFilter;
    private Integer labelId;

    private DashboardLabelFilterDto(Builder builder) {
        this.labelFilterId = builder.labelFilterId;
        this.esFilterPath = builder.esFilterPath;
        this.esFilterPathValue = builder.esFilterPathValue;
        this.esFilterPath = builder.esFilterPath;
        this.additionalFilter = builder.additionalFilter;
        this.labelId = builder.labelId;
    }

    public static class Builder {

        private Integer labelFilterId;
        private String esFilterPath;
        private String esFilterPathValue;
        private String esNestedPath;
        private String additionalFilter;
        private Integer labelId;

        public Builder withLabelFilterId(int labelFilterId) {
            this.labelFilterId = labelFilterId;
            return this;
        }

        public Builder withEsFilterPath(String esFilterPath) {
            this.esFilterPath = esFilterPath;
            return this;
        }

        public Builder withEsFilterPathValue(String esFilterPathValue) {
            this.esFilterPathValue = esFilterPathValue;
            return this;
        }

        public Builder withEsNestedPath(String esNestedPath) {
            this.esNestedPath = esNestedPath;
            return this;
        }

        public Builder withAdditionalFilter(String additionalFilter) {
            this.additionalFilter = additionalFilter;
            return this;
        }

        public Builder withLabelId(int labelId) {
            this.labelId = labelId;
            return this;
        }

        public DashboardLabelFilterDto build() {
            return new DashboardLabelFilterDto(this);
        }
    }

}
