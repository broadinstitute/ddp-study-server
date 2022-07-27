package org.broadinstitute.ddp.route;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.StudyGovernanceDao;
import org.broadinstitute.ddp.db.dao.UserGovernanceDao;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.json.AgeUpResponse;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.governance.Governance;
import org.broadinstitute.ddp.model.governance.GovernancePolicy;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.broadinstitute.ddp.pex.TreeWalkInterpreter;
import org.broadinstitute.ddp.util.ResponseUtil;
import spark.Request;
import spark.Response;
import spark.Route;
import java.util.Optional;

import static org.apache.http.HttpStatus.SC_NOT_FOUND;

/**
 * Returns the boolean value whether the user has agedUp or not
 */
@Slf4j
@AllArgsConstructor
public class HasUserAgedUpRoute implements Route {

    @Override
    public AgeUpResponse handle(Request request, Response response) {
        String userGuid = request.params(PathParam.USER_GUID);
        String studyGuid = request.params(PathParam.STUDY_GUID);

        log.info("Attempting to retrieve whether the participant:{} agedUp in study:{}",
                userGuid, studyGuid);

        Boolean hasAgedUp = TransactionWrapper.withTxn(handle -> {
            Optional<Governance> governance = handle.attach(UserGovernanceDao.class)
                    .findGovernancesByParticipantAndStudyGuids(userGuid, studyGuid).findFirst();
            if (governance.isEmpty()) {
                return false;
            }
            String operatorGuid = governance.get().getProxyUserGuid();
            Long studyId = handle.attach(JdbiUmbrellaStudy.class)
                    .getIdByGuid(studyGuid)
                    .orElseGet(() -> {
                        String msg = String.format("The study guid '%s' does not refer to a valid study", studyGuid);
                        log.warn(msg);
                        throw ResponseUtil.haltError(response, SC_NOT_FOUND, new ApiError(ErrorCodes.STUDY_NOT_FOUND, msg));
                    });

            GovernancePolicy policy = handle.attach(StudyGovernanceDao.class)
                    .findPolicyByStudyId(studyId)
                    .orElse(null);
            UserProfile profile = handle.attach(UserProfileDao.class).findProfileByUserGuid(userGuid).orElse(null);
            if (profile == null || profile.getBirthDate() == null) {
                log.warn("User {} in study {} does not have profile or birth date to evaluate age-up policy, defaulting to false",
                        userGuid, studyGuid);
                return false;
            }
            if (policy == null) {
                String msg = String.format("No governance policy for study %s", studyGuid);
                log.warn(msg);
                throw ResponseUtil.haltError(response, SC_NOT_FOUND, new ApiError(ErrorCodes.NOT_FOUND, msg));
            }
            return policy.hasReachedAgeOfMajority(handle, new TreeWalkInterpreter(), userGuid, operatorGuid,
                    profile.getBirthDate());
        });
        return new AgeUpResponse(userGuid, hasAgedUp);
    }
}
