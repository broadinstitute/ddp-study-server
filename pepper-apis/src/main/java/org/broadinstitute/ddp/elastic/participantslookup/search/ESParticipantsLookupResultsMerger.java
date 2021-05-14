package org.broadinstitute.ddp.elastic.participantslookup.search;

import static org.broadinstitute.ddp.elastic.ElasticSearchQueryBuilderUtil.orMatch;
import static org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupField.PROFILE__GUID;
import static org.broadinstitute.ddp.elastic.participantslookup.search.ESSearch.USERS__INDEX__SOURCE;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsStructuredIndexResultRow;
import org.broadinstitute.ddp.elastic.participantslookup.model.ESUsersIndexResultRow;
import org.broadinstitute.ddp.json.admin.participantslookup.ParticipantsLookupResultRow;
import org.broadinstitute.ddp.json.admin.participantslookup.ResultRowBase;
import org.elasticsearch.client.RestHighLevelClient;


/**
 * Provides merging of data found in index "participants_structured" with proxy data found in index "users".
 */
public class ESParticipantsLookupResultsMerger {

    private final RestHighLevelClient esClient;

    public ESParticipantsLookupResultsMerger(RestHighLevelClient esClient) {
        this.esClient = esClient;
    }

    /**
     * Merge results found in "users" and "participants_structured".<br>
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
    public Map<String, ParticipantsLookupResultRow> mergeResults(
            Map<String, ESUsersIndexResultRow> usersResults,
            Map<String, ESParticipantsStructuredIndexResultRow> participantsStructuredResults,
            Map<String, String> governedUserToProxy,
            Map<String, String> governedUserToProxyExtraSearch) {

        Map<String, ParticipantsLookupResultRow> resultList = new HashMap<>();
        for (var participant : participantsStructuredResults.values()) {
            ParticipantsLookupResultRow resultRow = new ParticipantsLookupResultRow(participant);
            var proxyGuid = governedUserToProxy.get(participant.getGuid());
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
     * During merging of results it is detected a list of proxies which still not fetched from 'users':
     * governedUserToProxyExtraSearch.
     * In this method these proxies are fetched and added to the corresponding governed users
     * (into participantsStructuredResults)
     */
    public void addExtraProxiesToResult(
            Map<String, ParticipantsLookupResultRow> results,
            Map<String, String> governedUserToProxyExtraSearch,
            String usersEsIndex) throws IOException {

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
