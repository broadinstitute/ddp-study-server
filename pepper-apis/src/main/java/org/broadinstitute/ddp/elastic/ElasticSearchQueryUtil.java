package org.broadinstitute.ddp.elastic;

import java.io.IOException;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
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
            QueryBuilder queryBuilder,
            Integer resultMaxCount) throws IOException {
        var searchRequest = prepareSearching(esIndex, fetchSource, queryBuilder, resultMaxCount);
        return esClient.search(searchRequest, RequestOptions.DEFAULT);
    }

    public static SearchRequest prepareSearching(
            String esIndex,
            String[] fetchSource,
            QueryBuilder queryBuilder,
            Integer resultsMaxCount
    ) {
        SearchRequest searchRequest = new SearchRequest(esIndex);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(queryBuilder);
        searchSourceBuilder.from(0);
        if (resultsMaxCount != null) {
            searchSourceBuilder.size(resultsMaxCount);
        }
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

    /**
     * Escape characters which are part of the ElasticSearch query syntax.
     */
    public static String escapeQuerySyntaxCharacters(String s) {
        if (StringUtils.isEmpty(s)) {
            return s;
        } else {
            var sb = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '\\'
                        || c == '+'
                        || c == '-'
                        || c == '!'
                        || c == '('
                        || c == ')'
                        || c == ':'
                        || c == '^'
                        || c == '['
                        || c == ']'
                        || c == '\"'
                        || c == '{'
                        || c == '}'
                        || c == '~'
                        || c == '*'
                        || c == '?'
                        || c == '|'
                        || c == '&'
                        || c == '/') {
                    sb.append('\\');
                }
                sb.append(c);
            }
            return sb.toString();
        }
    }

    /**
     * In most cases invitation GUID contains character '-' (at least on the web page it entered with it),
     * but in DB it can or cannot contain '-' (i.e. it could be stored without '-').
     * Therefore it is searched by 'query' as it is sent from frontend
     * and by same 'query' but with '-' removed from it.
     * This method removes characters '-' from 'query' string (guessing that it is invitationId or it's fragment).
     */
    public static String normalizeInvitationGuid(String invitationGuid) {
        if (invitationGuid != null) {
            return invitationGuid.replaceAll("-", "");
        }
        return invitationGuid;
    }
}
