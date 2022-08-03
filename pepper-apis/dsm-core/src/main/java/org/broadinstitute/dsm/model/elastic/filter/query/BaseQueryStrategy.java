package org.broadinstitute.dsm.model.elastic.filter.query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.elasticsearch.index.query.QueryBuilder;

public abstract class BaseQueryStrategy implements BuildQueryStrategy {

    protected List<BuildQueryStrategy> additionalQueryStrategies;
    protected BaseQueryBuilder baseQueryBuilder;

    public BaseQueryStrategy(BaseQueryBuilder baseQueryBuilder) {
        this();
        this.baseQueryBuilder = baseQueryBuilder;
    }

    public BaseQueryStrategy() {
        additionalQueryStrategies = new ArrayList<>();
    }

    @Override
    public void setBaseQueryBuilder(BaseQueryBuilder baseQueryBuilder) {
        this.baseQueryBuilder = baseQueryBuilder;
    }

    @Override
    public void addAdditionalQueryStrategy(BuildQueryStrategy... queryStrategy) {
        this.additionalQueryStrategies.addAll(Arrays.asList(queryStrategy));
    }

    @Override
    public List<QueryBuilder> build(BaseQueryBuilder baseQueryBuilder) {
        List<QueryBuilder> queryBuilders = new ArrayList<>();
        if (hasAdditionalStrategies()) {
            additionalQueryStrategies.stream()
                    .map(additionalQueryStrategy ->
                            additionalQueryStrategy.getMainQueryBuilder(additionalQueryStrategy.getBaseQueryBuilder()))
                    .forEach(queryBuilders::add);
        }
        if (Objects.nonNull(getMainQueryBuilder(baseQueryBuilder))) {
            queryBuilders.add(getMainQueryBuilder(baseQueryBuilder));
        }
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


    private boolean hasAdditionalStrategies() {
        return additionalQueryStrategies.size() > 0;
    }

    public QueryBuilder getMainQueryBuilder(BaseQueryBuilder baseQueryBuilder) {
        return getMainQueryBuilderFromChild(baseQueryBuilder);
    }

    protected abstract QueryBuilder getMainQueryBuilderFromChild(BaseQueryBuilder baseQueryBuilder);
}
