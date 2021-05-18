package org.broadinstitute.ddp.route;

import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.broadinstitute.ddp.constants.ErrorCodes.USER_NOT_FOUND;
import static org.broadinstitute.ddp.route.AdminParticipantsLookupUtil.handleParticipantLookupException;
import static org.broadinstitute.ddp.service.participantslookup.ParticipantLookupType.BY_PARTICIPANT_GUID;

import org.apache.http.entity.ContentType;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.service.participantslookup.ParticipantsLookupService;
import org.broadinstitute.ddp.service.participantslookup.error.ParticipantsLookupException;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.RouteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;


/**
 * Participant lookup by GUID route: handles request GET '/studies/{studyGuid}/participants/{userGuid}'.
 */
public class AdminParticipantLookupByGuidRoute implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(AdminParticipantLookupByGuidRoute.class);

    private final ParticipantsLookupService participantsLookupService;

    public AdminParticipantLookupByGuidRoute(ParticipantsLookupService participantsLookupService) {
        this.participantsLookupService = participantsLookupService;
    }

    @Override
    public Object handle(Request request, Response response) throws Exception {
        var studyGuid = request.params(RouteConstants.PathParam.STUDY_GUID);
        var participantGuid = request.params(RouteConstants.PathParam.USER_GUID);

        response.type(ContentType.APPLICATION_JSON.getMimeType());

        var studyDto = RouteUtil.readStudyDto(studyGuid, this::haltError);

        try {
            var lookupResult = participantsLookupService.lookupParticipants(
                    BY_PARTICIPANT_GUID, studyDto, participantGuid, null);
            if (lookupResult.getResultRows().isEmpty()) {
                haltError(SC_NOT_FOUND, USER_NOT_FOUND, "Participant with guid '" + participantGuid + "' was not found");
            } else {
                return lookupResult.getResultRows().get(0);
            }
        } catch (ParticipantsLookupException e) {
            handleParticipantLookupException(e, this::haltError);
        }

        return null;
    }

    public void haltError(int status, String code, String msg) {
        LOG.warn(msg);
        throw ResponseUtil.haltError(status, new ApiError(code, msg));
    }
}
