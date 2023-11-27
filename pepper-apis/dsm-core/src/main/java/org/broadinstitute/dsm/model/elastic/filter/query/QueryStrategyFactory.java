package org.broadinstitute.dsm.model.elastic.filter.query;

import java.util.Arrays;
import java.util.List;

import org.broadinstitute.dsm.model.participant.Util;
import org.broadinstitute.dsm.statics.ESObjectConstants;

public class QueryStrategyFactory {

    private static final List<String> NON_ACTIVITY_OBJECTS = Arrays.asList(ESObjectConstants.FILES, ESObjectConstants.ADDRESS,
            ESObjectConstants.DSM, ESObjectConstants.PROFILE, ESObjectConstants.INVITATIONS, ESObjectConstants.DATA);

    public static BuildQueryStrategy create(QueryStrategyFactoryPayload queryStrategyFactoryPayload) {
        BuildQueryStrategy result;
        if (!Util.isUnderDsmKey(queryStrategyFactoryPayload.getAlias())) {
            if (NON_ACTIVITY_OBJECTS.contains(queryStrategyFactoryPayload.getAlias())) {
                result = buildNonActivityQueryStategy(queryStrategyFactoryPayload);
            } else {
                result = BaseActivitiesStrategy.of(queryStrategyFactoryPayload.getParser(),
                    queryStrategyFactoryPayload.getOperator(), queryStrategyFactoryPayload.getBaseQueryBuilder());
            }
        } else {
            result = buildNonActivityQueryStategy(queryStrategyFactoryPayload);
        }
        return result;
    }

    private static BuildQueryStrategy buildNonActivityQueryStategy(QueryStrategyFactoryPayload queryStrategyFactoryPayload) {
        BuildQueryStrategy queryStrategy = queryStrategyFactoryPayload.getOperator().getQueryStrategy();
        queryStrategy.setBaseQueryBuilder(queryStrategyFactoryPayload.getBaseQueryBuilder());
        queryStrategyFactoryPayload.getBaseQueryBuilder().setOperator(queryStrategyFactoryPayload.getOperator());
        return queryStrategy;
    }

}
