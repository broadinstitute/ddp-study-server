package org.broadinstitute.ddp.route;

import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.json.DynamicSelectAnswersResponse;
import org.broadinstitute.ddp.security.DDPAuth;
import org.broadinstitute.ddp.util.RouteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

import java.util.stream.Collectors;

/**
 *  This route returns the list of answers on previous questions that pointed in basedQuestion param
 */
public class GetDynamicAnswersBasedOnQuestionsRoute implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(GetDynamicAnswersBasedOnQuestionsRoute.class);

    @Override
    public Object handle(Request request, Response response) {
        DDPAuth ddpAuth = RouteUtil.getDDPAuth(request);
        String studyGuid = request.params(RouteConstants.PathParam.STUDY_GUID);
        String userGuid = request.params(RouteConstants.PathParam.USER_GUID);
        String stableId = request.params(RouteConstants.PathParam.STABLE_ID);

        LOG.info("Attempting to get answers based on questions for dynamic question {} for user {} in study {} by operator {}",
                stableId, userGuid, studyGuid, ddpAuth.getOperator());

        return new DynamicSelectAnswersResponse(TransactionWrapper.withTxn(handle ->
                handle.attach(AnswerDao.class).findDynamicSelectAnswerByUserGuidAndStableId(userGuid, stableId)
                        .collect(Collectors.toList())));
    }
}
