package org.broadinstitute.ddp.elastic.participantslookup.search;

import static org.broadinstitute.ddp.elastic.ElasticSearchQueryBuilderUtil.or;
import static org.broadinstitute.ddp.elastic.ElasticSearchQueryBuilderUtil.orMatch;
import static org.broadinstitute.ddp.elastic.ElasticSearchQueryBuilderUtil.queryStringQuery;
import static org.broadinstitute.ddp.elastic.ElasticSearchQueryUtil.getJsonObject;
import static org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupField.INVITATIONS__GUID;
import static org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupField.PROFILE__GUID;
import static org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupIndexType.PARTICIPANTS_STRUCTURED;
import static org.broadinstitute.ddp.elastic.participantslookup.search.ESSearchUtil.GSON;
import static org.broadinstitute.ddp.elastic.participantslookup.search.ESSearchUtil.GUID;
import static org.broadinstitute.ddp.elastic.participantslookup.search.ESSearchUtil.SOURCE__INVITATIONS;
import static org.broadinstitute.ddp.elastic.participantslookup.search.ESSearchUtil.SOURCE__PROFILE;
import static org.broadinstitute.ddp.elastic.participantslookup.search.ESSearchUtil.SOURCE__PROXIES;
import static org.broadinstitute.ddp.elastic.participantslookup.search.ESSearchUtil.SOURCE__STATUS;

import java.util.HashMap;
import java.util.Map;

import org.broadinstitute.ddp.elastic.ElasticSearchQueryExecutor;
import org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsStructuredIndexResultRow;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.service.participantslookup.ParticipantsLookupResult;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;


/**
 * Search step 2.
 * In index "participants_structured" search participants.
 */
public class ESParticipantsSearch extends ElasticSearchQueryExecutor {

    private final Map<String, String> governedUserToProxy;
    private final ParticipantsLookupResult participantsLookupResult;

    /**
     * Constructor.
     * @param esClient ElasticSearch client
     * @param governedUserToProxy map with following data: key=governedUser.guid. value=proxy.guid
     * @param participantsLookupResult object to store final result (here it needs to set to it detected 'totalCount'
     */
    public ESParticipantsSearch(
            RestHighLevelClient esClient,
            Map<String, String> governedUserToProxy,
            ParticipantsLookupResult participantsLookupResult) {
        super(esClient);
        this.governedUserToProxy = governedUserToProxy;
        this.participantsLookupResult = participantsLookupResult;
    }

    /**
     * Create query:
     * <pre>
     * - find participants by a specified query;
     * - find participants invitations by a specified normalized query (removed all '-');
     * - if proxy users was found in index 'users' then find governedUsers by their GUIDs.
     * </pre>
     */
    public QueryBuilder createQuery() {
        var queryBuilder = ESSearchUtil.queryLookupFieldsOfIndex(PARTICIPANTS_STRUCTURED, query);
        var invitationsQueryBuilder = queryStringQuery(INVITATIONS__GUID.getEsField(), normalizeInvitationGuid(query));
        if (governedUserToProxy != null && governedUserToProxy.size() > 0) {
            return or(queryBuilder, invitationsQueryBuilder, orMatch(PROFILE__GUID.getEsField(), governedUserToProxy.keySet()));
        } else {
            return or(queryBuilder, invitationsQueryBuilder);
        }
    }

    /**
     * Read "participants_structured" index result.
     *
     * @param response search response from ElasticSearch
     */
    public Map<String, ESParticipantsStructuredIndexResultRow> readResults(SearchResponse response) {
        Map<String, ESParticipantsStructuredIndexResultRow> results = new HashMap<>();

        for (var hit : response.getHits()) {
            var hitAsJson = getJsonObject(hit);
            var participantResult = GSON.fromJson(
                    hitAsJson.get(SOURCE__PROFILE), ESParticipantsStructuredIndexResultRow.class);

            var invitations = hitAsJson.get(SOURCE__INVITATIONS);
            // expect that only one invitation exist (at least read a 1st one)
            if (invitations != null && invitations.getAsJsonArray().size() > 0) {
                participantResult.setInvitationId(
                        invitations.getAsJsonArray().get(0).getAsJsonObject().get(GUID).getAsString());
            }

            var status = hitAsJson.get(SOURCE__STATUS);
            if (status != null) {
                participantResult.setStatus(EnrollmentStatusType.valueOf(status.getAsString()));
            }

            var proxies = GSON.fromJson(hitAsJson.get(SOURCE__PROXIES), String[].class);
            if (proxies != null) {
                for (var proxyGuid : proxies) {
                    participantResult.getProxies().add(proxyGuid);
                }
            }

            results.put(participantResult.getGuid(), participantResult);
        }
        participantsLookupResult.setTotalCount(Long.valueOf(response.getHits().getTotalHits()).intValue());
        return results;
    }

    /**
     * In most cases invitation GUID contains character '-' (at least on the web page it entered with it),
     * but in DB it can or cannot contain '-' (i.e. it could be stored without '-').
     * Therefore it is searched by 'query' as it is sent from frontend
     * and by same 'query' but with '-' removed from it.
     * This method removes characters '-' from 'query' string (guessing that it is invitationId or it's fragment).
     */
    private static String normalizeInvitationGuid(String invitationGuid) {
        if (invitationGuid != null) {
            return invitationGuid.replaceAll("-", "");
        }
        return invitationGuid;
    }
}
