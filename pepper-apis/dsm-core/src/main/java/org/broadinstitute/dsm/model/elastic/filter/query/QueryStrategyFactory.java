package org.broadinstitute.dsm.model.elastic.filter.query;

import org.broadinstitute.dsm.model.participant.Util;

public class QueryStrategyFactory {

    public static BuildQueryStrategy create(QueryStrategyFactoryPayload queryStrategyFactoryPayload) {
        BuildQueryStrategy result;
        if (!Util.isUnderDsmKey(queryStrategyFactoryPayload.getAlias())) {
            BuildQueryStrategy queryStrategy = queryStrategyFactoryPayload.getOperator().getQueryStrategy();
            queryStrategy.setBaseQueryBuilder(queryStrategyFactoryPayload.getBaseQueryBuilder());
            queryStrategyFactoryPayload.getBaseQueryBuilder().setOperator(queryStrategyFactoryPayload.getOperator());
            result = queryStrategy;
//            result = BaseActivitiesStrategy.of(queryStrategyFactoryPayload.getParser(),
//                    queryStrategyFactoryPayload.getOperator(), queryStrategyFactoryPayload.getBaseQueryBuilder());
        } else {
            BuildQueryStrategy queryStrategy = queryStrategyFactoryPayload.getOperator().getQueryStrategy();
            queryStrategy.setBaseQueryBuilder(queryStrategyFactoryPayload.getBaseQueryBuilder());
            queryStrategyFactoryPayload.getBaseQueryBuilder().setOperator(queryStrategyFactoryPayload.getOperator());
            result = queryStrategy;
        }
        return result;
    }


}
