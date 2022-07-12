package org.broadinstitute.dsm.model.dashboard;

import lombok.Getter;
import org.broadinstitute.dsm.db.dto.dashboard.DashboardDto;
import org.elasticsearch.action.search.MultiSearchResponse;

@Getter
class ChartStrategyPayload {
    private DashboardDto dashboardDto;
    private MultiSearchResponse multiSearchResponse;

    public ChartStrategyPayload(DashboardDto dashboardDto, MultiSearchResponse multiSearchResponse) {
        this.dashboardDto = dashboardDto;
        this.multiSearchResponse = multiSearchResponse;
    }
}
