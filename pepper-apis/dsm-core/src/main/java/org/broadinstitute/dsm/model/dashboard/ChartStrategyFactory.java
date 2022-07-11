package org.broadinstitute.dsm.model.dashboard;

import java.util.function.Supplier;

public class ChartStrategyFactory {
    private ChartStrategyPayload payload;

    public ChartStrategyFactory(ChartStrategyPayload payload) {
        this.payload = payload;
    }

    public Supplier<DashboardData> of() {
        Supplier<DashboardData> basicChartStrategy;
        switch (payload.getDashboardDto().getDisplayType()) {
            case HORIZONTAL_BAR_CHART:
            case VERTICAL_BAR_CHART:
                basicChartStrategy = new BarChartStrategy(payload);
                break;
            case DONUT:
                basicChartStrategy = new DonutChartStrategy(payload);
                break;
            default:
                basicChartStrategy = new NullChartStrategy();
                break;
        }
        return basicChartStrategy;
    }

}
