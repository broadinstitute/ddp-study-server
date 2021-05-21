package org.broadinstitute.ddp.elastic.participantslookup.search;

import static org.broadinstitute.ddp.elastic.ElasticSearchQueryUtil.addWildcards;

import java.io.IOException;
import java.util.Map;
import java.util.function.Supplier;

import com.google.gson.Gson;
import org.broadinstitute.ddp.elastic.ElasticSearchQueryUtil;
import org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupField;
import org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupIndexType;
import org.broadinstitute.ddp.json.admin.participantslookup.ResultRowBase;
import org.broadinstitute.ddp.util.GsonUtil;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;


/**
 * Base class providing participants/users searching in ElasticSearch
 */
public abstract class ESSearch<T extends ResultRowBase> {

    static final String SOURCE__PROFILE = "profile";
    static final String SOURCE__GOVERNED_USERS = "governedUsers";
    static final String SOURCE__STATUS = "status";
    static final String SOURCE__INVITATIONS = "invitations";
    static final String SOURCE__PROXIES = "proxies";

    // Field value (to fetch from source 'invitations')
    static final String GUID = "guid";


    /**
     * ElasticSearch "_source" names (to fetch data from index "users")
     */
    public static final String[] USERS__INDEX__SOURCE = {
            SOURCE__PROFILE,
            SOURCE__GOVERNED_USERS
    };

    /**
     * ElasticSearch "_source" names (to fetch data from index "participants_structured")
     */
    public static final String[] PARTICIPANTS_STRUCTURED__INDEX__SOURCE = {
            SOURCE__STATUS,
            SOURCE__PROFILE,
            SOURCE__INVITATIONS,
            SOURCE__PROXIES
    };

    static final Gson gson = GsonUtil.standardGson();

    protected final RestHighLevelClient esClient;

    protected String query;
    protected String esIndex;
    protected String[] fetchSource;
    protected Integer resultMaxCount;


    public ESSearch(RestHighLevelClient esClient) {
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

    public ESSearch setQuery(String query) {
        this.query = query;
        return this;
    }

    public ESSearch setEsIndex(String esIndex) {
        this.esIndex = esIndex;
        return this;
    }

    public ESSearch setFetchSource(String[] fetchSource) {
        this.fetchSource = fetchSource;
        return this;
    }

    public ESSearch setResultMaxCount(Integer resultMaxCount) {
        this.resultMaxCount = resultMaxCount;
        return this;
    }

    /**
     * Helper method for building {@link QueryBuilder} to search by specified 'query' in list of fields
     * specified in {@link ESParticipantsLookupField} - taken only fields of a specified 'indexType' (or all index types).
     * @param indexType type of index
     * @param query string fragment to do full-text search
     * @return QueryBuilder - created query
     */
    public static QueryBuilder queryLookupFieldsOfIndex(ESParticipantsLookupIndexType indexType, String query) {
        var queryBuilder = QueryBuilders.queryStringQuery(addWildcards(query)).defaultOperator(Operator.OR);
        for (var field : ESParticipantsLookupField.values()) {
            if (field.isQueryFieldForIndex(indexType)) {
                queryBuilder.field(field.getEsField());
            }
        }
        return queryBuilder;
    }

    @FunctionalInterface
    public interface QueryResultsReader<T> {
        Map<String, T> readResults(SearchResponse response);
    }
}
