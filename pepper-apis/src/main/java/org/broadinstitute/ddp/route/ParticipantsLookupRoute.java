package org.broadinstitute.ddp.route;

import static java.lang.String.format;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.broadinstitute.ddp.constants.ErrorCodes.INVALID_REQUEST;
import static org.broadinstitute.ddp.constants.ErrorCodes.STUDY_NOT_FOUND;

import org.apache.http.entity.ContentType;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.json.admin.participantslookup.ParticipantsLookupPayload;
import org.broadinstitute.ddp.json.admin.participantslookup.ParticipantsLookupResponse;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.service.participantslookup.ParticipantsLookupService;
import org.broadinstitute.ddp.service.participantslookup.error.ParticipantsLookupException;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.ValidatedJsonInputRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;


/**
 * Participants lookup route: handles request 'participants-lookup'.<br>
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

    private static final Logger LOG = LoggerFactory.getLogger(ParticipantsLookupRoute.class);

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

        StudyDto studyDto = readStudyDto(studyGuid);

        try {
            var lookupResult = participantsLookupService.lookupParticipants(studyDto, query, resultsMaxCount);
            return new ParticipantsLookupResponse(lookupResult.getTotalCount(), lookupResult.getResultRows());
        } catch (ParticipantsLookupException e) {
            handleException(e);
        }

        return null;
    }

    private StudyDto readStudyDto(String studyGuid) {
        return TransactionWrapper.withTxn(handle -> {
            StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);
            if (studyDto == null) {
                haltError(SC_NOT_FOUND, STUDY_NOT_FOUND, format("Study with guid=%s not found", studyGuid));
            }
            return studyDto;
        });
    }

    private void handleException(ParticipantsLookupException e) {
        String code;
        int status = SC_BAD_REQUEST;
        switch (e.getErrorType()) {
            case INVALID_RESULT_MAX_COUNT:
                code = INVALID_REQUEST;
                break;
            case ELASTIC_SEARCH_STATUS:
                status = SC_INTERNAL_SERVER_ERROR;
                code = e.getRestStatus().name();
                break;
            default:
                throw new DDPException("Unknown participants lookup error type");
        }
        haltError(status, code, e.getExtendedMessage());
    }

    private void haltError(int status, String code, String msg) {
        LOG.warn(msg);
        throw ResponseUtil.haltError(status, new ApiError(code, msg));
    }
}
