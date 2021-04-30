package org.broadinstitute.ddp.elastic;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;

/**
 * Helper methods used to search in ElasticSearch indices
 */
public class ElasticSearchQueryUtil {

    public static SearchRequest prepareSearching(
            String esIndex,
            String[] fetchSource,
            AbstractQueryBuilder abstractQueryBuilder,
            int resultsMaxCount
    ) {
        SearchRequest searchRequest = new SearchRequest(esIndex);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(abstractQueryBuilder);
        searchSourceBuilder.from(0);
        searchSourceBuilder.size(resultsMaxCount);
        searchSourceBuilder.fetchSource(fetchSource, null);
        searchRequest.source(searchSourceBuilder);
        return searchRequest;
    }

    public static String addWildcards(String query) {
        return '*' + query + '*';
    }

    public static JsonElement getJsonElement(SearchHit hit, String nodeName) {
        JsonObject participantJson = new JsonParser().parse(hit.getSourceAsString()).getAsJsonObject();
        return participantJson.get(nodeName);
    }
}
