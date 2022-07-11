package org.broadinstitute.dsm.model.dashboard;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.broadinstitute.dsm.db.dto.dashboard.DashboardDto;
import org.elasticsearch.action.search.MultiSearchResponse;

public class BarChartStrategy extends BasicChartStrategy implements Supplier<DashboardData> {


    public BarChartStrategy(ChartStrategyPayload chartStrategyPayload) {
        super(chartStrategyPayload);
    }

    @Override
    public BarChartData get() {
        BarChartData barChartData = null;
        List<Object> y = new ArrayList<>();
        List<Object> x = new ArrayList<>();
        DashboardDto dashboardDto = chartStrategyPayload.getDashboardDto();
        MultiSearchResponse msearch = chartStrategyPayload.getMultiSearchResponse();
        if (DisplayType.VERTICAL_BAR_CHART == dashboardDto.getDisplayType()) {
            for (int i = 0; i < dashboardDto.getLabels().size(); i++) {
                x.add(dashboardDto.getLabels().get(i).getLabelName());
                MultiSearchResponse.Item response = msearch.getResponses()[i];
                y.add(response.getResponse().getHits().getTotalHits());
            }
            barChartData = new BarChartData(dashboardDto.getDisplayType(), dashboardDto.getColors(), dashboardDto.getSize(),
                    x, y, dashboardDto.getDisplayText(), dashboardDto.getOrder());
        } else if (DisplayType.HORIZONTAL_BAR_CHART == dashboardDto.getDisplayType()) {
            for (int i = 0; i < dashboardDto.getLabels().size(); i++) {
                y.add(dashboardDto.getLabels().get(i).getLabelName());
                MultiSearchResponse.Item response = msearch.getResponses()[i];
                x.add(response.getResponse().getHits().getTotalHits());
            }
            barChartData = new BarChartData(dashboardDto.getDisplayType(), dashboardDto.getColors(), dashboardDto.getSize(),
                    x, y, dashboardDto.getDisplayText(), dashboardDto.getOrder());
        }
        return barChartData;
    }
}
