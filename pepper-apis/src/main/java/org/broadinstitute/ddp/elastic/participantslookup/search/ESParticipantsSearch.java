package org.broadinstitute.ddp.elastic.participantslookup.search;

import static org.broadinstitute.ddp.elastic.ElasticSearchQueryUtil.addWildcards;
import static org.broadinstitute.ddp.elastic.ElasticSearchQueryUtil.getJsonObject;
import static org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupField.INVITATIONS__GUID;
import static org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupField.PROFILE__GUID;
import static org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupField.isQueryFieldForIndex;
import static org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupIndexType.PARTICIPANTS_STRUCTURED;

import java.util.HashMap;
import java.util.Map;

import org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupField;
import org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsStructuredIndexResultRow;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.service.participantslookup.ParticipantsLookupResult;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;


/**
 * Search step 2.
 * In index "participants_structured" search participants.
 */
public class ESParticipantsSearch extends ESSearchBase {

    private final Map<String, String> governedUserToProxy;
    private final ParticipantsLookupResult participantsLookupResult;

    public ESParticipantsSearch(
            RestHighLevelClient esClient,
            Map<String, String> governedUserToProxy,
            ParticipantsLookupResult participantsLookupResult) {
        super(esClient);
        this.governedUserToProxy = governedUserToProxy;
        this.participantsLookupResult = participantsLookupResult;
    }

    @Override
    protected AbstractQueryBuilder createQuery() {

        // find participants by a specified query
        var queryBuilder = QueryBuilders.queryStringQuery(addWildcards(query)).defaultOperator(Operator.OR);
        for (var field : ESParticipantsLookupField.values()) {
            if (isQueryFieldForIndex(field, PARTICIPANTS_STRUCTURED)) {
                queryBuilder.field(field.getEsField());
            }
        }

        // find participants invitations by a specified query
        var invitationsQueryBuilder = QueryBuilders.queryStringQuery(addWildcards(normalizeInvitationGuid(query)))
                .field(INVITATIONS__GUID.getEsField());

        BoolQueryBuilder mainQueryBuilder = new BoolQueryBuilder();
        mainQueryBuilder
                .should(queryBuilder)
                .should(invitationsQueryBuilder);

        if (governedUserToProxy.size() > 0) {
            // find all governedUsers for which found proxies in 'users'
            BoolQueryBuilder governedUsersByGuidQueryBuilder = new BoolQueryBuilder();
            for (var gu : governedUserToProxy.keySet()) {
                governedUsersByGuidQueryBuilder.should(QueryBuilders.matchQuery(PROFILE__GUID.getEsField(), gu));
            }
            mainQueryBuilder.should(governedUsersByGuidQueryBuilder);
        }

        return mainQueryBuilder;
    }

    /**
     * Read "participants_structured" index result.
     *
     * @param response search response from ElasticSearch
     */
    @Override
    protected Map<String, ESParticipantsStructuredIndexResultRow> readResults(SearchResponse response) {
        Map<String, ESParticipantsStructuredIndexResultRow> results = new HashMap<>();

        for (var hit : response.getHits()) {
            var hitAsJson = getJsonObject(hit);
            var participantResult = gson.fromJson(
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

            var proxies = gson.fromJson(hitAsJson.get(SOURCE__PROXIES), String[].class);
            for (var proxyGuid :proxies) {
                participantResult.getProxies().add(proxyGuid);
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
