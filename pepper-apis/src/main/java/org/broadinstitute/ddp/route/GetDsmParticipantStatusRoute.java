package org.broadinstitute.ddp.route;

import org.apache.http.entity.ContentType;
import org.broadinstitute.ddp.client.DsmClient;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.dsm.ParticipantStatusTrackingInfo;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.RouteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

/**
 * This route calls DSM and returns the DTO after enriching it with certain information
 */
public class GetDsmParticipantStatusRoute implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(GetDsmParticipantStatusRoute.class);

    private final DsmClient dsm;

    public GetDsmParticipantStatusRoute(DsmClient dsmClient) {
        this.dsm = dsmClient;
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
        String token = RouteUtil.getDDPAuth(request).getToken();

        LOG.info("Attempting to fetch DSM participant status for {} in study {}", userGuid, studyGuid);

        response.type(ContentType.APPLICATION_JSON.getMimeType());
        return process(studyGuid, userGuid, token);
    }

    ParticipantStatusTrackingInfo process(String studyGuid, String userGuid, String token) {
        // User guid or study guid might not exist, or user might not be in study.
        // In all these cases, there won't be an enrollment status, so we return 404.
        EnrollmentStatusType status = TransactionWrapper.withTxn(handle -> handle
                .attach(JdbiUserStudyEnrollment.class)
                .getEnrollmentStatusByUserAndStudyGuids(userGuid, studyGuid)
                .orElseThrow(() -> {
                    String errMsg = "Participant " + userGuid + " or study " + studyGuid + " not found";
                    LOG.warn(errMsg);
                    throw ResponseUtil.haltError(404, new ApiError(ErrorCodes.NOT_FOUND, errMsg));
                }));

        var result = dsm.getParticipantStatus(studyGuid, userGuid, token);
        result.runIfThrown(e -> LOG.error("Failed to fetch participant status from DSM", e));
        LOG.info("DSM call completed, study={} and participant={}, status={}", studyGuid, userGuid, result.getStatusCode());

        if (result.getStatusCode() == 200) {
            return new ParticipantStatusTrackingInfo(result.getBody(), status, userGuid);
        } else if (result.getStatusCode() == 404) {
            String errMsg = "Participant " + userGuid + " or study " + studyGuid + " not found";
            LOG.warn(errMsg);
            throw ResponseUtil.haltError(404, new ApiError(ErrorCodes.NOT_FOUND, errMsg));
        } else {
            // The user doesn't need to know the details, just general information, so we convert
            // all unrecognized statuses caused by DSM interaction into HTTP 500
            String errMsg = "Something went wrong with DSM interaction while trying to fetch the status "
                    + " for the study " + studyGuid + " and participant " + userGuid
                    + ". The returned HTTP status is " + result.getStatusCode();
            LOG.error(errMsg);
            throw ResponseUtil.haltError(500, new ApiError(ErrorCodes.SERVER_ERROR, errMsg));
        }
    }
}
