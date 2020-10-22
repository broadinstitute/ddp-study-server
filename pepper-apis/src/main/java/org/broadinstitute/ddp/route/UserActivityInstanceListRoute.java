package org.broadinstitute.ddp.route;

import static org.broadinstitute.ddp.util.ResponseUtil.halt400ErrorResponse;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.ActivityInstanceDao;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dto.LanguageDto;
import org.broadinstitute.ddp.json.activity.ActivityInstanceSummary;
import org.broadinstitute.ddp.security.DDPAuth;
import org.broadinstitute.ddp.util.RouteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

/**
 * Returns activity instances for a user in a given study.
 */
public class UserActivityInstanceListRoute implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(UserActivityInstanceListRoute.class);

    private ActivityInstanceDao activityInstanceDao;

    public UserActivityInstanceListRoute(ActivityInstanceDao activityInstanceDao) {
        this.activityInstanceDao = activityInstanceDao;
    }

    @Override
    public List<ActivityInstanceSummary> handle(Request request, Response response) {
        String userGuid = request.params(RouteConstants.PathParam.USER_GUID);
        String studyGuid = request.params(RouteConstants.PathParam.STUDY_GUID);

        if (StringUtils.isBlank(userGuid)) {
            halt400ErrorResponse(response, ErrorCodes.MISSING_USER_GUID);
        }
        if (StringUtils.isBlank(studyGuid)) {
            halt400ErrorResponse(response, ErrorCodes.MISSING_STUDY_GUID);
        }

        DDPAuth ddpAuth = RouteUtil.getDDPAuth(request);
        String operatorGuid = ddpAuth.getOperator();
        LOG.info("Looking up activity instances for user {} in study {} by operator {}", userGuid, studyGuid, operatorGuid);

        return TransactionWrapper.withTxn(handle -> {
            var found = RouteUtil.findUserAndStudyOrHalt(handle, userGuid, studyGuid);
            LanguageDto preferredUserLanguage = RouteUtil.getUserLanguage(request);
            List<ActivityInstanceSummary> summaries = activityInstanceDao.listActivityInstancesForUser(
                    handle, userGuid, studyGuid, preferredUserLanguage.getIsoCode()
            );
            // IMPORTANT: do numbering before filtering so each instance is assigned their correct number.
            performActivityInstanceNumbering(summaries);
            summaries = filterActivityInstancesFromDisplay(summaries);
            activityInstanceDao.countActivitySummaryQuestionsAndAnswers(handle, userGuid, operatorGuid, studyGuid, summaries);
            activityInstanceDao.renderActivitySummary(handle, found.getUser().getId(), summaries);
            return summaries;
        });
    }

    private void performActivityInstanceNumbering(
            Collection<ActivityInstanceSummary> summaries
    ) {
        // Group summaries by activity code
        Map<String, List<ActivityInstanceSummary>> summariesByActivityCode = summaries
                .stream()
                .collect(Collectors.groupingBy(ActivityInstanceSummary::getActivityCode, Collectors.toList()));
        for (List<ActivityInstanceSummary> summariesWithTheSameCode : summariesByActivityCode.values()) {
            // No need to bother with no items
            if (summariesWithTheSameCode.isEmpty()) {
                continue;
            }
            // Sort items by date
            summariesWithTheSameCode.sort(Comparator.comparing(ActivityInstanceSummary::getCreatedAt));
            // Number items within each group.
            int counter = 1;
            for (var summary : summariesWithTheSameCode) {
                summary.setInstanceNumber(counter);
                counter++;
            }
        }
    }

    private List<ActivityInstanceSummary> filterActivityInstancesFromDisplay(List<ActivityInstanceSummary> summaries) {
        return summaries.stream()
                .filter(instance -> !instance.isExcludeFromDisplay() && !instance.isHidden())
                .collect(Collectors.toList());
    }
}
