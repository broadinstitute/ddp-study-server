package org.broadinstitute.dsm.model.dashboard;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.broadinstitute.dsm.db.dto.dashboard.DashboardDto;
import org.broadinstitute.dsm.db.dto.dashboard.DashboardLabelDto;
import org.elasticsearch.action.search.MultiSearchResponse;

abstract class BasicChartStrategy implements Supplier<DashboardData> {

    protected ChartStrategyPayload chartStrategyPayload;
    protected List<Object> values;
    protected List<Object> labels;

    public BasicChartStrategy(ChartStrategyPayload chartStrategyPayload) {
        this.chartStrategyPayload = chartStrategyPayload;
        values = new ArrayList<>();
        labels = new ArrayList<>();
    }

    protected abstract void fillData(List<Object> x, List<Object> y, DashboardLabelDto label, long totalHits);

    protected DashboardDto getDashboardDto() {
        return chartStrategyPayload.getDashboardDto();
    }

    @Override
    public DashboardData get() {
        fillLabelsValues();
        return buildData();
    }

    protected void fillLabelsValues() {
        DashboardDto dashboardDto = chartStrategyPayload.getDashboardDto();
        MultiSearchResponse msearch = chartStrategyPayload.getMultiSearchResponse();
        for (int i = 0; i < dashboardDto.getLabels().size(); i++) {
            MultiSearchResponse.Item response = msearch.getResponses()[i];
            fillData(values, labels, dashboardDto.getLabels().get(i), response.getResponse().getHits().getTotalHits());
        }
    }

    protected abstract DashboardData buildData();
}
