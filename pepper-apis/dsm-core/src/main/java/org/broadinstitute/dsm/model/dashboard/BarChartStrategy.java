package org.broadinstitute.dsm.model.dashboard;

abstract class BarChartStrategy extends BasicChartStrategy {


    public BarChartStrategy(ChartStrategyPayload chartStrategyPayload) {
        super(chartStrategyPayload);
    }

    @Override
    protected DashboardData buildData() {
        return new BarChartData(getDashboardDto().getDisplayType(), getDashboardDto().getColors(), getDashboardDto().getSize(),
                values, labels, getDashboardDto().getDisplayText(), getDashboardDto().getOrder());
    }
}
