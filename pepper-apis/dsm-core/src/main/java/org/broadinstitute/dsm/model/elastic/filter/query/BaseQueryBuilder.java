package org.broadinstitute.dsm.model.elastic.filter.query;

import java.util.List;
import java.util.Objects;

import lombok.Getter;
import lombok.Setter;
import org.broadinstitute.dsm.model.elastic.export.generate.PropertyInfo;
import org.broadinstitute.dsm.model.elastic.filter.Operator;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;

@Setter
@Getter
public abstract class BaseQueryBuilder {

    protected Operator operator;
    protected QueryPayload payload;

    public BaseQueryBuilder(QueryPayload queryPayload) {
        this.payload = queryPayload;
    }

    public static BaseQueryBuilder of(QueryPayload queryPayload) {
        BaseQueryBuilder queryBuilder;
        boolean isCollection = PropertyInfo.of(queryPayload.getAlias()).isCollection();
        if (isCollection) {
            if (TestResultCollectionQueryBuilder.TEST_RESULT.equals(queryPayload.getProperty())) {
                queryBuilder =
                        new TestResultCollectionQueryBuilder(queryPayload);
            } else {
                queryBuilder = new CollectionQueryBuilder(queryPayload);
            }
        } else {
            queryBuilder = new SingleQueryBuilder(queryPayload);
        }
        return queryBuilder;
    }

    public QueryBuilder build(List<QueryBuilder> queryBuilders) {
        QueryBuilder result;
        if (queryBuilders.size() == 1) {
            result = queryBuilders.get(0);
        } else {
            BoolQueryBuilder builder = new BoolQueryBuilder();
            queryBuilders
                    .stream()
                    .filter(Objects::nonNull)
                    .forEach(builder::must);
            result = builder;
        }
        return getFinalQuery(result);
    }

    protected abstract QueryBuilder getFinalQuery(QueryBuilder query);

}
