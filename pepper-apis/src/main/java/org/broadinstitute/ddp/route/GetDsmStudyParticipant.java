package org.broadinstitute.ddp.route;

import static org.broadinstitute.ddp.constants.RouteConstants.PathParam.STUDY_GUID;
import static org.broadinstitute.ddp.constants.RouteConstants.PathParam.USER_GUID;

import java.util.Optional;

import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.DsmStudyParticipantDao;
import org.broadinstitute.ddp.db.dto.dsm.DsmStudyParticipant;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

public class GetDsmStudyParticipant implements Route {
    private static final Logger LOG = LoggerFactory.getLogger(GetDsmStudyParticipant.class);

    @Override
    public Object handle(Request request, Response response) {
        return TransactionWrapper.withTxn(handle -> {
            String studyGuid = request.params(STUDY_GUID);
            String userGuidOrAltpid = request.params(USER_GUID);

            LOG.info("Retrieving participant for user with GUID: {}", userGuidOrAltpid);

            DsmStudyParticipantDao dsmStudyParticipantDao = handle.attach(DsmStudyParticipantDao.class);
            Optional<DsmStudyParticipant> optionalParticipant = dsmStudyParticipantDao
                    .findStudyParticipant(userGuidOrAltpid, studyGuid, EnrollmentStatusType.getNonExitedStates());
            if (optionalParticipant.isPresent()) {
                return optionalParticipant.get();
            } else {
                LOG.info("GUID retrieval failed, trying legacy AltPid: {}", userGuidOrAltpid);
                optionalParticipant = dsmStudyParticipantDao
                        .findStudyParticipant(userGuidOrAltpid, studyGuid, EnrollmentStatusType.getAllStates());

                if (optionalParticipant.isPresent()) {
                    return optionalParticipant.get();
                } else {
                    ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND, new ApiError(ErrorCodes.NOT_FOUND,
                            "Participant with User GUID OR legacy_altpid " + userGuidOrAltpid + " in study " + studyGuid
                                    + " was not found"));
                    return null;
                }
            }

        });
    }
}
