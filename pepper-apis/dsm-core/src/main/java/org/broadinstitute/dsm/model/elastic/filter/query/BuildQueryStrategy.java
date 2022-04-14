package org.broadinstitute.dsm.model.elastic.filter.query;

import org.elasticsearch.index.query.QueryBuilder;

public interface BuildQueryStrategy {

    QueryBuilder build(BaseQueryBuilder baseQueryBuilder);

}
