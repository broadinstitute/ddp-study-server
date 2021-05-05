package org.broadinstitute.ddp.elastic;

import java.io.IOException;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;

/**
 * Helper methods used to search in ElasticSearch indices
 */
public class ElasticSearchQueryUtil {

    public static SearchResponse search(
            RestHighLevelClient esClient,
            String esIndex,
            String[] fetchSource,
            AbstractQueryBuilder queryBuilder,
            int resultMaxCount) throws IOException {
        var searchRequest = prepareSearching(esIndex, fetchSource, queryBuilder, resultMaxCount);
        return esClient.search(searchRequest, RequestOptions.DEFAULT);
    }

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

    public static JsonObject getJsonObject(SearchHit hit) {
        return new JsonParser().parse(hit.getSourceAsString()).getAsJsonObject();
    }
}
