package org.broadinstitute.dsm.model.dashboard;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.broadinstitute.dsm.db.dto.dashboard.DashboardDto;
import org.elasticsearch.action.search.MultiSearchResponse;

public class DonutChartStrategy extends BasicChartStrategy implements Supplier<DashboardData> {
    public DonutChartStrategy(ChartStrategyPayload chartStrategyPayload) {
        super(chartStrategyPayload);
    }

    @Override
    public DonutData get() {
        List<String> labels = new ArrayList<>();
        List<Long> values = new ArrayList<>();
        DashboardDto dashboardDto = chartStrategyPayload.getDashboardDto();
        for (int i = 0; i < dashboardDto.getLabels().size(); i++) {
            labels.add(dashboardDto.getLabels().get(i).getLabelName());
            MultiSearchResponse.Item response = chartStrategyPayload.getMultiSearchResponse().getResponses()[i];
            values.add(response.getResponse().getHits().getTotalHits());
        }
        return new DonutData(dashboardDto.getDisplayType(), dashboardDto.getColors(), dashboardDto.getSize(), values, labels,
                dashboardDto.getDisplayText(), dashboardDto.getOrder());
    }
}
