package org.broadinstitute.ddp.elastic.participantslookup;

import static org.broadinstitute.ddp.elastic.participantslookup.ESParticipantsStructuredIndexSearchHelper.searchInParticipantsStructuredIndex;
import static org.broadinstitute.ddp.elastic.participantslookup.ESUsersIndexSearchHelper.searchInUsersIndex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsStructuredIndexResultRow;
import org.broadinstitute.ddp.elastic.participantslookup.model.ESUsersIndexResultRow;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.json.admin.participantslookup.ParticipantsLookupResultRow;
import org.broadinstitute.ddp.json.admin.participantslookup.ParticipantsLookupResultRowBase;
import org.broadinstitute.ddp.service.participantslookup.ParticipantsLookupResult;
import org.broadinstitute.ddp.service.participantslookup.ParticipantsLookupService;
import org.elasticsearch.client.RestHighLevelClient;

/**
 * Participants lookup service implementation for searching in Pepper ElasticSearch database
 */
public class ESParticipantsLookupService implements ParticipantsLookupService {

    private final RestHighLevelClient esClient;

    public ESParticipantsLookupService(RestHighLevelClient esClient) {
        this.esClient = esClient;
    }

    @Override
    public ParticipantsLookupResult lookupParticipants(String studyGuid, String query, int resultsMaxCount) {

        ParticipantsLookupResult participantsLookupResult = new ParticipantsLookupResult();

        Map<String, String> governedUserToProxy = new HashMap<>();

        try {
            var usersResults = searchInUsersIndex(
                    esClient, studyGuid, query, governedUserToProxy, resultsMaxCount);

            var participantsStructuredResults = searchInParticipantsStructuredIndex(
                    esClient, studyGuid, query, governedUserToProxy, resultsMaxCount, participantsLookupResult);
            var esResultRows = mergeParticipantsWithProxyResults(
                    usersResults, participantsStructuredResults, governedUserToProxy);

            participantsLookupResult.setResultRows(esResultRows);
        } catch (Exception e) {
            throw new DDPException(e);
        }
        return participantsLookupResult;
    }

    /**
     * Adds to results of "participants_structured" index the 'proxies' data results (taken from
     * "users" index results).
     */
    private List<ParticipantsLookupResultRow> mergeParticipantsWithProxyResults(
            Map<String, ESUsersIndexResultRow> usersResults,
            Map<String, ESParticipantsStructuredIndexResultRow> participantsStructuredResults,
            Map<String, String> governedUserToProxy) {
        List<ParticipantsLookupResultRow> resultList = new ArrayList<>();
        for (var participant : participantsStructuredResults.values()) {
            ParticipantsLookupResultRow resultRow = new ParticipantsLookupResultRow(participant);
            resultRow.setInvitationId(participant.getInvitationId());
            resultRow.setStatus(participant.getStatus());
            var proxyGuid = governedUserToProxy.get(participant.getGuid());
            if (proxyGuid != null) {
                resultRow.setProxy(new ParticipantsLookupResultRowBase(usersResults.get(proxyGuid)));
            }
            resultList.add(resultRow);
        }
        return resultList;
    }
}
