package org.broadinstitute.dsm.model.dashboard;

import java.util.function.Supplier;

class ChartStrategyFactory {
    private ChartStrategyPayload payload;

    public ChartStrategyFactory(ChartStrategyPayload payload) {
        this.payload = payload;
    }

    public Supplier<DashboardData> of() {
        Supplier<DashboardData> basicChartStrategy;
        switch (payload.getDashboardDto().getDisplayType()) {
            case HORIZONTAL_BAR_CHART:
                basicChartStrategy = new HorizontalBarChartStrategy(payload);
                break;
            case VERTICAL_BAR_CHART:
                basicChartStrategy = new VerticalBarChartStrategy(payload);
                break;
            case VERTICAL_HIGHLIGHTED_BAR_CHART:
                basicChartStrategy = new VerticalHighlightedBarChartStrategy(payload);
                break;
            case DONUT_CHART:
                basicChartStrategy = new DonutChartStrategy(payload);
                break;
            case COUNT:
                basicChartStrategy = new CountStrategy(payload);
                break;
            default:
                basicChartStrategy = new NullChartStrategy();
                break;
        }
        return basicChartStrategy;
    }

}
