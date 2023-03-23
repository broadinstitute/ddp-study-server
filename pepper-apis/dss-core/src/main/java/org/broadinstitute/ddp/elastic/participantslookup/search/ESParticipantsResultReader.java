package org.broadinstitute.ddp.elastic.participantslookup.search;

import static org.broadinstitute.ddp.elastic.ElasticSearchQueryUtil.getJsonObject;
import static org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupData.GUID;
import static org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupData.IndexSource.INVITATIONS;
import static org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupData.IndexSource.PROFILE;
import static org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupData.IndexSource.PROXIES;
import static org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsLookupData.IndexSource.STATUS;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import org.broadinstitute.ddp.elastic.participantslookup.model.ESParticipantsStructuredIndexResultRow;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.service.participantslookup.ParticipantsLookupResult;
import org.broadinstitute.ddp.util.GsonUtil;
import org.elasticsearch.action.search.SearchResponse;


/**
 * Process results found in index "participants_structured".
 */
public class ESParticipantsResultReader {

    private static final Gson gson = GsonUtil.standardGson();

    private final ParticipantsLookupResult participantsLookupResult;

    /**
     * Constructor.
     * @param participantsLookupResult object to store final result (here it needs to set to it detected 'totalCount'
     */
    public ESParticipantsResultReader(ParticipantsLookupResult participantsLookupResult) {
        this.participantsLookupResult = participantsLookupResult;
    }

    /**
     * Read "participants_structured" index result.
     * @param response search response from ElasticSearch
     */
    public Map<String, ESParticipantsStructuredIndexResultRow> readResults(SearchResponse response) {
        Map<String, ESParticipantsStructuredIndexResultRow> results = new HashMap<>();

        for (var hit : response.getHits()) {
            var hitAsJson = getJsonObject(hit);
            var participantResult = gson.fromJson(
                    hitAsJson.get(PROFILE.getSource()), ESParticipantsStructuredIndexResultRow.class);

            var invitations = hitAsJson.get(INVITATIONS.getSource());
            // expect that only one invitation exist (at least read a 1st one)
            if (invitations != null && invitations.getAsJsonArray().size() > 0) {
                participantResult.setInvitationId(
                        invitations.getAsJsonArray().get(0).getAsJsonObject().get(GUID).getAsString());
            }

            var status = hitAsJson.get(STATUS.getSource());
            if (status != null) {
                participantResult.setStatus(EnrollmentStatusType.valueOf(status.getAsString()));
            }

            var proxies = gson.fromJson(hitAsJson.get(PROXIES.getSource()), String[].class);
            if (proxies != null) {
                for (var proxyGuid : proxies) {
                    participantResult.getProxies().add(proxyGuid);
                }
            }

            results.put(participantResult.getGuid(), participantResult);
        }
        participantsLookupResult.setTotalCount((int) response.getHits().getTotalHits().value);

        return results;
    }
}
