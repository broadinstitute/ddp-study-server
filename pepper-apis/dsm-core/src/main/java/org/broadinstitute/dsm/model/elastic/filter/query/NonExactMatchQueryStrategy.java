package org.broadinstitute.dsm.model.elastic.filter.query;

import java.util.ArrayList;
import java.util.List;

import org.broadinstitute.dsm.model.Filter;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.WildcardQueryBuilder;

public class NonExactMatchQueryStrategy extends MatchQueryStrategy {

    public static final String WILDCARD_ASTERISK = "*";

    @Override
    public List<QueryBuilder> build(BaseQueryBuilder baseQueryBuilder) {
        List<QueryBuilder> queryBuilders = new ArrayList<>();
        if (isTextType(baseQueryBuilder)) {
            queryBuilders.addAll(buildWildCardQueries(baseQueryBuilder));
        } else {
            queryBuilders.add(super.getMainQueryBuilder(baseQueryBuilder));
        }
        return queryBuilders;
    }

    private List<WildcardQueryBuilder> buildWildCardQueries(BaseQueryBuilder baseQueryBuilder) {
        List<WildcardQueryBuilder> result = new ArrayList<>();
        String lowerCaseValue = String.valueOf(baseQueryBuilder.payload.getValues()[0]).toLowerCase();
        String[] wordsSplittedBySpace = lowerCaseValue.split(Filter.SPACE);
        if (isValueSpaceSeparated(wordsSplittedBySpace)) {
            for (String word: wordsSplittedBySpace) {
                result.add(buildWildCardQuery(baseQueryBuilder, word));
            }
        } else {
            result.add(buildWildCardQuery(baseQueryBuilder, lowerCaseValue));
        }
        return result;
    }

    private boolean isValueSpaceSeparated(String[] wordsSplittedBySpace) {
        return wordsSplittedBySpace.length > 1;
    }

    private WildcardQueryBuilder buildWildCardQuery(BaseQueryBuilder baseQueryBuilder, String lowerCaseValue) {
        return QueryBuilders.wildcardQuery(
                baseQueryBuilder.payload.getFieldName(),
                String.format("%s%s%s",
                        WILDCARD_ASTERISK, lowerCaseValue, WILDCARD_ASTERISK));
    }

    @Override
    protected MatchQueryBuilder addOperator(MatchQueryBuilder baseQuery) {
        return baseQuery.operator(Operator.OR);
    }
}
