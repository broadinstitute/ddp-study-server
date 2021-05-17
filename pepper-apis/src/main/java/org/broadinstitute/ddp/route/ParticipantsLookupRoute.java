package org.broadinstitute.ddp.route;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.broadinstitute.ddp.constants.ErrorCodes.INVALID_REQUEST;
import static org.broadinstitute.ddp.service.participantslookup.ParticipantLookupType.FULL_TEXT_SEARCH_BY_QUERY_STRING;
import static org.broadinstitute.ddp.util.RouteUtil.haltError;

import org.apache.http.entity.ContentType;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.json.admin.participantslookup.ParticipantsLookupPayload;
import org.broadinstitute.ddp.json.admin.participantslookup.ParticipantsLookupResponse;
import org.broadinstitute.ddp.service.participantslookup.ParticipantsLookupService;
import org.broadinstitute.ddp.service.participantslookup.error.ParticipantsLookupException;
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
public class ParticipantsLookupRoute extends ValidatedJsonInputRoute<ParticipantsLookupPayload> {

    /**
     * It is temporarily specified in this class, but in the future it should be
     * passed from a client side as a parameter.
     */
    public static final int DEFAULT_PARTICIPANTS_LOOKUP_RESULT_MAX_COUNT = 500;

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

        var studyDto = RouteUtil.readStudyDto(studyGuid);

        try {
            var lookupResult = participantsLookupService.lookupParticipants(
                    FULL_TEXT_SEARCH_BY_QUERY_STRING, studyDto, query, resultsMaxCount);
            return new ParticipantsLookupResponse(lookupResult.getTotalCount(), lookupResult.getResultRows());
        } catch (ParticipantsLookupException e) {
            handleParticipantLookupException(e);
        }

        return null;
    }

    @Override
    protected int getValidationErrorStatus() {
        return SC_BAD_REQUEST;
    }

    static void handleParticipantLookupException(ParticipantsLookupException e) {
        String code;
        int status;
        switch (e.getErrorType()) {
            case INVALID_RESULT_MAX_COUNT:
                status = SC_BAD_REQUEST;
                code = INVALID_REQUEST;
                break;
            case SEARCH_ERROR:
                status = SC_INTERNAL_SERVER_ERROR;
                code = e.getErrorCode();
                break;
            default:
                throw new DDPException("Unknown participants lookup error type");
        }
        haltError(status, code, e.getExtendedMessage());
    }
}
