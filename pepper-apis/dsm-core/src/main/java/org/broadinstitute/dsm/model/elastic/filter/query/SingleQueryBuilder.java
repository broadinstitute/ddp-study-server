package org.broadinstitute.dsm.model.elastic.filter.query;

import java.util.List;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;

public class SingleQueryBuilder extends BaseQueryBuilder {

    @Override
    protected QueryBuilder build(List<QueryBuilder> queryBuilders) {
        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        queryBuilders.forEach(boolQueryBuilder::must);
        return boolQueryBuilder;
    }
}
