package org.broadinstitute.ddp.elastic.participantslookup;

import static com.google.common.collect.ImmutableList.of;
import static java.util.Arrays.asList;
import static org.broadinstitute.ddp.elastic.ElasticSearchIndexType.PARTICIPANTS_STRUCTURED;
import static org.broadinstitute.ddp.elastic.ElasticSearchIndexType.USERS;
import static org.broadinstitute.ddp.elastic.ElasticSearchQueryUtil.escapeQuerySyntaxCharacters;
import static org.broadinstitute.ddp.service.participantslookup.error.ParticipantsLookupErrorType.SEARCH_ERROR;
import static org.broadinstitute.ddp.util.ElasticsearchServiceUtil.detectEsIndices;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupData;
import org.broadinstitute.ddp.elastic.participantslookup.search.ESSearchSteps;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.json.admin.participantslookup.ParticipantsLookupResultRow;
import org.broadinstitute.ddp.service.participantslookup.ParticipantLookupType;
import org.broadinstitute.ddp.service.participantslookup.ParticipantsLookupResult;
import org.broadinstitute.ddp.service.participantslookup.ParticipantsLookupService;
import org.broadinstitute.ddp.service.participantslookup.error.ParticipantsLookupException;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.client.RestHighLevelClient;

/**
 * Participants lookup service implementation for searching in Pepper ElasticSearch database.
 */
public class ESParticipantsLookupService extends ParticipantsLookupService {

    private final RestHighLevelClient esClient;

    public ESParticipantsLookupService(RestHighLevelClient esClient) {
        this.esClient = esClient;
    }

