package org.broadinstitute.dsm.model.elastic.filter.query;

import org.apache.lucene.search.join.ScoreMode;
import org.broadinstitute.dsm.model.elastic.filter.Operator;
import org.broadinstitute.dsm.model.elastic.filter.splitter.BaseSplitter;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.ExistsQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.WildcardQueryBuilder;

public class CollectionQueryBuilder extends BaseQueryBuilder {

    @Override
    protected QueryBuilder build(QueryBuilder queryBuilder) {
        return new NestedQueryBuilder(payload.getPath(), queryBuilder, ScoreMode.Avg);
    }
}

