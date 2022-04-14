package org.broadinstitute.ddp.elastic;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;


/**
 * Executes query to ElasticSearch.<br>
 *
 * <p>Example:
 * <pre>
 * var indices = ElasticsearchServiceUtil.detectEsIndices(studyDto, of(ElasticSearchIndexType.PARTICIPANTS_STRUCTURED));
 * var participantsEsIndex = indices.get(ElasticSearchIndexType.PARTICIPANTS_STRUCTURED);
 * var elasticSearchQueryExecutor = new ElasticSearchQueryExecutor(esClient);
 * elasticSearchQueryExecutor
 *      .setResultMaxCount(resultsMaxCount)
 *      .setEsIndex(participantsEsIndex)
 *      .setFetchSource(new String[] {"profile"})
 *      .search(
 *          () -> QueryBuilders.matchQuery("profile.guid", participantGuid),
 *          (response) -> {
 *              Map&lt;String, ESUsersIndexResultRow&gt; resultData = new HashMap&lt;&gt;();
 *              for (var hit : response.getHits()) {
 *                 var hitAsJson = ElasticSearchQueryUtil.getJsonObject(hit);
 *                 var userData = gson.fromJson(hitAsJson.get("profile"), ESUsersIndexResultRow.class);
 *                 resultData.put(userData.getGuid(), userData);
 *              }
 *              return resultData;
 *          }
 *      );
 * </pre>
 */
public class ElasticSearchQueryExecutor<T> {

    protected final RestHighLevelClient esClient;

    protected String esIndex;
    protected String[] fetchSource;
    protected Integer resultMaxCount;


    public ElasticSearchQueryExecutor(RestHighLevelClient esClient) {
        this.esClient = esClient;
    }

    /**
     * Method providing searching in ElasticSearch. The result is returned as a Map of (String, T).
     *
     * @param queryBuilder method implementing building of {@link QueryBuilder} to be used to search in ElasticSearch
     * @param resultsReader method implementing search result processing
     * @return Map with results: key - guid of found record, T - type of record
     */
    public Map<String, T> search(Supplier<QueryBuilder> queryBuilder, QueryResultsReaderToMap resultsReader) throws IOException {
        return resultsReader.readResults(
                ElasticSearchQueryUtil.search(esClient, esIndex, fetchSource, queryBuilder.get(), resultMaxCount)
        );
    }

    /**
     * Method providing searching in ElasticSearch. The result is returned as a List of T.
     *
     * @param queryBuilder method implementing building of {@link QueryBuilder} to be used to search in ElasticSearch
     * @param resultsReader method implementing search result processing
     * @return List with results (elements of type T)
     */
    public List<T> search(Supplier<QueryBuilder> queryBuilder, QueryResultsReaderToList resultsReader) throws IOException {
        return resultsReader.readResults(
                ElasticSearchQueryUtil.search(esClient, esIndex, fetchSource, queryBuilder.get(), resultMaxCount)
        );
    }

    public ElasticSearchQueryExecutor setEsIndex(String esIndex) {
        this.esIndex = esIndex;
        return this;
    }

    public ElasticSearchQueryExecutor setFetchSource(String[] fetchSource) {
        this.fetchSource = fetchSource;
        return this;
    }

    public ElasticSearchQueryExecutor setResultMaxCount(Integer resultMaxCount) {
        this.resultMaxCount = resultMaxCount;
        return this;
    }


    @FunctionalInterface
    public interface QueryResultsReaderToMap<T> {
        Map<String, T> readResults(SearchResponse response);
    }

    @FunctionalInterface
    public interface QueryResultsReaderToList<T> {
        List<T> readResults(SearchResponse response);
    }
}
