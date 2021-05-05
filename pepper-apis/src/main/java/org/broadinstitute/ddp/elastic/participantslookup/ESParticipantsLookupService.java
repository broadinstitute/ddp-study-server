package org.broadinstitute.ddp.elastic.participantslookup;

import static org.broadinstitute.ddp.elastic.participantslookup.search.ESSearchBase.PARTICIPANTS_STRUCTURED__INDEX__SOURCE;
import static org.broadinstitute.ddp.elastic.participantslookup.search.ESSearchBase.USERS__INDEX__SOURCE;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.broadinstitute.ddp.elastic.ElasticSearchIndexType;
import org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupField;
import org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsStructuredIndexResultRow;
import org.broadinstitute.ddp.elastic.participantslookup.model.ESUsersIndexResultRow;
import org.broadinstitute.ddp.elastic.participantslookup.search.ESParticipantsSearch;
import org.broadinstitute.ddp.elastic.participantslookup.search.ESUsersProxiesExtraSearch;
import org.broadinstitute.ddp.elastic.participantslookup.search.ESUsersProxiesSearch;
import org.broadinstitute.ddp.json.admin.participantslookup.ParticipantsLookupResultRow;
import org.broadinstitute.ddp.json.admin.participantslookup.ParticipantsLookupResultRowBase;
import org.broadinstitute.ddp.service.participantslookup.ParticipantsLookupResult;
import org.broadinstitute.ddp.service.participantslookup.ParticipantsLookupService;
import org.broadinstitute.ddp.util.ElasticsearchServiceUtil;
import org.elasticsearch.client.RestHighLevelClient;

/**
 * Participants lookup service implementation for searching in Pepper ElasticSearch database.
 *
 * <p><b>Participants lookup algorithm:</b>
 * <ul>
 *     <li>find in index "users" (detect by current study) a substring 'query' (full-text-search in fields
 *     specified in {@link ESParticipantsLookupField}) and having 'governedUsers' not empty;
 *     collect found rows in Map(Guid, ESUsersIndexResultRow);</li>
 *     <li>add to Map(governedUserGuid, proxyUserGuid) values of 'governedUsers' (key) and 'profile.guid' (value);</li>
 *     <li>find in index "participants_structured" (detect by current study) a substring 'query' (full-text-search in
 *     fields specified in {@link ESParticipantsLookupField};</li>
 *     <li>additional query in same index: in invitationId by normalized 'query' - where '-' are removed;</li>
 *     <li>additional query in same index: in guid by all found 'governedUsers';</li>
 *     <li>all rows found in index "participants_structured" saved to Map(Guid, ESParticipantsStructuredIndexResultRow);</li>
 *     <li>find in proxy users list (found in "users") a proxy of each found "participant": if proxy not found then
 *     add it to Map(String,String) 'governedUserToProxyExtraSearch';</li>
 *     <li>if 'governedUserToProxyExtraSearch' not empty then do extra serach (by list of proxy guids) in 'users' index;</li>
 *     <li>add found extra proxies to the proxies map;</li>
 *     <li>the last step - merge results: go through the Map(Guid, ESParticipantsStructuredIndexResultRow)
 *     add each element (value) to List(ParticipantsLookupResultRow);</li>
 *     <li>if processed participant's guid exist in Map(governedUserGuid, proxyUserGuid) then find the corresponding
 *     proxy in Map(Guid, ESUsersIndexResultRow) and set it to 'proxy' element of ParticipantsLookupResultRow.</li>
 * </ul>
 */
public class ESParticipantsLookupService extends ParticipantsLookupService {

    private final RestHighLevelClient esClient;

    public ESParticipantsLookupService(RestHighLevelClient esClient) {
        this.esClient = esClient;
    }

