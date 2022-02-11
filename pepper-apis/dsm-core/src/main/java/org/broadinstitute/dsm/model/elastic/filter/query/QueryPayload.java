package org.broadinstitute.dsm.model.elastic.filter.query;

import lombok.Getter;
import org.broadinstitute.dsm.statics.DBConstants;

@Getter
public class QueryPayload {

    private String property;
    private String path;
    Object[] values;

    public QueryPayload(String path, String property, Object[] values) {
        this.path = path;
        this.property = property;
        this.values = values;
    }

    public String getFieldName() {
        return path + DBConstants.ALIAS_DELIMITER + property;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
