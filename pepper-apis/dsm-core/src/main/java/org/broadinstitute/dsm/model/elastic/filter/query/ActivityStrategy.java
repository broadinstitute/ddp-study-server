package org.broadinstitute.dsm.model.elastic.filter.query;

import java.util.List;

import org.broadinstitute.dsm.model.elastic.export.parse.Parser;
import org.broadinstitute.dsm.model.elastic.filter.Operator;
import org.elasticsearch.index.query.QueryBuilder;

public class ActivityStrategy extends BaseActivitiesStrategy {

    protected ActivityStrategy(Parser parser, Operator operator, BaseQueryBuilder baseQueryBuilder) {
        super(operator, baseQueryBuilder, parser);
    }

    @Override
    protected List<QueryBuilder> getSpecificQueries() {
        BuildQueryStrategy queryStrategy = operator.getQueryStrategy();
        queryStrategy.setBaseQueryBuilder(baseQueryBuilder);
        return queryStrategy.build();
    }

}
