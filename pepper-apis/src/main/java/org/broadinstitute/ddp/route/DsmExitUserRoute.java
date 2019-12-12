package org.broadinstitute.ddp.route;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.DataExportDao;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

/**
 * When called by DSM and given the user/study GUIDs, exits a user from a study
 * and makes all of his/her activity instances read-only
 * Returns:
 * 1) HTTP 200 if all is ok
 * 2) HTTP 404 if either user or study is not found
 * 3) HTTP 500 if the number of users affected is greater than 1
 */
public class DsmExitUserRoute implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(DsmExitUserRoute.class);

    @Override
    public Object handle(Request request, Response response) {
        String studyGuid = request.params(PathParam.STUDY_GUID);
        String participantGuidOrLegacyAltPid = request.params(PathParam.USER_GUID);
        if (StringUtils.isBlank(studyGuid)) {
            String errMsg = "Study GUID is a mandatory path parameter";
            LOG.warn(errMsg);
            ResponseUtil.haltError(response, HttpStatus.SC_INTERNAL_SERVER_ERROR, new ApiError(ErrorCodes.MISSING_STUDY_GUID, errMsg));
        }
        if (StringUtils.isBlank(participantGuidOrLegacyAltPid)) {
            String errMsg = "User GUID is a mandatory path parameter";
            LOG.warn(errMsg);
            ResponseUtil.haltError(response, HttpStatus.SC_INTERNAL_SERVER_ERROR, new ApiError(ErrorCodes.MISSING_USER_GUID, errMsg));
        }
        LOG.info("Requested to terminate the enrollment of the user {} in the study {}", participantGuidOrLegacyAltPid, studyGuid);
        TransactionWrapper.useTxn(
                handle -> {
                    StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);
                    // No study found, respond with HTTP 404
                    if (studyDto == null) {
                        String errMsg = "A study with GUID " + studyGuid + " whose enrollment you try to terminate is not found";
                        LOG.warn(errMsg);
                        throw ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND, new ApiError(ErrorCodes.NOT_FOUND, errMsg));
                    }

                    UserDto userDto = handle.attach(JdbiUser.class).findByLegacyAltPidIfNotFoundByUserGuid(participantGuidOrLegacyAltPid);
                    if (userDto == null) {
                        ApiError err = new ApiError(ErrorCodes.USER_NOT_FOUND, "Could not find participant "
                                + "with GUID or Legacy AltPid " + participantGuidOrLegacyAltPid);
                        LOG.warn(err.getMessage());
                        throw ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND, err);
                    }

                    String userGuid = userDto.getUserGuid();
                    LOG.info("Terminating the enrollment of the user {} in the study {}", userGuid, studyGuid);
                    try {
                        handle.attach(JdbiUserStudyEnrollment.class).terminateStudyEnrollment(userGuid, studyGuid);
                    } catch (DaoException e) {
                        String errMsg = e.getMessage();
                        LOG.warn(errMsg);
                        ResponseUtil.haltError(
                                response, HttpStatus.SC_INTERNAL_SERVER_ERROR,
                                new ApiError(ErrorCodes.SERVER_ERROR, errMsg)
                        );
                    }
                    LOG.info("Marking all activity instance of the user {} in the study {} as read-only", userGuid, studyGuid);
                    int numUpdated = handle.attach(JdbiActivityInstance.class).makeUserActivityInstancesReadonly(studyGuid, userGuid);
                    if (numUpdated > 0) {
                        LOG.info(
                                "Successfully terminated the enrollment of the user {} in the study {}"
                                        + " and marked {} activity instances as read-only", userGuid, studyGuid, numUpdated
                        );
                    } else {
                        LOG.info("User {} doesn't have any activity instances, so nothing to mark as read-only", numUpdated);
                    }
                    handle.attach(DataExportDao.class).queueDataSync(userDto.getUserId(), studyDto.getId());
                    response.status(HttpStatus.SC_OK);
                }
        );
        return null;
    }

}
