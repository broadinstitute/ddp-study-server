package org.broadinstitute.ddp.elastic.participantslookup;

import static org.broadinstitute.ddp.elastic.ElasticSearchQueryUtil.addWildcards;
import static org.broadinstitute.ddp.elastic.ElasticSearchQueryUtil.getJsonElement;
import static org.broadinstitute.ddp.elastic.ElasticSearchQueryUtil.prepareSearching;
import static org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupField.isQueryFieldForIndex;
import static org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupIndexType.USERS;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import org.broadinstitute.ddp.elastic.ElasticSearchIndexType;
import org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupField;
import org.broadinstitute.ddp.elastic.participantslookup.model.ESUsersIndexResultRow;
import org.broadinstitute.ddp.util.ElasticsearchServiceUtil;
import org.broadinstitute.ddp.util.GsonUtil;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;


/**
 * Provides data reading from ES index "users"
 */
public class ESUsersIndexSearchHelper {

    private static final Gson gson = GsonUtil.standardGson();

    private static final ElasticSearchIndexType ES_INDEX_USERS = ElasticSearchIndexType.USERS;

    private static final String SOURCE__PROFILE = "profile";
    private static final String SOURCE__GOVERNED_USERS = "governedUsers";

    private static final String[] USERS__INDEX__SOURCE = {
            SOURCE__PROFILE,
            SOURCE__GOVERNED_USERS
    };

    public static Map<String, ESUsersIndexResultRow> searchInUsersIndex(
            RestHighLevelClient esClient,
            String studyGuid,
            String mainQuery,
            Map<String, String> governedUserToProxy,
            int resultMaxCount
    ) throws IOException {
        var esIndex = ElasticsearchServiceUtil.detectEsIndex(studyGuid, ES_INDEX_USERS);
        var queryBuilder = createESQueryForUsersIndex(mainQuery);
        var searchRequest = prepareSearching(
                esIndex, USERS__INDEX__SOURCE, queryBuilder, doubleResultMaxCount(resultMaxCount));
        var response = esClient.search(searchRequest, RequestOptions.DEFAULT);
        return readUsersIndexResult(response, governedUserToProxy);
    }

    private static AbstractQueryBuilder createESQueryForUsersIndex(String query) {
        var builder = QueryBuilders.queryStringQuery(addWildcards(query)).defaultOperator(Operator.OR);
        for (ESParticipantsLookupField field : ESParticipantsLookupField.values()) {
            if (isQueryFieldForIndex(field, USERS)) {
                builder.field(field.getEsField());
            }
        }
        return builder;
    }

    public static Map<String, ESUsersIndexResultRow> readUsersIndexResult(
            SearchResponse response,
            Map<String, String> governedUserToProxy) {
        Map<String, ESUsersIndexResultRow> resultData = new HashMap<>();
        for (var hit : response.getHits()) {
            var participantResultData = gson.fromJson(getJsonElement(hit, SOURCE__PROFILE), ESUsersIndexResultRow.class);
            JsonElement governedUsersJson = getJsonElement(hit, SOURCE__GOVERNED_USERS);
            if (governedUsersJson != null) {
                var governedUsers = gson.fromJson(getJsonElement(hit, SOURCE__GOVERNED_USERS), String[].class);
                for (String gu : governedUsers) {
                    participantResultData.getGovernedUsers().add(gu);
                    governedUserToProxy.put(gu, participantResultData.getGuid());
                }
            }
            resultData.put(participantResultData.getGuid(), participantResultData);
        }
        return resultData;
    }

    /**
     * Double MAX limit for searching in index "users" because it can be found proxy users (parents) having
     * governed users (children): in such case we can get a final list (governed users)
     * which number less than MAX (whereas list found in index "users" has number of elements == MAX).<br>
     * EXAMPLE:
     * <pre>
     * For example: MAX = 10
     * Query: "john"
     * In index "users" found 5 of proxy users having in lastName substring "john" and
     * all their children (governed users) have same lastName therefore they found also (5 users). Total found = 10.
     * When searching in index "participants_structured" it is found governed users only (5 participants)
     * therefore final list is 5 (which twice less than specified limit 10).
     * </pre>
     * That's why it needs to double MAX limit when searching in index "users".
     */
    private static int doubleResultMaxCount(int resultMaxCount) {
        return resultMaxCount * 2;
    }
}
