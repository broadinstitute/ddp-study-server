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
import org.broadinstitute.ddp.content.I18nContentRenderer;
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
    private I18nContentRenderer renderer;

    public UserActivityInstanceListRoute(ActivityInstanceDao activityInstanceDao) {
        this.activityInstanceDao = activityInstanceDao;
        this.renderer = new I18nContentRenderer();
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
        LOG.info("Looking up activity instances for user {} in study {} by operator {}", userGuid, studyGuid, ddpAuth.getOperator());

        return TransactionWrapper.withTxn(handle -> {
            var found = RouteUtil.findUserAndStudyOrHalt(handle, userGuid, studyGuid);
            LanguageDto preferredUserLanguage = RouteUtil.getUserLanguage(request);
            List<ActivityInstanceSummary> summaries = activityInstanceDao.listActivityInstancesForUser(
                    handle, userGuid, studyGuid, preferredUserLanguage.getIsoCode()
            );
            performActivityInstanceNumbering(summaries);
            summaries = filterActivityInstancesFromDisplay(summaries);
            activityInstanceDao.renderActivitySummaryText(handle, found.getUser().getId(), summaries);
            return summaries;
        });
    }

    /**
     * Appends the number to the dashboard name of the activity instance summary
     * There can be multiple instances of the same activity and we need to discern
     * Thus, we group them by activity code, sort by creation date (in ascending
     * order) within each group and finally number.
     * The result is "[activity_name] - #[N]", where N is greater than 1 (we don't
     * number a single item in the group)
     */
    private void performActivityInstanceNumbering(
            Collection<ActivityInstanceSummary> summaries
    ) {
        // Group summaries by activity code
        Map<String, List<ActivityInstanceSummary>> summariesByActivityCode = summaries
                .stream()
                .collect(Collectors.groupingBy(ActivityInstanceSummary::getActivityCode, Collectors.toList()));
        for (List<ActivityInstanceSummary> summariesWithTheSameCode : summariesByActivityCode.values()) {
            // No need to bother with no items, no need to number the single item
            if (summariesWithTheSameCode.size() <= 1) {
                continue;
            }
            // Sort items by date
            summariesWithTheSameCode.sort(Comparator.comparing(ActivityInstanceSummary::getCreatedAt));
            // Number items within each group. The 1st item is not numbered so numbers start with 2
            for (int i = 1, numberWithinGroup = 2; i < summariesWithTheSameCode.size(); ++i, ++numberWithinGroup) {
                ActivityInstanceSummary summary = summariesWithTheSameCode.get(i);
                String dashboardName = summary.getActivityName();
                summary.setActivityName(dashboardName + " #" + numberWithinGroup);
            }
        }
    }

    private List<ActivityInstanceSummary> filterActivityInstancesFromDisplay(List<ActivityInstanceSummary> summaries) {
        return summaries.stream()
                .filter(instance -> !instance.isExcludeFromDisplay() && !instance.isHidden())
                .collect(Collectors.toList());
    }
}
