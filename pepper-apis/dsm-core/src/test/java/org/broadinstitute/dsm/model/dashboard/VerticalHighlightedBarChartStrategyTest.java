package org.broadinstitute.dsm.model.dashboard;

import java.io.IOException;
import java.util.Arrays;
import java.util.function.Supplier;

import org.broadinstitute.dsm.db.dto.dashboard.DashboardDto;
import org.broadinstitute.dsm.db.dto.dashboard.DashboardLabelDto;
import org.junit.Assert;
import org.junit.Test;

public class VerticalHighlightedBarChartStrategyTest {

    public static final long TOTAL_HITS = 10;
    public static final long TOTAL_HITS2 = 15;


    @Test
    public void get() throws IOException {
        String darkIndigo = "dark indigo";
        String yourCenter = "Your center";
        DashboardLabelDto mainLabel = new DashboardLabelDto.Builder()
                .withColor(darkIndigo)
                .withLabelName(yourCenter)
                .build();
        String labelName = "centers de identified";
        DashboardLabelDto label2 = new DashboardLabelDto.Builder()
                .withLabelName(labelName)
                .build();
        DashboardDto dashboardDto = new DashboardDto.Builder()
                .withDisplayType(DisplayType.VERTICAL_HIGHLIGHTED_BAR_CHART)
                .withLabels(Arrays.asList(mainLabel, label2))
                .build();
        MockMultiSearchResponse multiSearchResponse = new MockMultiSearchResponse();
        ChartStrategyPayload chartStrategyPayload = new ChartStrategyPayload(dashboardDto, multiSearchResponse);
        Supplier<DashboardData> chartStrategy = new VerticalHighlightedBarChartStrategy(chartStrategyPayload);
        BarChartData dashboardData = (BarChartData) chartStrategy.get();
        Assert.assertEquals(TOTAL_HITS, dashboardData.getValuesY().get(0));
        Assert.assertEquals(TOTAL_HITS2, dashboardData.getValuesY().get(1));
        Assert.assertEquals(yourCenter, dashboardData.getValuesX().get(0));
        Assert.assertEquals(darkIndigo, dashboardData.getColor().get(0));
    }


}

