package org.broadinstitute.ddp.elastic.participantslookup.search;

import static org.broadinstitute.ddp.elastic.ElasticSearchQueryBuilderUtil.and;
import static org.broadinstitute.ddp.elastic.ElasticSearchQueryBuilderUtil.notEmptyFieldQuery;
import static org.broadinstitute.ddp.elastic.ElasticSearchQueryBuilderUtil.or;
import static org.broadinstitute.ddp.elastic.ElasticSearchQueryBuilderUtil.orMatch;
import static org.broadinstitute.ddp.elastic.ElasticSearchQueryBuilderUtil.queryStringQuery;
import static org.broadinstitute.ddp.elastic.ElasticSearchQueryUtil.addWildcards;
import static org.broadinstitute.ddp.elastic.ElasticSearchQueryUtil.normalizeInvitationGuid;
import static org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupData.LookupField.GOVERNED_USERS;
import static org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupData.LookupField.INVITATIONS__GUID;
import static org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupData.LookupField.PROFILE__GUID;
import static org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupData.IndexType.PARTICIPANTS_STRUCTURED;
import static org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupData.IndexType.USERS;
import static org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupData.PARTICIPANTS_STRUCTURED_SOURCES;
import static org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupData.USERS_SOURCES;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.broadinstitute.ddp.elastic.ElasticSearchQueryExecutor;
import org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupData;
import org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsStructuredIndexResultRow;
import org.broadinstitute.ddp.elastic.participantslookup.model.ESUsersIndexResultRow;
import org.broadinstitute.ddp.json.admin.participantslookup.ParticipantsLookupResultRow;
import org.broadinstitute.ddp.json.admin.participantslookup.ResultRowBase;
import org.broadinstitute.ddp.service.participantslookup.ParticipantsLookupResult;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

/**
 * Participants lookup steps.
 */
public class ESSearchSteps {

    private final ElasticSearchQueryExecutor elasticSearchQueryExecutor;

    public ESSearchSteps(RestHighLevelClient esClient, Integer resultsMaxCount) {
        this.elasticSearchQueryExecutor = new ElasticSearchQueryExecutor(esClient).setResultMaxCount(resultsMaxCount);
    }

    /**
     * Participants lookup step 1 (optional).<br>
     * Full-text search by `query` in 'users' index: search proxies.
     * In `governedUserToProxy` saved all found proxies (governedUser.guid=proxy.guid).<br>
     * <b>Search steps:</b>
     * <pre>
     * - query substring 'query' in all searchable fields of index "users";
     * - query only proxy-users having not empty 'governedUsers';
     * - join queries with 'AND'.
     * </pre>
     */
    public Map<String, ESUsersIndexResultRow> preliminarySearchProxies(
            String query,
            String usersEsIndex,
            Map<String, String> governedUserToProxy
    ) throws IOException {
        return elasticSearchQueryExecutor
                .setEsIndex(usersEsIndex)
                .setFetchSource(USERS_SOURCES)
                .search(
                        () -> {
                            var queryBuilder = queryLookupFieldsOfIndex(USERS, query);
                            var governedUsersExistBuilder = notEmptyFieldQuery(GOVERNED_USERS.getEsField());
                            return and(queryBuilder, governedUsersExistBuilder);
                        },
                        new ESUsersProxiesResultReader(governedUserToProxy)::readResults
                );
    }

    /**
     * Participants lookup step 2.<br>
     * Full-text search by `query` in 'participants' index: search proxies.
     * Plus search in governedUser.guid by guids saved in `governedUserToProxy'.<br>
     * <b>Search steps:</b>
     * <pre>
     * - find participants by a specified query;
     * - find participants invitations by a specified normalized query (removed all '-');
     * - if proxy users was found in index 'users' then find governedUsers by their GUIDs.
     * </pre>
     */
    public Map<String, ESParticipantsStructuredIndexResultRow> searchParticipants(
            String query,
            String participantsEsIndex,
            Map<String, String> governedUserToProxy,
            ParticipantsLookupResult participantsLookupResult
    ) throws IOException {
        return elasticSearchQueryExecutor
                .setEsIndex(participantsEsIndex)
                .setFetchSource(PARTICIPANTS_STRUCTURED_SOURCES)
                .search(
                        () -> {
                            var queryBuilder = queryLookupFieldsOfIndex(PARTICIPANTS_STRUCTURED, query);
                            var invitationsQueryBuilder =
                                    queryStringQuery(INVITATIONS__GUID.getEsField(), normalizeInvitationGuid(query));
                            if (governedUserToProxy != null && governedUserToProxy.size() > 0) {
                                return or(queryBuilder, invitationsQueryBuilder,
                                        orMatch(PROFILE__GUID.getEsField(), governedUserToProxy.keySet()));
                            } else {
                                return or(queryBuilder, invitationsQueryBuilder);
                            }
                        },
                        new ESParticipantsResultReader(participantsLookupResult)::readResults
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
        return elasticSearchQueryExecutor
                .setEsIndex(participantsEsIndex)
                .setFetchSource(PARTICIPANTS_STRUCTURED_SOURCES)
                .search(
                        () -> QueryBuilders.matchQuery(PROFILE__GUID.getEsField(), participantGuid),
                        new ESParticipantsResultReader(participantsLookupResult)::readResults
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

            Map<String, ESUsersIndexResultRow> proxiesExtraResults = elasticSearchQueryExecutor
                    .setEsIndex(usersEsIndex)
                    .setFetchSource(USERS_SOURCES)
                    .search(
                            () -> orMatch(PROFILE__GUID.getEsField(), governedUserToProxyExtraSearch.values()),
                            new ESUsersProxiesResultReader(governedUserToProxyExtraSearch)::readResults
                    );

            // add to result found extra proxies (which were not found during search step 1)
            for (var gu : governedUserToProxyExtraSearch.keySet()) {
                results.get(gu).setProxy(proxiesExtraResults.get(governedUserToProxyExtraSearch.get(gu)));
            }
        }
    }

    /**
     * Helper method for building {@link QueryBuilder} to search by specified 'query' in list of fields
     * specified in {@link ESParticipantsLookupData.LookupField} - taken only fields of a specified 'indexType' (or all index types).
     * @param indexType type of index
     * @param query string fragment to do full-text search
     * @return QueryBuilder - created query
     */
    private static QueryBuilder queryLookupFieldsOfIndex(ESParticipantsLookupData.IndexType indexType, String query) {
        var queryBuilder = QueryBuilders.queryStringQuery(addWildcards(query)).defaultOperator(Operator.OR);
        for (var field : ESParticipantsLookupData.LookupField.values()) {
            if (field.isQueryFieldForIndex(indexType)) {
                queryBuilder.field(field.getEsField());
            }
        }
        return queryBuilder;
    }
}
