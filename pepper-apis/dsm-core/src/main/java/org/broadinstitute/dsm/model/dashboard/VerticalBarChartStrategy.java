package org.broadinstitute.dsm.model.dashboard;

import java.util.List;

import org.broadinstitute.dsm.db.dto.dashboard.DashboardLabelDto;

class VerticalBarChartStrategy extends BarChartStrategy {

    public VerticalBarChartStrategy(ChartStrategyPayload chartStrategyPayload) {
        super(chartStrategyPayload);
    }

    @Override
    protected void fillData(List<Object> x, List<Object> y, DashboardLabelDto label, long totalHits) {
        x.add(label.getLabelName());
        y.add(totalHits);
    }
}
