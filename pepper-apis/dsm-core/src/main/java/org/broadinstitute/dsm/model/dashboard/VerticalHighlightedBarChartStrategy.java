package org.broadinstitute.dsm.model.dashboard;

import java.util.List;
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
        setHighlightedValueToFirst(index);
        setHighlightedColorToFirst(index);
        setHighlightedLabelToFirst(index);
    }

    private void setHighlightedValueToFirst(int index) {
        Object firstValue = labels.get(0);
        labels.set(0, labels.get(index));
        labels.set(index, firstValue);
    }

    private void setHighlightedColorToFirst(int index) {
        List<String> colors = chartStrategyPayload.getDashboardDto().getColors();
        String firstColor = colors.get(0);
        colors.set(0, colors.get(index));
        colors.set(index, firstColor);
    }

    private void setHighlightedLabelToFirst(int index) {
        Object firstLabel = values.get(0);
        values.set(0, values.get(index));
        values.set(index, firstLabel);
    }
}
