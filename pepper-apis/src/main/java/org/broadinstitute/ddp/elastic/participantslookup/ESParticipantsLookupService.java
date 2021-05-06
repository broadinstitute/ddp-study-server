package org.broadinstitute.ddp.elastic.participantslookup;

import static java.util.Arrays.asList;
import static org.broadinstitute.ddp.elastic.participantslookup.search.ESSearch.PARTICIPANTS_STRUCTURED__INDEX__SOURCE;
import static org.broadinstitute.ddp.elastic.participantslookup.search.ESSearch.USERS__INDEX__SOURCE;

import java.util.HashMap;
import java.util.Map;

import org.broadinstitute.ddp.elastic.ElasticSearchIndexType;
import org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupField;
import org.broadinstitute.ddp.elastic.participantslookup.search.ESParticipantsLookupResultsMerger;
import org.broadinstitute.ddp.elastic.participantslookup.search.ESParticipantsSearch;
import org.broadinstitute.ddp.elastic.participantslookup.search.ESUsersProxiesSearch;
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

        var proxiesSearch = new ESUsersProxiesSearch(esClient, governedUserToProxy);
        var proxiesResults = proxiesSearch
                .setResultMaxCount(resultsMaxCount)
                .setEsIndex(usersEsIndex)
                .setFetchSource(USERS__INDEX__SOURCE)
                .setQuery(query)
                .search(
                   proxiesSearch::createQuery,
                   proxiesSearch::readResults
                );

        var participantsSearch = new ESParticipantsSearch(esClient, governedUserToProxy, participantsLookupResult);
        var participantsStructuredResults = participantsSearch
                .setResultMaxCount(resultsMaxCount)
                .setEsIndex(participantsEsIndex)
                .setFetchSource(PARTICIPANTS_STRUCTURED__INDEX__SOURCE)
                .setQuery(query)
                .search(
                   participantsSearch::createQuery,
                   participantsSearch::readResults
                );

        ESParticipantsLookupResultsMerger resultsMerger = new ESParticipantsLookupResultsMerger(esClient);

        var esResultRows = resultsMerger.mergeResults(
                proxiesResults, participantsStructuredResults, governedUserToProxy, governedUserToProxyExtraSearch);

        // if any extra-proxies found then fetch their data from index "users", add to proxiesResults, and merge results again
        if (governedUserToProxyExtraSearch.size() > 0) {
            resultsMerger.addExtraProxiesToResult(esResultRows, governedUserToProxyExtraSearch, usersEsIndex);
        }

        participantsLookupResult.setResultRows(asList(esResultRows.values().toArray()));
    }
}
