package org.broadinstitute.ddp.route;

import java.util.Optional;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.db.ActivityInstanceDao;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.StudyActivityDao;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.json.UserActivity;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.security.DDPAuth;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.RouteUtil;
import spark.Request;
import spark.Response;
import spark.Route;

/**
 * Returns the latest instance of the Prequalifier (can be easily adjusted to return more), see comments
 */
@Slf4j
@AllArgsConstructor
public class GetPrequalifierInstanceRoute implements Route {
    private final StudyActivityDao studyActivityDao;
    private final ActivityInstanceDao activityInstanceDao;

    @Override
    public UserActivity handle(Request request, Response response) {
        String userGuid = request.params(PathParam.USER_GUID);
        String studyGuid = request.params(PathParam.STUDY_GUID);

        log.info("Attempting to retrieve prequalifier instance summary for participant {} in study {}",
                userGuid, studyGuid);

        UserActivity activity = TransactionWrapper.withTxn(handle -> {
            Long studyId = handle.attach(JdbiUmbrellaStudy.class)
                    .getIdByGuid(studyGuid)
                    .orElseGet(() -> {
                        String msg = String.format("The study guid '%s' does not refer to a valid study", studyGuid);
                        log.warn(msg);
                        throw ResponseUtil.haltError(response, 404, new ApiError(ErrorCodes.STUDY_NOT_FOUND, msg));
                    });

            // We limit the number of Prequalifer codes to just 1 (can be changed later)
            Optional<String> prequalCode = studyActivityDao.getPrequalifierActivityCodeForStudy(handle, studyGuid);
            if (prequalCode.isEmpty()) {
                String msg = "Study does not have a prequalifier activity";
                ResponseUtil.haltError(response, 404, new ApiError(ErrorCodes.ACTIVITY_NOT_FOUND, msg));
            }

            // We fetch just the latest Prequalifier instance (can be changed later)
            Optional<String> latestPrequalInstanceGuid = activityInstanceDao
                    .getGuidOfLatestInstanceForUserAndActivity(handle, userGuid, prequalCode.get(), studyId);
            if (latestPrequalInstanceGuid.isEmpty()) {
                String msg = "User does not have an activity instance for study prequalifier activity";
                ResponseUtil.haltError(response, 404, new ApiError(ErrorCodes.ACTIVITY_NOT_FOUND, msg));
            }

            DDPAuth ddpAuth = RouteUtil.getDDPAuth(request);

            UserActivity latestPrequalInstanceTranslated = null;
            try {
                latestPrequalInstanceTranslated = activityInstanceDao.getTranslatedSummaryByGuid(
                        handle,
                        latestPrequalInstanceGuid.get(),
                        ddpAuth.getPreferredLanguage()
                );
            } catch (DaoException e) {
                log.warn(e.getMessage());
                ResponseUtil.haltError(response, 404, new ApiError(ErrorCodes.TRANSLATION_NOT_FOUND, e.getMessage()));
            }
            return latestPrequalInstanceTranslated;
        });
        log.info("Retrieved prequalifier summary {} for participant {} in study {}",
                activity.getActivityInstanceGuid(), userGuid, studyGuid);
        return activity;
    }
}
