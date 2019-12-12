package org.broadinstitute.ddp.route;

import org.apache.commons.lang3.StringUtils;

import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.dsm.DsmCallResponse;
import org.broadinstitute.ddp.model.dsm.ParticipantStatusTrackingInfo;
import org.broadinstitute.ddp.security.DDPAuth;
import org.broadinstitute.ddp.service.DsmParticipantStatusService;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.RouteUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spark.Request;
import spark.Response;
import spark.Route;

/**
 *  This route calls DSM and returns the DTO after enriching it with certain information
 */
public class GetDsmParticipantStatusRoute implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(GetDsmParticipantStatusRoute.class);
    private final DsmParticipantStatusService service;

    public GetDsmParticipantStatusRoute(DsmParticipantStatusService service) {
        this.service = service;
    }

    /**
     * Halts with
     * 1) 404 if the participant doesn't exist in Pepper/DSM
     * 2) 400 if the studyGuid / userGuid is malformed
     * 3) 500 (general) in case of other unexpected errors
     */
    @Override
    public ParticipantStatusTrackingInfo handle(Request request, Response response) {
        String studyGuid = request.params(RouteConstants.PathParam.STUDY_GUID);
        String userGuid = request.params(RouteConstants.PathParam.USER_GUID);
        if (StringUtils.isBlank(studyGuid)) {
            LOG.warn("Study GUID is blank");
            ResponseUtil.halt400ErrorResponse(response, ErrorCodes.MISSING_STUDY_GUID);
        }
        if (StringUtils.isBlank(userGuid)) {
            LOG.warn("Participant GUID is blank");
            ResponseUtil.halt400ErrorResponse(response, ErrorCodes.MISSING_USER_GUID);
        }
        DDPAuth ddpAuth = RouteUtil.getDDPAuth(request);
        DsmCallResponse status = service.fetchParticipantStatus(studyGuid, userGuid, ddpAuth.getToken());
        LOG.info("DSM call completed, study = {} and participant = {}, status = ", studyGuid, userGuid, status.getHttpStatus());
        String errMsg = null;
        if (status.getHttpStatus() == 404) {
            errMsg = "Participant " + userGuid + " or study " + studyGuid + " not found";
            LOG.warn(errMsg);
            ResponseUtil.haltError(response, 404, new ApiError(ErrorCodes.NOT_FOUND, errMsg));
        } else if (status.getHttpStatus() != 200) {
            // The user doesn't need to know the details, just general information, so we convert
            // all unrecognized statuses caused by DSM interaction into HTTP 500
            errMsg = "Something went wrong with DSM interaction while trying to fetch the status "
                    + " for the study " + studyGuid + " and participant " + userGuid
                    + ". The returned HTTP status is " + status.getHttpStatus();
            LOG.error(errMsg);
            ResponseUtil.haltError(response, 500, new ApiError(ErrorCodes.SERVER_ERROR, errMsg));
        }
        return status.getParticipantStatusTrackingInfo();
    }

}
