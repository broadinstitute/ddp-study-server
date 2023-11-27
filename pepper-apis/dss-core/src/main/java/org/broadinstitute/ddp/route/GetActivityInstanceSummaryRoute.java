package org.broadinstitute.ddp.route;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.LanguageDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.json.activity.ActivityInstanceSummary;
import org.broadinstitute.ddp.model.activity.instance.FormResponse;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.security.DDPAuth;
import org.broadinstitute.ddp.service.ActivityInstanceService;
import org.broadinstitute.ddp.util.RouteUtil;
import spark.Request;
import spark.Response;
import spark.Route;

@Slf4j
@AllArgsConstructor
public class GetActivityInstanceSummaryRoute implements Route {
    private final ActivityInstanceService service;

    @Override
    public ActivityInstanceSummary handle(Request request, Response response) {
        String studyGuid = request.params(RouteConstants.PathParam.STUDY_GUID);
        String participantGuid = request.params(RouteConstants.PathParam.USER_GUID);
        String instanceGuid = request.params(RouteConstants.PathParam.INSTANCE_GUID);

        DDPAuth ddpAuth = RouteUtil.getDDPAuth(request);
        String operatorGuid = StringUtils.defaultIfBlank(ddpAuth.getOperator(), participantGuid);
        boolean isStudyAdmin = ddpAuth.hasAdminAccessToStudy(studyGuid);
        LanguageDto preferredLangDto = RouteUtil.getUserLanguage(request);

        log.info("Fetching summary for activity instance {} and participant {} in study {} by operator {} (isStudyAdmin={})",
                instanceGuid, participantGuid, studyGuid, operatorGuid, isStudyAdmin);

        return TransactionWrapper.withTxn(handle -> {
            var found = RouteUtil.findUserAndStudyOrHalt(handle, participantGuid, studyGuid);
            User participantUser = found.getUser();
            ActivityInstanceDto instanceDto = RouteUtil.findAccessibleInstanceOrHalt(
                    response, handle, participantUser, found.getStudyDto(), instanceGuid, isStudyAdmin);

            ActivityInstanceSummary summary = service
                    .findTranslatedInstanceSummary(handle, participantGuid, studyGuid,
                            instanceDto.getActivityCode(), instanceGuid, preferredLangDto.getIsoCode())
                    .orElseThrow(() -> new DDPException("Could not find translated summary for activity instance " + instanceGuid));

            List<ActivityInstanceSummary> summaries = List.of(summary);
            Map<String, FormResponse> responses = service.countQuestionsAndAnswers(
                    handle, participantGuid, operatorGuid, studyGuid, summaries);
            service.renderInstanceSummaries(handle, participantUser.getId(), operatorGuid, studyGuid, summaries, responses);

            return summaries.get(0);
        });
    }
}
