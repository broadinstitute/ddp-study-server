package org.broadinstitute.ddp.route;

import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.broadinstitute.ddp.constants.ErrorCodes.USER_NOT_FOUND;
import static org.broadinstitute.ddp.route.ParticipantsLookupRoute.handleParticipantLookupException;
import static org.broadinstitute.ddp.service.participantslookup.ParticipantLookupType.BY_PARTICIPANT_GUID;
import static org.broadinstitute.ddp.util.RouteUtil.haltError;

import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.service.participantslookup.ParticipantsLookupService;
import org.broadinstitute.ddp.service.participantslookup.error.ParticipantsLookupException;
import org.broadinstitute.ddp.util.RouteUtil;
import spark.Request;
import spark.Response;
import spark.Route;


/**
 * Participant lookup by GUID route: handles request GET '/studies/{studyGuid}/participants/{userGuid}'.
 */
public class ParticipantLookupByGuidRoute implements Route {

    private final ParticipantsLookupService participantsLookupService;

    public ParticipantLookupByGuidRoute(ParticipantsLookupService participantsLookupService) {
        this.participantsLookupService = participantsLookupService;
    }

    @Override
    public Object handle(Request request, Response response) throws Exception {
        var studyGuid = request.params(RouteConstants.PathParam.STUDY_GUID);
        var participantGuid = request.params(RouteConstants.PathParam.USER_GUID);

        var studyDto = RouteUtil.readStudyDto(studyGuid);

        try {
            var lookupResult = participantsLookupService.lookupParticipants(
                    BY_PARTICIPANT_GUID, studyDto, participantGuid, null);
            if (lookupResult.getResultRows().isEmpty()) {
                haltError(SC_NOT_FOUND, USER_NOT_FOUND, "Participant with guid '" + participantGuid + "' was not found");
            } else {
                return lookupResult.getResultRows().get(0);
            }
        } catch (ParticipantsLookupException e) {
            handleParticipantLookupException(e);
        }

        return null;
    }
}
