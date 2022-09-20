package org.broadinstitute.dsm.model.elastic.filter.query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;

public class CompositeNestedQueryStrategy extends BaseQueryStrategy {

    private final List<BuildQueryStrategy> queryStrategies;
    private String path;

    public CompositeNestedQueryStrategy(String path) {
        this.path = path;
        queryStrategies = new ArrayList<>();
    }

    public void addStrategy(BuildQueryStrategy... queryStrategy) {
        this.queryStrategies.addAll(Arrays.asList(queryStrategy));
    }

    @Override
    protected QueryBuilder getMainQueryBuilderFromChild(BaseQueryBuilder baseQueryBuilder) {
        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        queryStrategies.stream()
                .map(queryStrategy -> queryStrategy.getMainQueryBuilder(queryStrategy.getBaseQueryBuilder()))
                .forEach(boolQueryBuilder::must);
        return new NestedQueryBuilder(path, boolQueryBuilder, ScoreMode.Avg);
    }
}
