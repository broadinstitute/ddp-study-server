package org.broadinstitute.ddp.elastic;

import java.io.IOException;
import java.util.Map;
import java.util.function.Supplier;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;


/**
 * Executes query to ElasticSearch.
 */
public class ElasticSearchQueryExecutor<T> {

    protected final RestHighLevelClient esClient;

    protected String query;
    protected String esIndex;
    protected String[] fetchSource;
    protected Integer resultMaxCount;


    public ElasticSearchQueryExecutor(RestHighLevelClient esClient) {
        this.esClient = esClient;
    }

    /**
     * Main class providing searching in ElasticSearch.
     *
     * @param queryBuilder method implementing building of {@link QueryBuilder} to be used to search in ElasticSearch
     * @param resultsReader method implementing search result processing
     * @return Map with results: key - guid of found record, T - type of record
     */
    public Map<String, T> search(Supplier<QueryBuilder> queryBuilder, QueryResultsReader resultsReader) throws IOException {
        return resultsReader.readResults(
                ElasticSearchQueryUtil.search(esClient, esIndex, fetchSource, queryBuilder.get(), resultMaxCount)
        );
    }

    public String getQuery() {
        return query;
    }

    public ElasticSearchQueryExecutor setQuery(String query) {
        this.query = query;
        return this;
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
    public interface QueryResultsReader<T> {
        Map<String, T> readResults(SearchResponse response);
    }
}
