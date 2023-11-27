package org.broadinstitute.dsm.model.elastic.export.painless;

import java.util.List;

import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.index.query.TermsQueryBuilder;

public class TermQueryBuilderFactory {
    private String fieldName;
    private Object value;

    public TermQueryBuilderFactory(String fieldName, Object value) {
        this.fieldName = fieldName;
        this.value = value;
    }

    public QueryBuilder instance() {
        QueryBuilder queryBuilder;
        if (value instanceof List) {
            queryBuilder = new TermsQueryBuilder(fieldName, ((List)value).toArray());
        } else {
            queryBuilder = new TermQueryBuilder(fieldName, value);
        }
        return queryBuilder;
    }
}
