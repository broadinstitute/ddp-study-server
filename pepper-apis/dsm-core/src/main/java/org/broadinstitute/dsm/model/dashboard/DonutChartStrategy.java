package org.broadinstitute.dsm.model.dashboard;

import java.util.List;

import org.broadinstitute.dsm.db.dto.dashboard.DashboardLabelDto;

class DonutChartStrategy extends BasicChartStrategy {
    public DonutChartStrategy(ChartStrategyPayload chartStrategyPayload) {
        super(chartStrategyPayload);
    }

    @Override
    protected DashboardData buildData() {
        return new DonutData(getDashboardDto().getDisplayType(), getDashboardDto().getColors(), getDashboardDto().getSize(), values, labels,
                getDashboardDto().getDisplayText(), getDashboardDto().getOrder());
    }

    @Override
    protected void fillData(List<Object> x, List<Object> y, DashboardLabelDto label, long totalHits) {
        labels.add(label.getLabelName());
        values.add(totalHits);
    }
}
