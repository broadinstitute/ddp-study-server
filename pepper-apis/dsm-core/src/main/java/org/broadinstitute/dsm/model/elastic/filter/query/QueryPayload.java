package org.broadinstitute.dsm.model.elastic.filter.query;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.statics.DBConstants;

@Getter
public class QueryPayload {

    private Object[] values;
    private String property;
    private String path;
    private String alias;
    private String esIndex;

    private QueryPayload(Builder builder) {
        this.values = builder.values;
        this.property = builder.property;
        this.path = builder.path;
        this.alias = builder.alias;
        this.esIndex = builder.esIndex;
    }

    public QueryPayload(String path, String property, Object[] values) {
        this.path = path;
        this.property = property;
        this.values = values;
    }

    public QueryPayload(String path, String property, String alias, Object[] values, String esIndex) {
        this(path, property, values);
        this.alias = alias;
        this.esIndex = esIndex;
    }

    public QueryPayload(String path, String property, Object[] values, String esIndex) {
        this(path, property, values);
        this.esIndex = esIndex;
    }

    public String getFieldName() {
        if (StringUtils.isNotBlank(path)) {
            return path + DBConstants.ALIAS_DELIMITER + property;
        }
        return property;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getPath() {
        return StringUtils.isBlank(path)
                ? property
                : path;
    }

    public static class Builder {

        private Object[] values;
        private String property;
        private String path;
        private String alias;
        private String esIndex;

        public Builder withValues(Object[] values) {
            this.values = values;
            return this;
        }

        public Builder withProperty(String property) {
            this.property = property;
            return this;
        }

        public Builder withPath(String path) {
            this.path = path;
            return this;
        }

        public Builder withAlias(String alias) {
            this.alias = alias;
            return this;
        }

        public Builder withEsIndex(String esIndex) {
            this.esIndex = esIndex;
            return this;
        }

        public QueryPayload build() {
            return new QueryPayload(this);
        }
    }
}
