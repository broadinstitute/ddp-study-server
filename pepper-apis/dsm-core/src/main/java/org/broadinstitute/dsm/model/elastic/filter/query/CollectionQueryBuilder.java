package org.broadinstitute.dsm.model.elastic.filter.query;

import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;

public class CollectionQueryBuilder extends BaseQueryBuilder {

    @Override
    protected QueryBuilder build(QueryBuilder queryBuilder) {
        return new NestedQueryBuilder(payload.getPath(), queryBuilder, ScoreMode.Avg);
    }
}

