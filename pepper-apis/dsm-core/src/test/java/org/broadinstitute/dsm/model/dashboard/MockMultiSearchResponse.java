package org.broadinstitute.dsm.model.dashboard;

import org.apache.lucene.search.TotalHits;
import org.elasticsearch.action.search.MultiSearchResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHits;

import java.io.IOException;

class MockMultiSearchResponse extends MultiSearchResponse {

    public MockMultiSearchResponse() throws IOException {
        super(null);
    }

    @Override
    public Item[] getResponses() {
        SearchResponse searchResponse = null;
        try {
            searchResponse = new SearchResponse(null) {
                @Override
                public SearchHits getHits() {
                    TotalHits totalHits = new TotalHits(VerticalHighlightedBarChartStrategyTest.TOTAL_HITS,
                            TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO);
                    return new SearchHits(null, totalHits, 0L);
                }
            };
        } catch (IOException e) {
            e.printStackTrace();
        }
        SearchResponse searchResponse2 = null;
        try {
            searchResponse2 = new SearchResponse(null) {
                @Override
                public SearchHits getHits() {
                    TotalHits totalHits2 = new TotalHits(VerticalHighlightedBarChartStrategyTest.TOTAL_HITS2,
                            TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO);
                    return new SearchHits(null, totalHits2, 0L);
                }
            };
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new Item[] {new Item(searchResponse, null), new Item(searchResponse2, null)};
    }
}
