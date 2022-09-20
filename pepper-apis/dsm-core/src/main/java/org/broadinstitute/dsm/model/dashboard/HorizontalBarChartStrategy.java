package org.broadinstitute.dsm.model.dashboard;

import java.util.List;

import org.broadinstitute.dsm.db.dto.dashboard.DashboardLabelDto;

class HorizontalBarChartStrategy extends BarChartStrategy {

    public HorizontalBarChartStrategy(ChartStrategyPayload chartStrategyPayload) {
        super(chartStrategyPayload);
    }

    @Override
    protected void fillData(List<Object> x, List<Object> y, DashboardLabelDto label, long totalHits) {
        y.add(label.getLabelName());
        x.add(totalHits);
    }
}
