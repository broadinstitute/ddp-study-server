package org.broadinstitute.ddp.route;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.UserGovernanceDao;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.json.GovernedParticipant;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.RouteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

public class GetGovernedStudyParticipantsRoute implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(GetGovernedStudyParticipantsRoute.class);

    @Override
    public Object handle(Request request, Response response) {
        String operatorGuid = RouteUtil.getDDPAuth(request).getOperator();
        String studyGuid = request.params(RouteConstants.PathParam.STUDY_GUID);
        LOG.info("Attempting to retrieve list of governed study participants for operator {} in study {}", operatorGuid, studyGuid);

        List<GovernedParticipant> participants = TransactionWrapper.withTxn(handle -> {
            StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);
            UserProfileDao userProfileDao = handle.attach(UserProfileDao.class);
            if (studyDto == null) {
                ApiError err = new ApiError(ErrorCodes.STUDY_NOT_FOUND, "Could not find study with guid " + studyGuid);
                LOG.warn(err.getMessage());
                throw ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND, err);
            }
            return handle.attach(UserGovernanceDao.class)
                    .findActiveGovernancesByProxyAndStudyGuids(operatorGuid, studyGuid)
                    .map(governed -> new GovernedParticipant(governed.getGovernedUserGuid(), governed.getAlias(),
                            userProfileDao.findProfileByUserGuid(governed.getGovernedUserGuid()).orElse(null)))
                    .collect(Collectors.toList());
        });

        LOG.info("Found {} governed study participants for operator {} in study {}", participants.size(), operatorGuid, studyGuid);
        return participants;
    }
}