    @Override
    protected void doLookupParticipants(
            String studyGuid,
            String query,
            int resultsMaxCount,
            ParticipantsLookupResult participantsLookupResult) throws Exception {

        Map<String, String> governedUserToProxy = new HashMap<>();
        Map<String, String> governedUserToProxyExtraSearch = new HashMap<>();

        var usersEsIndex = ElasticsearchServiceUtil.detectEsIndex(studyGuid, ElasticSearchIndexType.USERS);
        var participantsEsIndex = ElasticsearchServiceUtil.detectEsIndex(studyGuid, ElasticSearchIndexType.PARTICIPANTS_STRUCTURED);

        var proxiesResults = new ESUsersProxiesSearch(esClient, governedUserToProxy)
                .setResultMaxCount(resultsMaxCount)
                .setEsIndex(usersEsIndex)
                .setFetchSource(USERS__INDEX__SOURCE)
                .setQuery(query)
                .search();

        var participantsStructuredResults = new ESParticipantsSearch(esClient, governedUserToProxy, participantsLookupResult)
                .setResultMaxCount(resultsMaxCount)
                .setEsIndex(participantsEsIndex)
                .setFetchSource(PARTICIPANTS_STRUCTURED__INDEX__SOURCE)
                .setQuery(query)
                .search();

        var esResultRows = mergeResults(
                proxiesResults, participantsStructuredResults, governedUserToProxy, governedUserToProxyExtraSearch);

        // if any extra-proxies found then fetch their data from index "users", add to proxiesResults, and merge results again
        if (governedUserToProxyExtraSearch.size() > 0) {
            esResultRows = reMergeResultsWithExtraFoundProxies(
                    query, resultsMaxCount, governedUserToProxy, governedUserToProxyExtraSearch,
                    usersEsIndex, proxiesResults, participantsStructuredResults);

        }

        participantsLookupResult.setResultRows(esResultRows);
    }

    /**
     * Adds to results of "participants_structured" index the 'proxies' data results (taken from
     * "users" index results).
     */
    private List<ParticipantsLookupResultRow> mergeResults(
            Map<String, ESUsersIndexResultRow> usersResults,
            Map<String, ESParticipantsStructuredIndexResultRow> participantsStructuredResults,
            Map<String, String> governedUserToProxy,
            Map<String, String> governedUserToProxyExtraSearch) {
        List<ParticipantsLookupResultRow> resultList = new ArrayList<>();
        for (var participant : participantsStructuredResults.values()) {
            ParticipantsLookupResultRow resultRow = new ParticipantsLookupResultRow(participant);
            var proxyGuid = governedUserToProxy.get(participant.getGuid());
            if (proxyGuid != null) {
                resultRow.setProxy(new ParticipantsLookupResultRowBase(usersResults.get(proxyGuid)));
            } else if (participant.getProxies().size() > 0) {
                governedUserToProxyExtraSearch.put(participant.getGuid(), participant.getProxies().get(0));
            }
            resultList.add(resultRow);
        }
        return resultList;
    }

    private List reMergeResultsWithExtraFoundProxies(
            String query,
            int resultsMaxCount,
            Map<String, String> governedUserToProxy,
            Map<String, String> governedUserToProxyExtraSearch,
            String usersEsIndex,
            Map proxiesResults,
            Map participantsStructuredResults) throws IOException {
        List esResultRows;
        Map<String, ESUsersIndexResultRow> proxiesExtraResults = new ESUsersProxiesExtraSearch(esClient, governedUserToProxyExtraSearch)
                .setResultMaxCount(resultsMaxCount)
                .setEsIndex(usersEsIndex)
                .setFetchSource(USERS__INDEX__SOURCE)
                .setQuery(query)
                .search();
        // add to proxy result found extra proxies (which were not found during search step 1)
        for (var proxyGuid : proxiesExtraResults.keySet()) {
            proxiesResults.put(proxyGuid, proxiesExtraResults.get(proxyGuid));
        }
        // copy to map governedUser=proxy the found extra proxies
        governedUserToProxy.putAll(governedUserToProxyExtraSearch);
        // repeated merge of results
        esResultRows = mergeResults(
                proxiesResults, participantsStructuredResults, governedUserToProxy, governedUserToProxyExtraSearch);
        return esResultRows;
    }
}
