package org.broadinstitute.dsm.model.dashboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import org.broadinstitute.dsm.db.dto.dashboard.DashboardDto;
import org.broadinstitute.dsm.db.dto.dashboard.DashboardLabelDto;
import org.elasticsearch.action.search.MultiSearchResponse;

public class CountStrategy implements Supplier<DashboardData> {

    private ChartStrategyPayload payload;

    public CountStrategy(ChartStrategyPayload payload) {
        this.payload = payload;
    }

    @Override
    public DashboardData get() {
        DashboardDto dashboardDto = payload.getDashboardDto();
        DashboardLabelDto dashboardLabelDto = dashboardDto.getLabels().get(0);
        MultiSearchResponse.Item response = payload.getMultiSearchResponse().getResponses()[0];
        return new CountData(dashboardDto.getDisplayType(), Collections.emptyList(),
                dashboardDto.getSize(), dashboardDto.getDisplayText(), dashboardDto.getOrder(),
                response.getResponse().getHits().getTotalHits()
        );
    }
}