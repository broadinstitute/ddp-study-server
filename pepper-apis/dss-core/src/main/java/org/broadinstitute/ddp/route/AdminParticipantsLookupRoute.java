package org.broadinstitute.ddp.route;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.broadinstitute.ddp.route.AdminParticipantsLookupUtil.handleParticipantLookupException;
import static org.broadinstitute.ddp.service.participantslookup.ParticipantLookupType.FULL_TEXT_SEARCH_BY_QUERY_STRING;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.entity.ContentType;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.json.admin.participantslookup.ParticipantsLookupPayload;
import org.broadinstitute.ddp.json.admin.participantslookup.ParticipantsLookupResponse;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.service.participantslookup.ParticipantsLookupService;
import org.broadinstitute.ddp.service.participantslookup.error.ParticipantsLookupException;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.RouteUtil;
import org.broadinstitute.ddp.util.ValidatedJsonInputRoute;
import spark.Request;
import spark.Response;


/**
 * Participants lookup route: handles request POST 'participants-lookup'.<br>
 * The searching delegated to service {@link ParticipantsLookupService} which has implementation
 * searching for participants in Pepper ElasticSearch database (but in future in could be added
 * other types of participants lookup - for example in MySQL DB).
 *
 * <p>Request payload {@link ParticipantsLookupPayload} contains parameter 'query' with substring by which to search
 * for participants.
 *
 * <p>Response object {@link ParticipantsLookupResponse} contains result of the lookup:
 * <ul>
 *     <li>totalCount - total number of found participants (note: it can be higher than fetched number of participants
 *       - in case if specified limit (parameter 'resultsMaxCount') less than the found number of participants);
 *       </li>
 *     <li>results - list of fetched participants (it's size could be equal to 'totalCount' or could be less -
 *     in case if number of found participants exceeds 'resultsMaxCount' - in such case real size of this list ==
 *     'resultsMaxCount' (and totalCount contains real found count).</li>
 * </ul>
 */
@Slf4j
@AllArgsConstructor
public class AdminParticipantsLookupRoute extends ValidatedJsonInputRoute<ParticipantsLookupPayload> {
    /**
     * It is temporarily specified in this class, but in the future it should be
     * passed from a client side as a parameter.
     */
    public static final int DEFAULT_PARTICIPANTS_LOOKUP_RESULT_MAX_COUNT = 500;

    private final ParticipantsLookupService participantsLookupService;


    @Override
    public Object handle(Request request, Response response, ParticipantsLookupPayload payload) throws Exception {
        var studyGuid = request.params(RouteConstants.PathParam.STUDY_GUID);
        var query = payload.getQuery();

        response.type(ContentType.APPLICATION_JSON.getMimeType());

        var studyDto = RouteUtil.readStudyDto(studyGuid, this::haltError);

        try {
            var lookupResult = participantsLookupService.lookupParticipants(
                    FULL_TEXT_SEARCH_BY_QUERY_STRING, studyDto, query, DEFAULT_PARTICIPANTS_LOOKUP_RESULT_MAX_COUNT);
            return new ParticipantsLookupResponse(lookupResult.getTotalCount(), lookupResult.getResultRows());
        } catch (ParticipantsLookupException e) {
            handleParticipantLookupException(e, this::haltError);
        }

        return null;
    }

    @Override
    protected int getValidationErrorStatus() {
        return SC_BAD_REQUEST;
    }

    public void haltError(int status, String code, String msg) {
        log.warn(msg);
        throw ResponseUtil.haltError(status, new ApiError(code, msg));
    }
}
