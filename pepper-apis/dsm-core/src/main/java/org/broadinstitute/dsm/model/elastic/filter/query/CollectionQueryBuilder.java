package org.broadinstitute.dsm.model.elastic.filter.query;

import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;

public class CollectionQueryBuilder extends BaseQueryBuilder {

    public CollectionQueryBuilder(QueryPayload queryPayload) {
        super(queryPayload);
    }

    public CollectionQueryBuilder() {
        super(null);
    }

    @Override
    protected QueryBuilder getFinalQuery(QueryBuilder query) {
        return new NestedQueryBuilder(payload.getPath(), query, ScoreMode.Avg);
    }
}

