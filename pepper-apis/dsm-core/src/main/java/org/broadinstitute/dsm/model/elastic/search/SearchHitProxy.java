package org.broadinstitute.dsm.model.elastic.search;

import java.util.Map;

import org.elasticsearch.search.SearchHit;

class SearchHitProxy {

    private SearchHit searchHit;

    public SearchHitProxy(SearchHit searchHit) {
        this.searchHit = searchHit;
    }

    Map<String, Object> getSourceAsMap() {
        return searchHit.getSourceAsMap();
    }

}
