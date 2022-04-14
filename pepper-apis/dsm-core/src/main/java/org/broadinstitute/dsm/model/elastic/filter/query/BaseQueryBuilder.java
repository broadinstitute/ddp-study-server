package org.broadinstitute.dsm.model.elastic.filter.query;

import org.broadinstitute.dsm.model.elastic.Util;
import org.broadinstitute.dsm.model.elastic.filter.Operator;
import org.elasticsearch.index.query.QueryBuilder;

public abstract class BaseQueryBuilder {

    protected Operator operator;
    protected QueryPayload payload;

    public static BaseQueryBuilder of(String alias, String fieldName) {
        BaseQueryBuilder queryBuilder;
        boolean isCollection = Util.TABLE_ALIAS_MAPPINGS.get(alias).isCollection();
        if (isCollection) {
            if (TestResultCollectionQueryBuilder.TEST_RESULT.equals(fieldName)) {
                queryBuilder =
                        new TestResultCollectionQueryBuilder();
            } else {
                queryBuilder = new CollectionQueryBuilder();
            }
        } else {
            queryBuilder = new SingleQueryBuilder();
        }
        return queryBuilder;
    }

    protected abstract QueryBuilder build(QueryBuilder queryBuilder);

    public QueryBuilder buildEachQuery(Operator operator,
                                       QueryPayload queryPayload) {
        this.operator = operator;
        this.payload = queryPayload;
        return operator.getQueryStrategy().build(this);
    }

}
