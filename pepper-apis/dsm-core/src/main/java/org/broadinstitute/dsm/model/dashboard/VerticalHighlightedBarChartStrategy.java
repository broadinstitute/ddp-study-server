package org.broadinstitute.dsm.model.dashboard;

import java.util.Objects;
import java.util.OptionalInt;
import java.util.stream.IntStream;

public class VerticalHighlightedBarChartStrategy extends VerticalBarChartStrategy {

    public VerticalHighlightedBarChartStrategy(ChartStrategyPayload chartStrategyPayload) {
        super(chartStrategyPayload);
    }

    @Override
    protected void fillLabelsValues() {
        super.fillLabelsValues();
        OptionalInt maybeHighlightOptionIndex = IntStream.range(0, getDashboardDto().getColors().size())
                .filter(index -> Objects.nonNull(getDashboardDto().getColors().get(index)))
                .findFirst();
        maybeHighlightOptionIndex.ifPresent(this::swapHighlightedToFirst);
    }

    private void swapHighlightedToFirst(int index) {
        Object first = labels.get(0);
        labels.set(0, labels.get(index));
        labels.set(index, first);
    }
}
