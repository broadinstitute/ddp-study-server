package org.broadinstitute.ddp.elastic.participantslookup;

import static org.broadinstitute.ddp.elastic.ElasticSearchQueryUtil.addWildcards;
import static org.broadinstitute.ddp.elastic.ElasticSearchQueryUtil.getJsonElement;
import static org.broadinstitute.ddp.elastic.ElasticSearchQueryUtil.prepareSearching;
import static org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupField.isQueryFieldForIndex;
import static org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupIndexType.PARTICIPANTS_STRUCTURED;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import org.broadinstitute.ddp.elastic.ElasticSearchIndexType;
import org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupField;
import org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsStructuredIndexResultRow;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.service.participantslookup.ParticipantsLookupResult;
import org.broadinstitute.ddp.util.ElasticsearchServiceUtil;
import org.broadinstitute.ddp.util.GsonUtil;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;


/**
 * Provides data reading from ES index "participants_structured"
 */
public class ESParticipantsStructuredIndexSearchHelper {

    private static final Gson gson = GsonUtil.standardGson();

    private static final ElasticSearchIndexType ES_INDEX_PARTICIPANTS_STRUCTURED = ElasticSearchIndexType.PARTICIPANTS_STRUCTURED;

    private static final String SOURCE__STATUS = "status";
    private static final String SOURCE__PROFILE = "profile";
    private static final String SOURCE__INVITATIONS = "invitations";
    private static final String SOURCE__PROXIES = "proxies";

    private static final String GUID = "guid";

    private static final String[] PARTICIPANTS_STRUCTURED__INDEX__SOURCE = {
            SOURCE__STATUS,
            SOURCE__PROFILE,
            SOURCE__INVITATIONS,
            SOURCE__PROXIES
    };

    public static Map<String, ESParticipantsStructuredIndexResultRow> searchInParticipantsStructuredIndex(
            RestHighLevelClient esClient,
            String studyGuid,
            String mainQuery,
            Map<String, String> governedUserToProxy,
            int resultMaxCount,
            ParticipantsLookupResult participantsLookupResult) throws IOException {
        var esIndex = ElasticsearchServiceUtil.detectEsIndex(studyGuid, ES_INDEX_PARTICIPANTS_STRUCTURED);
        var queryBuilder = createESQueryForParticipantsStructuredIndex(mainQuery, governedUserToProxy);
        var searchRequest = prepareSearching(esIndex, PARTICIPANTS_STRUCTURED__INDEX__SOURCE, queryBuilder, resultMaxCount);
        var response = esClient.search(searchRequest, RequestOptions.DEFAULT);
        participantsLookupResult.setTotalCount(response.getHits().getTotalHits());
        return readParticipantsStructuredIndexResult(response);
    }

    private static AbstractQueryBuilder createESQueryForParticipantsStructuredIndex(String query, Map<String, String> governedUserToProxy) {
        var aggregatedQuery = getAggregatedQuery(query, governedUserToProxy);
        var builder = QueryBuilders.queryStringQuery(aggregatedQuery.toString()).defaultOperator(Operator.OR);
        for (ESParticipantsLookupField field : ESParticipantsLookupField.values()) {
            if (isQueryFieldForIndex(field, PARTICIPANTS_STRUCTURED)) {
                builder.field(field.getEsField());
            }
        }
        return builder;
    }

    /**
     * Read "participants_structured" index result.
     *
     * @param response search response from ElasticSearch
     */
    private static Map<String, ESParticipantsStructuredIndexResultRow> readParticipantsStructuredIndexResult(
            SearchResponse response) {
        Map<String, ESParticipantsStructuredIndexResultRow> results = new HashMap<>();

        for (var hit : response.getHits()) {
            var participantResultData = gson.fromJson(
                    getJsonElement(hit, SOURCE__PROFILE), ESParticipantsStructuredIndexResultRow.class);

            var invitations = getJsonElement(hit, SOURCE__INVITATIONS);
            // expect that only one invitation exist (at least read a 1st one)
            if (invitations != null && invitations.getAsJsonArray().size() > 0) {
                participantResultData.setInvitationId(
                        invitations.getAsJsonArray().get(0).getAsJsonObject().get(GUID).getAsString());
            }
            var status = getJsonElement(hit, SOURCE__STATUS);
            if (status != null) {
                participantResultData.setStatus(EnrollmentStatusType.valueOf(status.getAsString()));
            }
            results.put(participantResultData.getGuid(), participantResultData);
        }
        return results;
    }

    private static StringBuffer getAggregatedQuery(String query, Map<String, String> governedUserToProxy) {
        var aggregatedQuery = new StringBuffer(addWildcards(query));
        aggregatedQuery.append(' ').append(addWildcards(normalizeInvitationGuid(query)));
        for (var governedUserGuid : governedUserToProxy.keySet()) {
            aggregatedQuery.append(' ').append(addWildcards(governedUserGuid));
        }
        return aggregatedQuery;
    }

    private static String normalizeInvitationGuid(String invitationGuid) {
        if (invitationGuid != null) {
            return invitationGuid.replaceAll("-", "");
        }
        return invitationGuid;
    }
}
