package org.broadinstitute.dsm.model.elastic.filter.query;

import java.util.List;

import lombok.Setter;
import org.broadinstitute.dsm.model.elastic.export.generate.PropertyInfo;
import org.broadinstitute.dsm.model.elastic.filter.Operator;
import org.elasticsearch.index.query.QueryBuilder;

@Setter
public abstract class BaseQueryBuilder {

    protected Operator operator;
    protected QueryPayload payload;

    public static BaseQueryBuilder of(String alias, String fieldName) {
        BaseQueryBuilder queryBuilder;
        boolean isCollection = PropertyInfo.of(alias).isCollection();
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

    protected abstract QueryBuilder build(List<QueryBuilder> queryBuilder);

    public QueryBuilder buildEachQuery(Operator operator,
                                       QueryPayload queryPayload) {
        this.operator = operator;
        this.payload = queryPayload;
        return this.build(operator.getQueryStrategy().build(this));
    }

}
