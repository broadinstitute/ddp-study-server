package org.broadinstitute.ddp.route;

import org.apache.http.entity.ContentType;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.elastic.participantslookup.ESParticipantsLookupService;
import org.broadinstitute.ddp.elastic.participantslookup.search.ESParticipantsSearch;
import org.broadinstitute.ddp.elastic.participantslookup.search.ESUsersProxiesSearch;
import org.broadinstitute.ddp.json.admin.participantslookup.ParticipantsLookupPayload;
import org.broadinstitute.ddp.json.admin.participantslookup.ParticipantsLookupResponse;
import org.broadinstitute.ddp.service.participantslookup.ParticipantsLookupService;
import org.broadinstitute.ddp.util.ValidatedJsonInputRoute;
import spark.Request;
import spark.Response;


/**
 * Participants lookup route: handles request 'participants-lookup'.<br>
 * The searching delegated to interface {@link ParticipantsLookupService} which has implementation
 * {@link ESParticipantsLookupService} - it searches for participants in Pepper ElasticSearch database.
 *
 * <p>Request payload {@link ParticipantsLookupPayload} contains parameter 'query' with substring by which to search
 * for participants.
 *
 * <p>Response object {@link ParticipantsLookupResponse} contains result of the lookup:
 * <ul>
 *     <li>totalCount - total number of found participants (note: it can be higher than fetched number of participants
 *       - in case if specified limit (parameter 'resultsMaxCount') less than the found number of participants);
 *       </li>
 *     <li>participants - list of fetched participants (it's size could be equal to 'totalCount' or could be less -
 *     in case if number of found participants exceeds 'resultsMaxCount' - in such case real size of this list ==
 *     'resultsMaxCount' (and totalCount contains real found count).</li>
 * </ul>
 *
 * <p>For more details about search in ElasticSearch DB:
 * @see ESParticipantsLookupService
 * @see ESUsersProxiesSearch
 * @see ESParticipantsSearch
 */
public class ParticipantsLookupRoute extends ValidatedJsonInputRoute<ParticipantsLookupPayload> {

    private static final int DEFAULT_PARTICIPANTS_LOOKUP_RESULT_MAX_COUNT = 500;

    private final ParticipantsLookupService participantsLookupService;
    private final int resultsMaxCount = DEFAULT_PARTICIPANTS_LOOKUP_RESULT_MAX_COUNT;

    public ParticipantsLookupRoute(ParticipantsLookupService participantsLookupService) {
        this.participantsLookupService = participantsLookupService;
    }

    @Override
    public Object handle(Request request, Response response, ParticipantsLookupPayload payload) throws Exception {
        var studyGuid = request.params(RouteConstants.PathParam.STUDY_GUID);
        var query = payload.getQuery();

        response.type(ContentType.APPLICATION_JSON.getMimeType());

        var lookupResult = participantsLookupService.lookupParticipants(studyGuid, query, resultsMaxCount);

        return new ParticipantsLookupResponse(
                lookupResult.getTotalCount(),
                lookupResult.getResultRows()
        );
    }
}
