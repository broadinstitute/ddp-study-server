package org.broadinstitute.ddp.elastic.participantslookup.search;

import static org.broadinstitute.ddp.elastic.ElasticSearchQueryUtil.addWildcards;
import static org.broadinstitute.ddp.elastic.ElasticSearchQueryUtil.getJsonObject;
import static org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupField.isQueryFieldForIndex;
import static org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupIndexType.USERS;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonObject;
import org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupField;
import org.broadinstitute.ddp.elastic.participantslookup.model.ESUsersIndexResultRow;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;


/**
 * Search step 1.
 * In index "users" search proxies only.
 */
public class ESUsersProxiesSearch extends ESSearchBase {

    protected final Map<String, String> governedUserToProxy;

    public ESUsersProxiesSearch(
            RestHighLevelClient esClient,
            Map<String, String> governedUserToProxy) {
        super(esClient);
        this.governedUserToProxy = governedUserToProxy;
    }

    @Override
    protected AbstractQueryBuilder createQuery() {
        var queryBuilder = QueryBuilders.queryStringQuery(addWildcards(query)).defaultOperator(Operator.OR);
        for (var field : ESParticipantsLookupField.values()) {
            if (isQueryFieldForIndex(field, USERS)) {
                queryBuilder.field(field.getEsField());
            }
        }

        // query only proxy-users having not empty 'governedUsers'
        var governedUsersExistBuilder = QueryBuilders.regexpQuery("governedUsers", ".+");

        BoolQueryBuilder mainQueryBuilder = new BoolQueryBuilder();
        mainQueryBuilder
                .must(queryBuilder)
                .must(governedUsersExistBuilder);

        return mainQueryBuilder;
    }

    @Override
    protected Map<String, ESUsersIndexResultRow> readResults(SearchResponse response) {
        Map<String, ESUsersIndexResultRow> resultData = new HashMap<>();
        for (var hit : response.getHits()) {
            var hitAsJson = getJsonObject(hit);
            var userData = gson.fromJson(hitAsJson.get(SOURCE__PROFILE), ESUsersIndexResultRow.class);
            readGovernedUserGuids(governedUserToProxy, hitAsJson, userData);
            resultData.put(userData.getGuid(), userData);
        }
        return resultData;
    }

    private void readGovernedUserGuids(Map<String, String> governedUserToProxy, JsonObject hitAsJson, ESUsersIndexResultRow userData) {
        var governedUsers = gson.fromJson(hitAsJson.get(SOURCE__GOVERNED_USERS), String[].class);
        for (String gu : governedUsers) {
            userData.getGovernedUsers().add(gu);
            if (governedUserToProxy != null) {
                governedUserToProxy.put(gu, userData.getGuid());
            }
        }
    }
}
