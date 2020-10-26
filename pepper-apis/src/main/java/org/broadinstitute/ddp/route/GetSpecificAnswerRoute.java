package org.broadinstitute.ddp.route;

import java.util.Optional;

import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.AnswerCachedDao;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

public class GetSpecificAnswerRoute implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(org.broadinstitute.ddp.route.GetSpecificAnswerRoute.class);

    public GetSpecificAnswerRoute() {
    }

    @Override
    public Object handle(Request request, Response response) {
        String userGuid = request.params(RouteConstants.PathParam.USER_GUID);
        String studyGuid = request.params(RouteConstants.PathParam.STUDY_GUID);
        String stableId = request.params(RouteConstants.PathParam.STABLE_ID);

        LOG.info("Attempting to retrieve answer by stableId {} for participant {} in study {}", stableId, userGuid, studyGuid);

        return TransactionWrapper.withTxn(handle -> {
            long userId = handle.attach(UserDao.class)
                    .findUserByGuid(userGuid)
                    .map(User::getId)
                    .orElseGet(() -> {
                        String msg = String.format("Could not find user with guid %s", userGuid);
                        LOG.warn(msg);
                        throw ResponseUtil.haltError(response, 404, new ApiError(ErrorCodes.USER_NOT_FOUND, msg));
                    });
            Long studyId = handle.attach(JdbiUmbrellaStudy.class)
                    .getIdByGuid(studyGuid)
                    .orElseGet(() -> {
                        String msg = String.format("The study guid '%s' does not refer to a valid study", studyGuid);
                        LOG.warn(msg);
                        throw ResponseUtil.haltError(response, 404, new ApiError(ErrorCodes.STUDY_NOT_FOUND, msg));
                    });
            var answerDao = new AnswerCachedDao(handle);
            Optional<Answer> answer = answerDao.findAnswerByLatestInstanceAndQuestionStableId(userId, studyId, stableId);
            if (answer.isPresent()) {
                return answer.get();
            } else {
                String errorMsg = String.format("No answer found for question with stableId '%s'", stableId);
                throw ResponseUtil.haltError(response, 404, new ApiError(ErrorCodes.ANSWER_NOT_FOUND, errorMsg));
            }
        });
    }


}
