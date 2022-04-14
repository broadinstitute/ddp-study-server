package org.broadinstitute.ddp.elastic.participantslookup.search;

import static org.broadinstitute.ddp.elastic.ElasticSearchQueryUtil.getJsonObject;
import static org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupData.IndexSource.GOVERNED_USERS;
import static org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupData.IndexSource.PROFILE;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.broadinstitute.ddp.elastic.participantslookup.model.ESUsersIndexResultRow;
import org.broadinstitute.ddp.util.GsonUtil;
import org.elasticsearch.action.search.SearchResponse;


/**
 * Process results found in index "users".
 * In this index search proxy users only.
 */
public class ESUsersProxiesResultReader {

    private static final Gson gson = GsonUtil.standardGson();

    protected final Map<String, String> governedUserToProxy;

    public ESUsersProxiesResultReader(Map<String, String> governedUserToProxy) {
        this.governedUserToProxy = governedUserToProxy;
    }

    public Map<String, ESUsersIndexResultRow> readResults(SearchResponse response) {
        Map<String, ESUsersIndexResultRow> resultData = new HashMap<>();
        for (var hit : response.getHits()) {
            var hitAsJson = getJsonObject(hit);
            var userData = gson.fromJson(hitAsJson.get(PROFILE.getSource()), ESUsersIndexResultRow.class);
            readGovernedUserGuids(governedUserToProxy, hitAsJson, userData);
            resultData.put(userData.getGuid(), userData);
        }
        return resultData;
    }

    private void readGovernedUserGuids(Map<String, String> governedUserToProxy, JsonObject hitAsJson, ESUsersIndexResultRow userData) {
        var governedUsers = gson.fromJson(hitAsJson.get(GOVERNED_USERS.getSource()), String[].class);
        for (String gu : governedUsers) {
            userData.getGovernedUsers().add(gu);
            if (governedUserToProxy != null) {
                governedUserToProxy.put(gu, userData.getGuid());
            }
        }
    }
}
