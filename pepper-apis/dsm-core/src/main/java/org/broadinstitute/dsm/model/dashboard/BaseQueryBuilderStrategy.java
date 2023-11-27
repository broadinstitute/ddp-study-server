package org.broadinstitute.dsm.model.dashboard;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.search.join.ScoreMode;
import org.broadinstitute.dsm.model.elastic.filter.Operator;
import org.broadinstitute.dsm.model.elastic.filter.query.BuildQueryStrategy;
import org.broadinstitute.dsm.model.elastic.filter.query.QueryPayload;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
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
        ((BoolQueryBuilder) finalQuery).must(queryBuilder);
        return queryBuilder;
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

    public static int count(String str, String regex) {
        int i = 0;
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(str);
        while (m.find()) {
            m.group();
            i++;
        }
        return i;
    }

    private void mightNeedLater(AbstractQueryBuilder finalQuery){
        // if esNestedPath = activities same as datePeriodField start
        if (queryBuildPayload.getStartDate() != null
                && StringUtils.isNotBlank(queryBuildPayload.getLabel().getDashboardFilterDto().getDatePeriodField())) {
            String datePeriodField = queryBuildPayload.getLabel().getDashboardFilterDto().getDatePeriodField();
            int dots = count(datePeriodField, "\\.+");
            if (dots >= 1) {
                //1 it is profile
                BoolQueryBuilder single = new BoolQueryBuilder();
                single.must(new RangeQueryBuilder(queryBuildPayload.getLabel().getDashboardFilterDto().getDatePeriodField())
                        .gte(queryBuildPayload.getStartDate()));
                single.must(new RangeQueryBuilder(queryBuildPayload.getLabel().getDashboardFilterDto().getDatePeriodField())
                        .lte(queryBuildPayload.getEndDate()));

                String nestedPath = datePeriodField.substring(0, datePeriodField.lastIndexOf("."));
                if (dots > 1 && datePeriodField.lastIndexOf(".") > -1 || !nestedPath.startsWith(ElasticSearchUtil.PROFILE)) {
                    //nested
                    BoolQueryBuilder nested = new BoolQueryBuilder();
                    nested.must(QueryBuilders.nestedQuery(nestedPath, single, ScoreMode.Avg));
                    ((BoolQueryBuilder) finalQuery).must(nested);
                } else {
                    ((BoolQueryBuilder) finalQuery).must(single);
                }
            }
            //empty  = default profile.createdAt????
        }
    }
}
