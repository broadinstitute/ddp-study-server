package org.broadinstitute.dsm.model.elastic.filter.query;

import java.util.List;

import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.javalite.common.Collections;

public class NonExactMatchQueryStrategy extends MatchQueryStrategy {

    public static final String WILDCARD_ASTERISK = "*";

    @Override
    public List<QueryBuilder> build(BaseQueryBuilder baseQueryBuilder) {
        QueryBuilder queryBuilder;
        if (isTextType(baseQueryBuilder)) {
            queryBuilder = QueryBuilders.wildcardQuery(
                    baseQueryBuilder.payload.getFieldName(),
                    String.format("%s%s%s",
                            WILDCARD_ASTERISK, String.valueOf(baseQueryBuilder.payload.getValues()[0]).toLowerCase(), WILDCARD_ASTERISK));

        } else {
            queryBuilder = super.getMainQueryBuilder(baseQueryBuilder);
        }
        return Collections.li(queryBuilder);
    }
}
