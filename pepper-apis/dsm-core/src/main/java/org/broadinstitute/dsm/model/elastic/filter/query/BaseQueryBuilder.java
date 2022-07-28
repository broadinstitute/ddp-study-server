package org.broadinstitute.dsm.model.elastic.filter.query;

import java.util.List;

import lombok.Setter;
import org.broadinstitute.dsm.model.elastic.export.generate.PropertyInfo;
import org.broadinstitute.dsm.model.elastic.filter.Operator;
import org.elasticsearch.index.query.BoolQueryBuilder;
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

    protected QueryBuilder build(List<QueryBuilder> queryBuilders) {
        QueryBuilder result;
        if (queryBuilders.size() == 1) {
            result = queryBuilders.get(0);
        } else {
            BoolQueryBuilder builder = new BoolQueryBuilder();
            queryBuilders.forEach(builder::must);
            result = builder;
        }
        return getFinalQuery(result);
    }

    protected abstract QueryBuilder getFinalQuery(QueryBuilder query);

    public QueryBuilder buildEachQuery(Operator operator,
                                       QueryPayload queryPayload) {
        this.operator = operator;
        this.payload = queryPayload;
        return this.build(operator.getQueryStrategy().build(this));
    }

}
