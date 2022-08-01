package org.broadinstitute.dsm.model.elastic.filter.query;

public class QueryStrategyFactory {


    private QueryStrategyFactoryPayload queryStrategyFactoryPayload;

    public QueryStrategyFactory(QueryStrategyFactoryPayload queryStrategyFactoryPayload) {

        this.queryStrategyFactoryPayload = queryStrategyFactoryPayload;
    }


    public BuildQueryStrategy create() {
        return null;
    }


}
