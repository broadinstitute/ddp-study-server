package org.broadinstitute.dsm.model.dashboard;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.elastic.filter.FilterParser;
import org.broadinstitute.dsm.model.elastic.filter.Operator;
import org.broadinstitute.dsm.model.elastic.filter.query.AbstractQueryBuilderFactory;
import org.broadinstitute.dsm.model.elastic.filter.query.BaseAbstractQueryBuilder;
import org.broadinstitute.dsm.model.elastic.filter.query.BuildQueryStrategy;
import org.broadinstitute.dsm.model.elastic.filter.query.QueryPayload;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class BaseQueryBuilderStrategy {

    private static final Logger logger = LoggerFactory.getLogger(BaseQueryBuilderStrategy.class);
    protected QueryBuildPayload queryBuildPayload;

    public BaseQueryBuilderStrategy(QueryBuildPayload queryBuildPayload) {
        this.queryBuildPayload = queryBuildPayload;
    }

    public QueryBuilder build() {
        AbstractQueryBuilder finalQuery = new BoolQueryBuilder();
        QueryBuilder queryBuilder;
        if (hasAdditionalFilter()) {
            queryBuilder = buildQueryForAdditionalFilter();
        } else {
            queryBuilder = buildQueryForNoAdditionalFilter();
        }
        if (queryBuildPayload.getStartDate() != null) {
            String filter = String.format("AND profile.createdAt >= '%s' AND profile.createdAt <= '%s'", queryBuildPayload.getStartDate(),
                    queryBuildPayload.getEndDate());
            //add endDate as well
            BaseAbstractQueryBuilder abstractQueryBuilder = AbstractQueryBuilderFactory.create(filter);
            abstractQueryBuilder.setParser(new FilterParser());
            ((BoolQueryBuilder) finalQuery).must(abstractQueryBuilder.build());
        }
        ((BoolQueryBuilder) finalQuery).must(queryBuilder);
        return finalQuery;
    }

    private boolean hasAdditionalFilter() {
        return StringUtils.isNotBlank(queryBuildPayload.getLabel().getDashboardFilterDto().getAdditionalFilter());
    }

    private QueryBuilder buildQueryForAdditionalFilter() {
        logger.info("Building search requests for additional filtering...");
        AdditionalFilterStrategyFactory additionalFilterStrategyFactory = new AdditionalFilterStrategyFactory(queryBuildPayload);
        return additionalFilterStrategyFactory.create().process();
    }

    protected QueryBuilder buildQueryForNoAdditionalFilter() {
        queryBuildPayload.getBaseQueryBuilder().setPayload(getQueryPayload());
        BuildQueryStrategy queryStrategy = Operator.EQUALS.getQueryStrategy();
        queryStrategy.setBaseQueryBuilder(queryBuildPayload.getBaseQueryBuilder());
        queryBuildPayload.getBaseQueryBuilder().setOperator(Operator.EQUALS);
        return queryBuildPayload.getBaseQueryBuilder().build(queryStrategy.build());
    }

    protected abstract QueryPayload getQueryPayload();
}
