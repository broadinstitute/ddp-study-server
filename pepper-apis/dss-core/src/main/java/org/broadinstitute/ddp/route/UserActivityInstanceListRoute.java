package org.broadinstitute.ddp.route;

import static org.broadinstitute.ddp.util.ResponseUtil.halt400ErrorResponse;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dto.LanguageDto;
import org.broadinstitute.ddp.json.activity.ActivityInstanceSummary;
import org.broadinstitute.ddp.model.activity.instance.FormResponse;
import org.broadinstitute.ddp.security.DDPAuth;
import org.broadinstitute.ddp.service.ActivityInstanceService;
import org.broadinstitute.ddp.util.RouteUtil;
import spark.Request;
import spark.Response;
import spark.Route;

/**
 * Returns activity instances for a user in a given study.
 */
@Slf4j
@AllArgsConstructor
public class UserActivityInstanceListRoute implements Route {
    private final ActivityInstanceService service;

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
        String operatorGuid = StringUtils.defaultIfBlank(ddpAuth.getOperator(), userGuid);
        boolean isStudyAdmin = ddpAuth.hasAdminAccessToStudy(studyGuid);

        log.info("Looking up activity instances for user {} in study {} by operator {} (isStudyAdmin={})",
                userGuid, studyGuid, operatorGuid, isStudyAdmin);

        return TransactionWrapper.withTxn(handle -> {
            var found = RouteUtil.findUserAndStudyOrHalt(handle, userGuid, studyGuid);
            LanguageDto preferredUserLanguage = RouteUtil.getUserLanguage(request);
            List<ActivityInstanceSummary> summaries = service.listTranslatedInstanceSummaries(
                    handle, userGuid, studyGuid, preferredUserLanguage.getIsoCode()
            );
            if (!isStudyAdmin) {
                // Study admins are allowed to view all the data, so if they're NOT admin then do filtering.
                summaries = filterActivityInstancesFromDisplay(summaries);
            }
            Map<String, FormResponse> responses = service.countQuestionsAndAnswers(
                    handle, userGuid, operatorGuid, studyGuid, summaries);
            service.renderInstanceSummaries(handle, found.getUser().getId(), operatorGuid, studyGuid, summaries, responses);
            return summaries;
        });
    }

    private List<ActivityInstanceSummary> filterActivityInstancesFromDisplay(List<ActivityInstanceSummary> summaries) {
        return summaries.stream()
                .filter(instance -> !instance.isHidden())
                .collect(Collectors.toList());
    }
}
