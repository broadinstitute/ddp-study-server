package org.broadinstitute.ddp.elastic.participantslookup.search;

import static org.broadinstitute.ddp.elastic.ElasticSearchQueryBuilderUtil.and;
import static org.broadinstitute.ddp.elastic.ElasticSearchQueryBuilderUtil.notEmptyFieldQuery;
import static org.broadinstitute.ddp.elastic.ElasticSearchQueryUtil.getJsonObject;
import static org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupField.GOVERNED_USERS;
import static org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupIndexType.USERS;
import static org.broadinstitute.ddp.elastic.participantslookup.search.ESSearchUtil.GSON;
import static org.broadinstitute.ddp.elastic.participantslookup.search.ESSearchUtil.SOURCE__GOVERNED_USERS;
import static org.broadinstitute.ddp.elastic.participantslookup.search.ESSearchUtil.SOURCE__PROFILE;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonObject;
import org.broadinstitute.ddp.elastic.ElasticSearchQueryExecutor;
import org.broadinstitute.ddp.elastic.participantslookup.model.ESUsersIndexResultRow;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;


/**
 * Search step 1.
 * In index "users" search proxies only.
 */
public class ESUsersProxiesSearch extends ElasticSearchQueryExecutor {

    protected final Map<String, String> governedUserToProxy;

    public ESUsersProxiesSearch(RestHighLevelClient esClient, Map<String, String> governedUserToProxy) {
        super(esClient);
        this.governedUserToProxy = governedUserToProxy;
    }

    /**
     * Create query:
     * <pre>
     * - query substring 'query' in all searchable fields of index "users";
     * - query only proxy-users having not empty 'governedUsers';
     * - join queries with 'AND'.
     * </pre>
     * @return
     */
    public QueryBuilder createQuery() {
        var queryBuilder = ESSearchUtil.queryLookupFieldsOfIndex(USERS, query);
        var governedUsersExistBuilder = notEmptyFieldQuery(GOVERNED_USERS.getEsField());
        return and(queryBuilder, governedUsersExistBuilder);
    }

    public Map<String, ESUsersIndexResultRow> readResults(SearchResponse response) {
        Map<String, ESUsersIndexResultRow> resultData = new HashMap<>();
        for (var hit : response.getHits()) {
            var hitAsJson = getJsonObject(hit);
            var userData = GSON.fromJson(hitAsJson.get(SOURCE__PROFILE), ESUsersIndexResultRow.class);
            readGovernedUserGuids(governedUserToProxy, hitAsJson, userData);
            resultData.put(userData.getGuid(), userData);
        }
        return resultData;
    }

    private void readGovernedUserGuids(Map<String, String> governedUserToProxy, JsonObject hitAsJson, ESUsersIndexResultRow userData) {
        var governedUsers = GSON.fromJson(hitAsJson.get(SOURCE__GOVERNED_USERS), String[].class);
        for (String gu : governedUsers) {
            userData.getGovernedUsers().add(gu);
            if (governedUserToProxy != null) {
                governedUserToProxy.put(gu, userData.getGuid());
            }
        }
    }
}
