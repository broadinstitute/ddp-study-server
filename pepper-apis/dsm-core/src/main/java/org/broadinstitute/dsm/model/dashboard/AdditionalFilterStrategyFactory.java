package org.broadinstitute.dsm.model.dashboard;

public class AdditionalFilterStrategyFactory {

    private final QueryBuildPayload queryBuildPayload;

    public AdditionalFilterStrategyFactory(QueryBuildPayload queryBuildPayload) {
        this.queryBuildPayload = queryBuildPayload;
    }

    public AdditionalFilterStrategy create() {
        AdditionalFilterStrategy strategy = new AdditionalFilterStrategy(queryBuildPayload);
        if (DisplayType.COUNT.equals(queryBuildPayload.getDisplayType())) {
            strategy = new CountAdditionalFilterStrategy(queryBuildPayload);
        }
        return strategy;
    }

}
