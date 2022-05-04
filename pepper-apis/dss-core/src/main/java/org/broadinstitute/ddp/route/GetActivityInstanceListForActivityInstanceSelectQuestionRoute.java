package org.broadinstitute.ddp.route;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiQuestionCached;
import org.broadinstitute.ddp.db.dto.LanguageDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.json.ActivityInstanceSelectAnswerSubmission;
import org.broadinstitute.ddp.json.ActivityInstanceSelectAnswersResponse;
import org.broadinstitute.ddp.json.activity.ActivityInstanceSummary;
import org.broadinstitute.ddp.model.activity.instance.FormResponse;
import org.broadinstitute.ddp.security.DDPAuth;
import org.broadinstitute.ddp.service.ActivityInstanceService;
import org.broadinstitute.ddp.util.RouteUtil;
import spark.Request;
import spark.Response;
import spark.Route;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 *  This route returns the list of activity instances to select in the question
 */
@Slf4j
@AllArgsConstructor
public class GetActivityInstanceListForActivityInstanceSelectQuestionRoute implements Route {
    private final ActivityInstanceService service;

    @Override
    public Object handle(Request request, Response response) {
        DDPAuth ddpAuth = RouteUtil.getDDPAuth(request);
        String studyGuid = request.params(RouteConstants.PathParam.STUDY_GUID);
        String userGuid = request.params(RouteConstants.PathParam.USER_GUID);
        String stableId = request.params(RouteConstants.PathParam.STABLE_ID);

        log.info("Attempting to get data for activity instance question {} for user {} in study {} by operator {}",
                stableId, userGuid, studyGuid, ddpAuth.getOperator());

        String operatorGuid = StringUtils.defaultIfBlank(ddpAuth.getOperator(), userGuid);
        boolean isStudyAdmin = ddpAuth.hasAdminAccessToStudy(studyGuid);

        var instanceSummaries = TransactionWrapper.withTxn(handle -> {
            var found = RouteUtil.findUserAndStudyOrHalt(handle, userGuid, studyGuid);
            LanguageDto preferredUserLanguage = RouteUtil.getUserLanguage(request);
            var jdbiQuestion = new JdbiQuestionCached(handle);
            QuestionDto questionDto = jdbiQuestion
                    .findLatestDtoByStudyIdAndQuestionStableId(found.getStudyDto().getId(), stableId)
                    .orElseThrow(() -> new DDPException("Could not find question with stableId " + stableId));

            Set<String> activityCodes = new HashSet<>(jdbiQuestion
                    .getActivityCodesByActivityInstanceSelectQuestionId(questionDto.getId()));

            List<ActivityInstanceSummary> summaries = service.findTranslatedInstanceSummaries(
                    handle, userGuid, studyGuid, activityCodes, preferredUserLanguage.getIsoCode());
            if (!isStudyAdmin) {
                // Study admins are allowed to view all the data, so if they're NOT admin then do filtering.
                summaries = summaries.stream()
                        .filter(instance -> !instance.isHidden())
                        .collect(Collectors.toList());
            }
            Map<String, FormResponse> responses = service.countQuestionsAndAnswers(
                    handle, userGuid, operatorGuid, studyGuid, summaries);
            // TODO: Filter out disabled activity instances DDP-7085
            service.renderInstanceSummaries(handle, found.getUser().getId(), operatorGuid, studyGuid, summaries, responses);
            return summaries;
        });

        List<ActivityInstanceSelectAnswerSubmission> results = new ArrayList<>();
        instanceSummaries.forEach(summary -> results.add(
                new ActivityInstanceSelectAnswerSubmission(summary.getActivityInstanceGuid(), summary.getActivityName())));

        return new ActivityInstanceSelectAnswersResponse(results);
    }
}
