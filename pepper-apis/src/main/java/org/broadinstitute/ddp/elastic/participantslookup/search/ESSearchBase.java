package org.broadinstitute.ddp.elastic.participantslookup.search;

import java.io.IOException;
import java.util.Map;

import com.google.gson.Gson;
import org.broadinstitute.ddp.elastic.ElasticSearchQueryUtil;
import org.broadinstitute.ddp.json.admin.participantslookup.ParticipantsLookupResultRowBase;
import org.broadinstitute.ddp.util.GsonUtil;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.AbstractQueryBuilder;

public abstract class ESSearchBase<T extends ParticipantsLookupResultRowBase> {

    static final String SOURCE__PROFILE = "profile";
    static final String SOURCE__GOVERNED_USERS = "governedUsers";
    static final String SOURCE__STATUS = "status";
    static final String SOURCE__INVITATIONS = "invitations";
    static final String SOURCE__PROXIES = "proxies";

    // Field value (to fetch from source 'invitations'
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
    protected int resultMaxCount;


    public ESSearchBase(RestHighLevelClient esClient) {
        this.esClient = esClient;
    }

    public Map<String, T> search() throws IOException {
        var queryBuilder = createQuery();
        var response = ElasticSearchQueryUtil.search(
                esClient, esIndex, fetchSource, queryBuilder, resultMaxCount);
        return readResults(response);
    }

    protected abstract AbstractQueryBuilder createQuery();

    protected abstract Map<String, T> readResults(SearchResponse response);

    public String getQuery() {
        return query;
    }

    public ESSearchBase setQuery(String query) {
        this.query = query;
        return this;
    }

    public String getEsIndex() {
        return esIndex;
    }

    public ESSearchBase setEsIndex(String esIndex) {
        this.esIndex = esIndex;
        return this;
    }

    public String[] getFetchSource() {
        return fetchSource;
    }

    public ESSearchBase setFetchSource(String[] fetchSource) {
        this.fetchSource = fetchSource;
        return this;
    }

    public int getResultMaxCount() {
        return resultMaxCount;
    }

    public ESSearchBase setResultMaxCount(int resultMaxCount) {
        this.resultMaxCount = resultMaxCount;
        return this;
    }
}
