package org.broadinstitute.ddp.elastic.participantslookup.search;

import static org.broadinstitute.ddp.elastic.ElasticSearchQueryBuilderUtil.orMatch;
import static org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupField.PROFILE__GUID;
import static org.broadinstitute.ddp.elastic.participantslookup.search.ESSearchUtil.PARTICIPANTS_STRUCTURED__INDEX__SOURCE;
import static org.broadinstitute.ddp.elastic.participantslookup.search.ESSearchUtil.USERS__INDEX__SOURCE;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsStructuredIndexResultRow;
import org.broadinstitute.ddp.elastic.participantslookup.model.ESUsersIndexResultRow;
import org.broadinstitute.ddp.json.admin.participantslookup.ParticipantsLookupResultRow;
import org.broadinstitute.ddp.json.admin.participantslookup.ResultRowBase;
import org.broadinstitute.ddp.service.participantslookup.ParticipantsLookupResult;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;

/**
 * Participants lookup steps.
 */
public class ESSearchSteps {

    private final RestHighLevelClient esClient;
    private final Integer resultsMaxCount;

    public ESSearchSteps(RestHighLevelClient esClient, Integer resultsMaxCount) {
        this.esClient = esClient;
        this.resultsMaxCount = resultsMaxCount;
    }

    /**
     * Participants lookup step 1 (optional).<br>
     * Full-text search by `query` in 'users' index: search proxies.
     * In `governedUserToProxy` saved all found proxies (governedUser.guid=proxy.guid).
     */
    public Map<String, ESUsersIndexResultRow> preliminarySearchProxies(
            String query,
            String usersEsIndex,
            Map<String, String> governedUserToProxy
    ) throws IOException {
        var proxiesSearch = new ESUsersProxiesSearch(esClient, governedUserToProxy);
        return proxiesSearch
                .setResultMaxCount(resultsMaxCount)
                .setEsIndex(usersEsIndex)
                .setFetchSource(USERS__INDEX__SOURCE)
                .setQuery(query)
                .search(
                        proxiesSearch::createQuery,
                        proxiesSearch::readResults
                );
    }

    /**
     * Participants lookup step 2.<br>
     * Full-text search by `query` in 'participants' index: search proxies.
     * Plus search in governedUser.guid by guids saved in `governedUserToProxy'.
     */
    public Map<String, ESParticipantsStructuredIndexResultRow> searchParticipants(
            String query,
            String participantsEsIndex,
            Map<String, String> governedUserToProxy,
            ParticipantsLookupResult participantsLookupResult
    ) throws IOException {
        var participantsSearch = new ESParticipantsSearch(esClient, governedUserToProxy, participantsLookupResult);
        return participantsSearch
                .setResultMaxCount(resultsMaxCount)
                .setEsIndex(participantsEsIndex)
                .setFetchSource(PARTICIPANTS_STRUCTURED__INDEX__SOURCE)
                .setQuery(query)
                .search(
                        participantsSearch::createQuery,
                        participantsSearch::readResults
                );
    }

    /**
     * Participants lookup step 2 (version for search by participant GUID).<br>
     */
    public Map<String, ESParticipantsStructuredIndexResultRow> searchParticipantByGuid(
            String participantGuid,
            String participantsEsIndex,
            ParticipantsLookupResult participantsLookupResult
    ) throws IOException {
        var participantsSearch = new ESParticipantsSearch(esClient, null, participantsLookupResult);
        return participantsSearch
                .setEsIndex(participantsEsIndex)
                .setFetchSource(PARTICIPANTS_STRUCTURED__INDEX__SOURCE)
                .setQuery(participantGuid)
                .search(
                        () -> QueryBuilders.matchQuery(PROFILE__GUID.getEsField(), participantGuid),
                        participantsSearch::readResults
                );
    }

    /**
     * Participants lookup step 3.<br>
     * Add proxies results found in "users" to participants data found "participants_structured".<br>
     * <b>Algorithm:</b>
     * <pre>
     * - go through found "participants":
     * -- if this participant - a governedUser (child) then try to find in already found proxies (parents)
     *    the corresponding proxy;
     * -- if it is found then add proxy data to participant object;
     * -- if it is not found then add pair governedUserGuid/proxyGuid to a separate map 'governedUserToProxyExtraSearch'
     *    for proxies extra search in 'users' (and then add found proxies to the result).
     * </pre>
     */
    public Map<String, ParticipantsLookupResultRow> addProxiesDataToParticipants(
            Map<String, ESUsersIndexResultRow> usersResults,
            Map<String, ESParticipantsStructuredIndexResultRow> participantsStructuredResults,
            Map<String, String> governedUserToProxy,
            Map<String, String> governedUserToProxyExtraSearch) {

        Map<String, ParticipantsLookupResultRow> resultList = new HashMap<>();
        for (var participant : participantsStructuredResults.values()) {
            ParticipantsLookupResultRow resultRow = new ParticipantsLookupResultRow(participant);
            var proxyGuid = governedUserToProxy == null ? null : governedUserToProxy.get(participant.getGuid());
            if (proxyGuid != null) {
                resultRow.setProxy(new ResultRowBase(usersResults.get(proxyGuid)));
            } else if (participant.getProxies().size() > 0) {
                governedUserToProxyExtraSearch.put(participant.getGuid(), participant.getProxies().get(0));
            }
            resultList.put(resultRow.getGuid(), resultRow);
        }
        return resultList;
    }

    /**
     * Participants lookup step 4.<br>
     * During reading of proxies data in method {@link #addProxiesDataToParticipants(Map, Map, Map, Map)}
     * it is detected a list of proxies which still not fetched from 'users': governedUserToProxyExtraSearch.
     * In method {@link #proxiesExtraSearch(Map, Map, String)} these proxies are fetched and added to the
     * corresponding governed users (into participantsStructuredResults)
     */
    public void proxiesExtraSearch(
            Map<String, ParticipantsLookupResultRow> results,
            Map<String, String> governedUserToProxyExtraSearch,
            String usersEsIndex) throws IOException {

        if (governedUserToProxyExtraSearch.size() > 0) {

            var proxiesExtraSearch = new ESUsersProxiesSearch(esClient, governedUserToProxyExtraSearch);
            Map<String, ESUsersIndexResultRow> proxiesExtraResults = proxiesExtraSearch
                    .setEsIndex(usersEsIndex)
                    .setFetchSource(USERS__INDEX__SOURCE)
                    .search(
                            () -> orMatch(PROFILE__GUID.getEsField(), governedUserToProxyExtraSearch.values()),
                            proxiesExtraSearch::readResults
                    );

            // add to result found extra proxies (which were not found during search step 1)
            for (var gu : governedUserToProxyExtraSearch.keySet()) {
                results.get(gu).setProxy(proxiesExtraResults.get(governedUserToProxyExtraSearch.get(gu)));
            }
        }
    }
}
