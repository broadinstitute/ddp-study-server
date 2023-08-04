package org.broadinstitute.dsm.model.dashboard;

import org.elasticsearch.action.search.MultiSearchResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHits;

class MockMultiSearchResponse extends MultiSearchResponse {

    public MockMultiSearchResponse() {
        super(null);
    }

    @Override
    public Item[] getResponses() {
        SearchResponse searchResponse = new SearchResponse() {
            @Override
            public SearchHits getHits() {
                return new SearchHits(null, VerticalHighlightedBarChartStrategyTest.TOTAL_HITS, 0L);
            }
        };
        SearchResponse searchResponse2 = new SearchResponse() {
            @Override
            public SearchHits getHits() {
                return new SearchHits(null, VerticalHighlightedBarChartStrategyTest.TOTAL_HITS2, 0L);
            }
        };
        return new Item[] {new Item(searchResponse, null), new Item(searchResponse2, null)};
    }
}