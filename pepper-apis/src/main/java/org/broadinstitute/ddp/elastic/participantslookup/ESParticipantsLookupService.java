package org.broadinstitute.ddp.elastic.participantslookup;

import static com.google.common.collect.ImmutableList.of;
import static java.util.Arrays.asList;
import static org.broadinstitute.ddp.elastic.ElasticSearchIndexType.PARTICIPANTS_STRUCTURED;
import static org.broadinstitute.ddp.elastic.ElasticSearchIndexType.USERS;
import static org.broadinstitute.ddp.elastic.ElasticSearchQueryUtil.escapeQuerySyntaxCharacters;
import static org.broadinstitute.ddp.elastic.participantslookup.search.ESSearch.PARTICIPANTS_STRUCTURED__INDEX__SOURCE;
import static org.broadinstitute.ddp.elastic.participantslookup.search.ESSearch.USERS__INDEX__SOURCE;
import static org.broadinstitute.ddp.service.participantslookup.error.ParticipantsLookupErrorType.SEARCH_ERROR;
import static org.broadinstitute.ddp.util.ElasticsearchServiceUtil.detectEsIndices;

import java.util.HashMap;
import java.util.Map;

import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.elastic.ElasticSearchIndexType;
import org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupField;
import org.broadinstitute.ddp.elastic.participantslookup.search.ESParticipantsLookupResultsMerger;
import org.broadinstitute.ddp.elastic.participantslookup.search.ESParticipantsSearch;
import org.broadinstitute.ddp.elastic.participantslookup.search.ESUsersProxiesSearch;
import org.broadinstitute.ddp.service.participantslookup.ParticipantsLookupResult;
import org.broadinstitute.ddp.service.participantslookup.ParticipantsLookupService;
import org.broadinstitute.ddp.service.participantslookup.error.ParticipantsLookupException;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.client.RestHighLevelClient;

/**
 * Participants lookup service implementation for searching in Pepper ElasticSearch database.
 *
 * <p><b>Participants lookup algorithm:</b>
 * <ul>
 *     <li>find in index "users" (name of index detected by current study) a substring 'query' (full-text-search in fields
 *     specified in {@link ESParticipantsLookupField}) and having 'governedUsers' not empty, so search among proxy users only;
 *     collect found rows in Map(Guid, ESUsersIndexResultRow);</li>
 *     <li>add to Map(governedUserGuid, proxyUserGuid) values of 'governedUsers' (key) and proxy's 'profile.guid' (value);</li>
 *     <li>find in index "participants_structured" (name of index detected by current study) a substring 'query' (full-text-search in
 *     fields specified in {@link ESParticipantsLookupField};</li>
 *     <li>additional query in same index: in invitationId by normalized 'query' - where '-' are removed;</li>
 *     <li>additional query in same index: in 'profile.guid' by all 'governedUsers' found in 'users';</li>
 *     <li>all rows found in index "participants_structured" saved to Map(Guid, ESParticipantsStructuredIndexResultRow);</li>
 *     <li>find in proxy users list (found in "users") a proxy of each found "participant": if proxy not found then
 *     add it to Map(String,String) 'governedUserToProxyExtraSearch';</li>
 *     <li>merge results: go through the Map(Guid, ESParticipantsStructuredIndexResultRow)
 *     add each element (value) to Map(Guid, ParticipantsLookupResultRow);</li>
 *     <li>if processed participant's guid exist in Map(governedUserGuid, proxyUserGuid) then find the corresponding
 *     proxy in Map(Guid, ESUsersIndexResultRow) and set it to 'proxy' element of ParticipantsLookupResultRow.</li>
 *     <li>if 'governedUserToProxyExtraSearch' not empty then do extra search (by list of proxy guids) in 'users' index;</li>
 *     <li>add found extra proxies to Map(Guid, ParticipantsLookupResultRow).</li>
 * </ul>
 */
public class ESParticipantsLookupService extends ParticipantsLookupService {

    private final RestHighLevelClient esClient;

    public ESParticipantsLookupService(RestHighLevelClient esClient) {
        this.esClient = esClient;
    }

    @Override
    protected void doLookupParticipants(
            StudyDto studyDto,
            String query,
            int resultsMaxCount,
            ParticipantsLookupResult participantsLookupResult) throws Exception {

        Map<String, String> governedUserToProxy = new HashMap<>();
        Map<String, String> governedUserToProxyExtraSearch = new HashMap<>();

        Map<ElasticSearchIndexType, String> indices = detectEsIndices(studyDto, of(USERS, PARTICIPANTS_STRUCTURED));
        var usersEsIndex = indices.get(USERS);
        var participantsEsIndex = indices.get(PARTICIPANTS_STRUCTURED);

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

        var resultsMerger = new ESParticipantsLookupResultsMerger(esClient);

        var esResultRows = resultsMerger.mergeResults(
                proxiesResults, participantsStructuredResults, governedUserToProxy, governedUserToProxyExtraSearch);

        // if any extra-proxies found then fetch their data from index "users" and add to esResultRows
        if (governedUserToProxyExtraSearch.size() > 0) {
            resultsMerger.addExtraProxiesToResult(esResultRows, governedUserToProxyExtraSearch, usersEsIndex);
        }

        participantsLookupResult.setResultRows(asList(esResultRows.values().toArray()));
    }

    @Override
    protected void handleException(Exception e) {
        if (e instanceof ElasticsearchStatusException) {
            throw new ParticipantsLookupException(SEARCH_ERROR, ((ElasticsearchStatusException)e).status().name(), e.getMessage());
        }
    }

    @Override
    protected String preProcessQuery(String query) {
        return escapeQuerySyntaxCharacters(query);
    }
}