    @Override
    protected void doLookupParticipants(
            ParticipantLookupType participantLookupType,
            StudyDto studyDto,
            String query,
            Integer resultsMaxCount,
            ParticipantsLookupResult participantsLookupResult) throws Exception {

        Map<String, String> governedUserToProxy = new HashMap<>();
        Map<String, String> governedUserToProxyExtraSearch = new HashMap<>();

        var indices = detectEsIndices(studyDto, of(USERS, PARTICIPANTS_STRUCTURED));
        var usersEsIndex = indices.get(USERS);
        var participantsEsIndex = indices.get(PARTICIPANTS_STRUCTURED);

        var esSearchSteps = new ESSearchSteps(esClient, resultsMaxCount);

        Map<String, ParticipantsLookupResultRow> esResultRows;

        switch (participantLookupType) {
            case FULL_TEXT_SEARCH_BY_QUERY_STRING:
                esResultRows = participantsLookupByQueryString(
                        esSearchSteps,
                        query,
                        usersEsIndex,
                        participantsEsIndex,
                        governedUserToProxy,
                        governedUserToProxyExtraSearch,
                        participantsLookupResult);
                break;
            case BY_PARTICIPANT_GUID:
                esResultRows = participantsLookupByGuid(
                        esSearchSteps,
                        query,
                        usersEsIndex,
                        participantsEsIndex,
                        governedUserToProxyExtraSearch,
                        participantsLookupResult);
                break;
            default:
                throw new DDPException("Unhandled participants lookup type: " + participantLookupType);
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

    /**
     * Participants lookup by `query` (full-text search).<br>
     * The following steps are executed:
     * <pre>
     *     - full-text search of proxies (users index): by specified fields: name, email, guid...;
     *     - full-text search of participants (participants index): by specified fields: name, email, guid... + detected
     *       guids of governed users;
     *     - search of proxies which not found on step 1 (by proxy guids detected from participants).
     * </pre>
     *
     * <p><b>Participants lookup algorithm:</b>
     * <ul>
     *     <li>find in index "users" (name of index detected by current study) a substring 'query' (full-text-search in fields
     *     specified in {@link ESParticipantsLookupData.LookupField}) and having 'governedUsers' not empty,
     *     so search among proxy users only;
     *     collect found rows in Map(Guid, ESUsersIndexResultRow);</li>
     *     <li>add to Map(governedUserGuid, proxyUserGuid) values of 'governedUsers' (key) and proxy's 'profile.guid' (value);</li>
     *     <li>find in index "participants_structured" (name of index detected by current study) a substring 'query' (full-text-search in
     *     fields specified in {@link ESParticipantsLookupData.LookupField};</li>
     *     <li>additional query in same index: in invitationId by normalized 'query' - where '-' are removed;</li>
     *     <li>additional query in same index: in 'profile.guid' by all 'governedUsers' found in 'users';</li>
     *     <li>all rows found in index "participants_structured" saved to Map(Guid, ESParticipantsStructuredIndexResultRow);</li>
     *     <li>find in proxy users list (found in "users") a proxy of each found "participant": if proxy not found then
     *     add it to Map(String,String) 'governedUserToProxyExtraSearch';</li>
     *     <li>add proxy results to participants: go through the Map(Guid, ESParticipantsStructuredIndexResultRow)
     *     add each element (value) to Map(Guid, ParticipantsLookupResultRow);</li>
     *     <li>if processed participant's guid exist in Map(governedUserGuid, proxyUserGuid) then find the corresponding
     *     proxy in Map(Guid, ESUsersIndexResultRow) and set it to 'proxy' element of ParticipantsLookupResultRow.</li>
     *     <li>if 'governedUserToProxyExtraSearch' not empty then do extra search (by list of proxy guids) in 'users' index;</li>
     *     <li>add found extra proxies to Map(Guid, ParticipantsLookupResultRow).</li>
     * </ul>
     */
    private Map<String, ParticipantsLookupResultRow> participantsLookupByQueryString(
            ESSearchSteps esSearchSteps,
            String query,
            String usersEsIndex,
            String participantsEsIndex,
            Map<String, String> governedUserToProxy,
            Map<String, String> governedUserToProxyExtraSearch,
            ParticipantsLookupResult participantsLookupResult) throws IOException {

        var proxiesResults = esSearchSteps.preliminarySearchProxies(
                query, usersEsIndex, governedUserToProxy);

        var participantsStructuredResults = esSearchSteps.searchParticipants(
                query, participantsEsIndex, governedUserToProxy, participantsLookupResult);

        var esResultRows = esSearchSteps.addProxiesDataToParticipants(
                proxiesResults, participantsStructuredResults, governedUserToProxy, governedUserToProxyExtraSearch);

        esSearchSteps.proxiesExtraSearch(esResultRows, governedUserToProxyExtraSearch, usersEsIndex);

        return esResultRows;
    }

    /**
     * Participant lookup by participant guid.
     *
     * <p><b>Participants lookup by GUID algorithm:</b>
     * <ul>
     *     <li>find in index 'participants_structured' a participants which GUID match to a specified
     *     guid (stored in parameter '`query`);</li>
     *     <li>if 'governedUserToProxyExtraSearch' not empty then do a search (by proxy guid) in 'users' index;</li>
     *     <li>add found proxy data to participant.</li>
     * </ul>
     */
    private Map<String, ParticipantsLookupResultRow> participantsLookupByGuid(
            ESSearchSteps esSearchSteps,
            String participantGuid,
            String usersEsIndex,
            String participantsEsIndex,
            Map<String, String> governedUserToProxyExtraSearch,
            ParticipantsLookupResult participantsLookupResult) throws IOException {

        var participantsStructuredResults = esSearchSteps.searchParticipantByGuid(
                participantGuid, participantsEsIndex, participantsLookupResult);

        var esResultRows = esSearchSteps.addProxiesDataToParticipants(
                null, participantsStructuredResults, null, governedUserToProxyExtraSearch);

        esSearchSteps.proxiesExtraSearch(esResultRows, governedUserToProxyExtraSearch, usersEsIndex);

        return esResultRows;
    }
}
