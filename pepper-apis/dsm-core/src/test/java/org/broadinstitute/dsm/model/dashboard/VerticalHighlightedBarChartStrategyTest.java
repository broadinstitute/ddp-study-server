package org.broadinstitute.dsm.model.dashboard;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;
import java.util.function.Supplier;

import org.apache.lucene.search.TotalHits;
import org.broadinstitute.dsm.db.dto.dashboard.DashboardDto;
import org.broadinstitute.dsm.db.dto.dashboard.DashboardLabelDto;
import org.elasticsearch.action.search.MultiSearchResponse;
import org.elasticsearch.action.search.MultiSearchResponse.Item;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHits;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class VerticalHighlightedBarChartStrategyTest {

    public static final long TOTAL_HITS = 10;
    public static final long TOTAL_HITS2 = 15;
    static MultiSearchResponse mockedSearchResponse = mock(MultiSearchResponse.class);
    static SearchResponse mockSearchResponse1 = mock(SearchResponse.class);
    static SearchResponse mockSearchResponse2 = mock(SearchResponse.class);

    @BeforeClass
    public static void setup() {
        SearchHits searchHits = new SearchHits(null, new TotalHits(TOTAL_HITS,
                TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO), 0L);
        SearchHits searchHits2 = new SearchHits(null, new TotalHits(TOTAL_HITS2,
                TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO), 0L);
        when(mockSearchResponse1.getHits()).thenReturn(searchHits);
        when(mockSearchResponse2.getHits()).thenReturn(searchHits2);
        Item[] itemArray = new Item[] {new Item(mockSearchResponse1, null),
                new Item(mockSearchResponse2, null)};
        when(mockedSearchResponse.getResponses()).thenReturn(itemArray);
    }

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
        ChartStrategyPayload chartStrategyPayload = new ChartStrategyPayload(dashboardDto, mockedSearchResponse);
        Supplier<DashboardData> chartStrategy = new VerticalHighlightedBarChartStrategy(chartStrategyPayload);
        BarChartData dashboardData = (BarChartData) chartStrategy.get();
        Assert.assertEquals(TOTAL_HITS, dashboardData.getValuesY().get(0));
        Assert.assertEquals(TOTAL_HITS2, dashboardData.getValuesY().get(1));
        Assert.assertEquals(yourCenter, dashboardData.getValuesX().get(0));
        Assert.assertEquals(darkIndigo, dashboardData.getColor().get(0));
    }
}

