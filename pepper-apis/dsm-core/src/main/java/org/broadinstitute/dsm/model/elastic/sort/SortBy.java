package org.broadinstitute.dsm.model.elastic.sort;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SortBy {

    public static final String SORT_BY = "sortBy";

    private String type;
    private String additionalType;
    private String tableAlias;
    private String outerProperty;
    private String innerProperty;
    private String order;
    private String[] activityVersions;

    private SortBy() {}

    private SortBy(Builder builder) {
        this.type = builder.type;
        this.additionalType = builder.additionalType;
        this.tableAlias = builder.tableAlias;
        this.outerProperty = builder.outerProperty;
        this.innerProperty = builder.innerProperty;
        this.order = builder.order;
        this.activityVersions = builder.activityVersions;
    }

    public static class Builder {

        private String type;
        private String additionalType;
        private String tableAlias;
        private String outerProperty;
        private String innerProperty;
        private String order;
        private String[] activityVersions;

        public Builder() {}

        public Builder withType(String type) {
            this.type = type;
            return this;
        }

        public Builder withAdditionalType(String additionalType) {
            this.additionalType = additionalType;
            return this;
        }

        public Builder withTableAlias(String tableAlias) {
            this.tableAlias = tableAlias;
            return this;
        }

        public Builder withOuterProperty(String outerProperty) {
            this.outerProperty = outerProperty;
            return this;
        }

        public Builder withInnerProperty(String innerProperty) {
            this.innerProperty = innerProperty;
            return this;
        }

        public Builder withOrder(String order) {
            this.order = order;
            return this;
        }

        public Builder withActivityVersions(String[] activityVersions) {
            this.activityVersions = activityVersions;
            return this;
        }

        public SortBy build() {
            return new SortBy(this);
        }

    }

}
