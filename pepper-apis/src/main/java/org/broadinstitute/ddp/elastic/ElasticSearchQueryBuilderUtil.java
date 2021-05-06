package org.broadinstitute.ddp.elastic;

import static org.broadinstitute.ddp.elastic.ElasticSearchQueryUtil.addWildcards;

import java.util.Collection;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;


/**
 * Helper methods for {@link QueryBuilder} creation.
 */
public class ElasticSearchQueryBuilderUtil {

    public static QueryBuilder queryStringQuery(String field, String query) {
        return QueryBuilders.queryStringQuery(addWildcards(query)).field(field);
    }

    public static QueryBuilder notEmptyFieldQuery(String field) {
        return QueryBuilders.regexpQuery(field, ".+");
    }

    public static QueryBuilder and(QueryBuilder... queryBuilders) {
        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        for (QueryBuilder builder : queryBuilders) {
            boolQueryBuilder.must(builder);
        }
        return boolQueryBuilder;
    }

    public static QueryBuilder or(QueryBuilder... queryBuilders) {
        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        for (QueryBuilder builder : queryBuilders) {
            boolQueryBuilder.should(builder);
        }
        return boolQueryBuilder;
    }

    public static <T> QueryBuilder orMatch(String field, Collection<T> values) {
        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        for (var v : values) {
            boolQueryBuilder.should(QueryBuilders.matchQuery(field, v));
        }
        return boolQueryBuilder;
    }
}
