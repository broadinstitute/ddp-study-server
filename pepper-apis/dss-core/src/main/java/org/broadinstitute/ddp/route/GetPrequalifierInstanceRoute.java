package org.broadinstitute.ddp.route;

import java.util.Optional;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

/**
 * Returns the latest instance of the Prequalifier (can be easily adjusted to return more), see comments
 */
public class GetPrequalifierInstanceRoute implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(GetPrequalifierInstanceRoute.class);
    private StudyActivityDao studyActivityDao;
    private ActivityInstanceDao activityInstanceDao;

    public GetPrequalifierInstanceRoute(StudyActivityDao studyActivityDao, ActivityInstanceDao activityInstanceDao) {
        this.studyActivityDao = studyActivityDao;
        this.activityInstanceDao = activityInstanceDao;
    }

    @Override
    public UserActivity handle(Request request, Response response) {
        String userGuid = request.params(PathParam.USER_GUID);
        String studyGuid = request.params(PathParam.STUDY_GUID);

        LOG.info("Attempting to retrieve prequalifier instance summary for participant {} in study {}",
                userGuid, studyGuid);

        UserActivity activity = TransactionWrapper.withTxn(handle -> {
            Long studyId = handle.attach(JdbiUmbrellaStudy.class)
                    .getIdByGuid(studyGuid)
                    .orElseGet(() -> {
                        String msg = String.format("The study guid '%s' does not refer to a valid study", studyGuid);
                        LOG.warn(msg);
                        throw ResponseUtil.haltError(response, 404, new ApiError(ErrorCodes.STUDY_NOT_FOUND, msg));
                    });

            // We limit the number of Prequalifer codes to just 1 (can be changed later)
            Optional<String> prequalCode = studyActivityDao.getPrequalifierActivityCodeForStudy(handle, studyGuid);
            if (!prequalCode.isPresent()) {
                String msg = "Study does not have a prequalifier activity";
                ResponseUtil.haltError(response, 404, new ApiError(ErrorCodes.ACTIVITY_NOT_FOUND, msg));
            }

            // We fetch just the latest Prequalifier instance (can be changed later)
            Optional<String> latestPrequalInstanceGuid = activityInstanceDao
                    .getGuidOfLatestInstanceForUserAndActivity(handle, userGuid, prequalCode.get(), studyId);
            if (!latestPrequalInstanceGuid.isPresent()) {
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
                LOG.warn(e.getMessage());
                ResponseUtil.haltError(response, 404, new ApiError(ErrorCodes.TRANSLATION_NOT_FOUND, e.getMessage()));
            }
            return latestPrequalInstanceTranslated;
        });
        LOG.info("Retrieved prequalifier summary {} for participant {} in study {}",
                activity.getActivityInstanceGuid(), userGuid, studyGuid);
        return activity;
    }
}
