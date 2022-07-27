package org.broadinstitute.dsm.model.elastic.filter.query;

import java.util.List;

import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;

public class CollectionQueryBuilder extends BaseQueryBuilder {

    @Override
    protected QueryBuilder build(List<QueryBuilder> queryBuilders) {
        BoolQueryBuilder builder = new BoolQueryBuilder();
        queryBuilders.forEach(builder::must);
        return new NestedQueryBuilder(payload.getPath(), builder, ScoreMode.Avg);
    }
}

