package org.broadinstitute.dsm.model.elastic.filter.query;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.elastic.Util;
import org.broadinstitute.dsm.model.elastic.filter.Operator;
import org.broadinstitute.dsm.model.elastic.filter.splitter.SplitterStrategy;
import org.broadinstitute.dsm.model.elastic.filter.splitter.GreaterThanEqualsSplitterStrategy;
import org.broadinstitute.dsm.model.elastic.filter.splitter.IsNullSplitterStrategy;
import org.broadinstitute.dsm.model.elastic.filter.splitter.JsonExtractSplitterStrategy;
import org.broadinstitute.dsm.model.elastic.filter.splitter.LessThanEqualsSplitterStrategy;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.ExistsQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;

public abstract class BaseQueryBuilder {

    protected Operator operator;
    protected QueryPayload payload;
    protected SplitterStrategy splitter;

    public static BaseQueryBuilder of(String alias, String fieldName) {
        BaseQueryBuilder queryBuilder;
        boolean isCollection = Util.TABLE_ALIAS_MAPPINGS.get(alias).isCollection();
        if (isCollection) {
            if (TestResultCollectionQueryBuilder.TEST_RESULT.equals(fieldName)) {
                queryBuilder =
                        new TestResultCollectionQueryBuilder();
            } else {
                queryBuilder = new CollectionQueryBuilder();
            }
        } else {
            queryBuilder = new SingleQueryBuilder();
        }
        return queryBuilder;
    }

    protected QueryBuilder buildQueryBuilder() {
        QueryBuilder qb;
        switch (operator) {
            case LIKE:
            case EQUALS:
            case DATE:
            case DIAMOND_EQUALS:
            case JSON_CONTAINS:
            case STR_DATE:
                qb = build(new MatchQueryBuilder(payload.getFieldName(), payload.getValues()[0]));
                break;
            case GREATER_THAN_EQUALS:
            case DATE_GREATER_THAN_EQUALS:
                RangeQueryBuilder greaterRangeQuery = new RangeQueryBuilder(payload.getFieldName());
                greaterRangeQuery.gte(payload.getValues()[0]);
                qb = build(greaterRangeQuery);
                break;
            case LESS_THAN_EQUALS:
            case DATE_LESS_THAN_EQUALS:
                RangeQueryBuilder lessRangeQuery = new RangeQueryBuilder(payload.getFieldName());
                lessRangeQuery.lte(payload.getValues()[0]);
                qb = build(lessRangeQuery);
                break;
            case IS_NOT_NULL:
                qb = buildIsNotNullAndEmpty();
                break;
            case IS_NULL:
                qb = buildIsNullQuery();
                break;
            case MULTIPLE_OPTIONS:
                BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
                Object[] values = payload.getValues();
                for (Object value : values) {
                    boolQueryBuilder.should(new MatchQueryBuilder(payload.getFieldName(), value));
                }
                qb = build(boolQueryBuilder);
                break;
            case JSON_EXTRACT:
                Object[] dynamicFieldValues = payload.getValues();
                JsonExtractSplitterStrategy jsonExtractSplitter = (JsonExtractSplitterStrategy) splitter;
                if (!StringUtils.EMPTY.equals(dynamicFieldValues[0])) {
                    if (jsonExtractSplitter.getDecoratedSplitter() instanceof GreaterThanEqualsSplitterStrategy) {
                        qb = new RangeQueryBuilder(payload.getFieldName());
                        ((RangeQueryBuilder) qb).gte(dynamicFieldValues[0]);
                    } else if (jsonExtractSplitter.getDecoratedSplitter() instanceof LessThanEqualsSplitterStrategy) {
                        qb = new RangeQueryBuilder(payload.getFieldName());
                        ((RangeQueryBuilder) qb).lte(dynamicFieldValues[0]);
                    } else {
                        qb = new MatchQueryBuilder(payload.getFieldName(), dynamicFieldValues[0]);
                    }
                    qb = build(qb);
                } else {
                    if (jsonExtractSplitter.getDecoratedSplitter() instanceof IsNullSplitterStrategy) {
                        qb = buildIsNullQuery();
                    } else {
                        qb = buildIsNotNullAndEmpty();
                    }
                }
                break;
            default:
                throw new IllegalArgumentException(Operator.UNKNOWN_OPERATOR);
        }
        return qb;
    }

    private QueryBuilder buildIsNotNullAndEmpty() {
        BoolQueryBuilder isNotNullAndNotEmpty = new BoolQueryBuilder();
        isNotNullAndNotEmpty.must(build(new ExistsQueryBuilder(payload.getFieldName())));
        return isNotNullAndNotEmpty;
    }

    protected abstract QueryBuilder build(QueryBuilder queryBuilder);

    private QueryBuilder buildIsNullQuery() {
        BoolQueryBuilder existsWithEmpty = new BoolQueryBuilder();
        existsWithEmpty.mustNot(build(new ExistsQueryBuilder(payload.getFieldName())));
        return existsWithEmpty;
    }

    public QueryBuilder buildEachQuery(Operator operator,
                                       QueryPayload queryPayload,
                                       SplitterStrategy splitter) {
        this.operator = operator;
        this.payload = queryPayload;
        this.splitter = splitter;
        return buildQueryBuilder();
    }

}
