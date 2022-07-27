package org.broadinstitute.dsm.model.elastic.filter.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.elasticsearch.index.query.QueryBuilder;

public abstract class BaseQueryStrategy implements BuildQueryStrategy {

    protected BuildQueryStrategy additionalQueryStrategy;
    protected BaseQueryBuilder baseQueryBuilder;

    public BaseQueryStrategy(BaseQueryBuilder baseQueryBuilder) {
        this();
        this.baseQueryBuilder = baseQueryBuilder;
    }

    public BaseQueryStrategy() {
    }

    @Override
    public void setAdditionalQueryStrategy(BuildQueryStrategy queryStrategy) {
        this.additionalQueryStrategy = queryStrategy;
    }

    @Override
    public List<QueryBuilder> build(BaseQueryBuilder baseQueryBuilder) {
        List<QueryBuilder> queryBuilders = new ArrayList<>();
        if (Objects.nonNull(additionalQueryStrategy)) {
            queryBuilders.add(additionalQueryStrategy.getMainQueryBuilder(additionalQueryStrategy.getBaseQueryBuilder()));
        }
        queryBuilders.add(getMainQueryBuilder(baseQueryBuilder));
        return queryBuilders;
    }

    @Override
    public List<QueryBuilder> build() {
        return build(baseQueryBuilder);
    }

    @Override
    public BaseQueryBuilder getBaseQueryBuilder() {
        return this.baseQueryBuilder;
    }

    protected QueryBuilder getMainQueryBuilder() {
        return this.getMainQueryBuilder(baseQueryBuilder);
    }


}
