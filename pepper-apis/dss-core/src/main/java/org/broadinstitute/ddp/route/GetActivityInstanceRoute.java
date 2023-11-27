package org.broadinstitute.ddp.route;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.LanguageDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.instance.ActivityInstance;
import org.broadinstitute.ddp.model.activity.instance.ConditionalBlock;
import org.broadinstitute.ddp.model.activity.instance.FormBlock;
import org.broadinstitute.ddp.model.activity.instance.FormInstance;
import org.broadinstitute.ddp.model.activity.instance.FormSection;
import org.broadinstitute.ddp.model.activity.instance.GroupBlock;
import org.broadinstitute.ddp.model.activity.instance.QuestionBlock;
import org.broadinstitute.ddp.model.activity.instance.question.Question;
import org.broadinstitute.ddp.model.activity.instance.validation.ActivityValidationFailure;
import org.broadinstitute.ddp.model.activity.types.ActivityType;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.broadinstitute.ddp.security.DDPAuth;
import org.broadinstitute.ddp.service.ActivityInstanceService;
import org.broadinstitute.ddp.service.ActivityValidationService;
import org.broadinstitute.ddp.util.RouteUtil;
import org.jdbi.v3.core.Handle;
import spark.Request;
import spark.Response;
import spark.Route;

@Slf4j
@AllArgsConstructor
public class GetActivityInstanceRoute implements Route {
    private final ActivityInstanceService actInstService;
    private final ActivityValidationService actValidationService;
    private final PexInterpreter interpreter;

    @Override
    public Object handle(Request request, Response response) {
        String userGuid = request.params(PathParam.USER_GUID);
        String studyGuid = request.params(PathParam.STUDY_GUID);
        String instanceGuid = request.params(PathParam.INSTANCE_GUID);

        StopWatch watch = new StopWatch();
        watch.start();

        DDPAuth ddpAuth = RouteUtil.getDDPAuth(request);
        String operatorGuid = StringUtils.defaultIfBlank(ddpAuth.getOperator(), userGuid);
        boolean isStudyAdmin = ddpAuth.hasAdminAccessToStudy(studyGuid);

        log.info("Attempting to retrieve activity instance {} for participant {} in study {} by operator {} (isStudyAdmin={})",
                instanceGuid, userGuid, studyGuid, operatorGuid, isStudyAdmin);

        ActivityInstance result = TransactionWrapper.withTxn(handle -> {

            ActivityInstanceDto instanceDto = RouteUtil.findAccessibleInstanceOrHalt(
                    response, handle, userGuid, studyGuid, instanceGuid, isStudyAdmin);

            ContentStyle style = RouteUtil.parseContentStyleHeaderOrHalt(request, response, ContentStyle.STANDARD);
            log.info("Using ddp content style {} to format activity content", style);

            Optional<EnrollmentStatusType> enrollmentStatus = handle.attach(JdbiUserStudyEnrollment.class)
                    .getEnrollmentStatusByUserAndStudyGuids(userGuid, studyGuid);

            LanguageDto preferredUserLanguage = RouteUtil.getUserLanguage(request);
            String isoLangCode = preferredUserLanguage.getIsoCode();

            log.info("Attempting to find a translation for the following language: {}", isoLangCode);
            Optional<ActivityInstance> inst = getActivityInstance(
                    handle, userGuid, operatorGuid, studyGuid, instanceGuid, style, isoLangCode);

            if (inst.isEmpty()) {
                String errMsg = String.format(
                        "Unable to find activity instance %s of type '%s' in '%s'",
                        instanceGuid,
                        instanceDto.getActivityType(),
                        isoLangCode
                );
                throw new DDPException(errMsg);
            }

            log.info("Found a translation to the '{}' language code for the activity instance with GUID {}",
                    isoLangCode, instanceGuid);
            ActivityInstance activityInstance = inst.get();
            activityInstance.setParentInstanceGuid(instanceDto.getParentInstanceGuid());
            if (activityInstance.getActivityType() == ActivityType.FORMS) {
                actInstService.loadNestedInstanceSummaries(
                        handle, (FormInstance) activityInstance, studyGuid, userGuid, operatorGuid, isoLangCode);
            }
            // To-do: change this to just "if (enrollmentStatus.get() == EnrollmentStatusType.EXITED_BEFORE_ENROLLMENT)) {...}"
            // when every user registered in the system will become enrolled automatically
            // When it is implemented, the check for the enrollment status presence is not needed
            if (enrollmentStatus.isPresent() && enrollmentStatus.get().shouldMarkActivitiesReadOnly()) {
                activityInstance.makeReadonly();
            }
            // end To-do
            Long languageCodeId = preferredUserLanguage.getId();
            return validateActivityInstance(handle, activityInstance, userGuid, operatorGuid, languageCodeId);
        });

        watch.stop();
        log.debug("ActivityInstance reading TOTAL time: " + watch.getTime());

        return result;
    }

    private Optional<ActivityInstance> getActivityInstance(
            Handle handle,
            String userGuid,
            String operatorGuid,
            String studyGuid,
            String instanceGuid,
            ContentStyle style,
            String isoLangCode) {

        StopWatch watch = new StopWatch();
        watch.start();

        Optional<ActivityInstance> inst = actInstService.buildInstanceFromDefinition(
                handle, userGuid, operatorGuid, studyGuid, instanceGuid, style, isoLangCode
        );

        watch.stop();
        log.debug("ActivityInstance reading time: " + watch.getTime());

        return inst;
    }

    private ActivityInstance validateActivityInstance(
            Handle handle, ActivityInstance activityInstance, String userGuid, String operatorGuid, long languageCodeId
    ) {
        List<ActivityValidationFailure> validationFailures = actValidationService.validate(
                handle, interpreter, userGuid, operatorGuid, activityInstance.getGuid(), activityInstance.getCreatedAtMillis(),
                activityInstance.getActivityId(), languageCodeId
        );
        if (validationFailures.isEmpty()) {
            return activityInstance;
        }
        String msg = "Activity validation failed";
        List<String> validationErrorSummaries = validationFailures
                .stream().map(ActivityValidationFailure::getErrorMessage).collect(Collectors.toList());
        log.info(msg + ", reasons: {}", validationErrorSummaries);
        return enrichFormQuestionsWithActivityValidationFailures(
                (FormInstance) activityInstance,
                validationFailures
        );
    }

    private FormInstance enrichFormQuestionsWithActivityValidationFailures(
            FormInstance form,
            List<ActivityValidationFailure> failures
    ) {
        Map<String, List<ActivityValidationFailure>> failuresByQuestionStableId = mapValidationFailuresToQuestionStableId(failures);
        List<Question> allQuestions = new ArrayList<>();
        for (FormSection formSection : form.getAllSections()) {
            for (FormBlock formBlock : formSection.getBlocks()) {
                // Step inside the group block and get questions from all nested question blocks
                // Link each nested question to the grouping block
                if (formBlock.getBlockType() == BlockType.GROUP) {
                    GroupBlock groupBlock = (GroupBlock) formBlock;
                    List<FormBlock> nestedBlocks = groupBlock.getNested();
                    for (FormBlock nestedBlock : nestedBlocks) {
                        if (nestedBlock.getBlockType() == BlockType.QUESTION) {
                            QuestionBlock questionBlock = (QuestionBlock) nestedBlock;
                            allQuestions.add(questionBlock.getQuestion());
                        }
                    }
                } else if (formBlock.getBlockType() == BlockType.CONDITIONAL) {
                    ConditionalBlock conditionalBlock = (ConditionalBlock) formBlock;
                    Question controlQuestion = conditionalBlock.getControl();
                    // Adding a control question itself
                    allQuestions.add(controlQuestion);
                    // Adding nested questions and linking them to the control question
                    for (FormBlock nestedBlock : conditionalBlock.getNested()) {
                        if (nestedBlock.getBlockType() == BlockType.QUESTION) {
                            QuestionBlock questionBlock = (QuestionBlock) nestedBlock;
                            allQuestions.add(questionBlock.getQuestion());
                        }
                    }
                } else if (formBlock.getBlockType() == BlockType.TABULAR) {
                    allQuestions.addAll(formBlock.streamQuestions().collect(Collectors.toList()));
                } else if (formBlock.getBlockType() == BlockType.QUESTION) {
                    QuestionBlock questionBlock = (QuestionBlock) formBlock;
                    allQuestions.add(questionBlock.getQuestion());
                }
            }
        }
        allQuestions.forEach(
                question -> {
                    String stableId = question.getStableId();
                    if (failuresByQuestionStableId.containsKey(stableId)) {
                        question.setActivityValidationFailures(
                                failuresByQuestionStableId.get(question.getStableId())
                        );
                    }
                }
        );
        return form;
    }

    private Map<String, List<ActivityValidationFailure>> mapValidationFailuresToQuestionStableId(List<ActivityValidationFailure> failures) {
        Map<String, List<ActivityValidationFailure>> failuresByQuestionStableId = new HashMap<>();
        for (ActivityValidationFailure failure : failures) {
            for (String questionStableId : failure.getAffectedQuestionStableIds()) {
                List<ActivityValidationFailure> failuresForQuestion = failuresByQuestionStableId.get(questionStableId);
                if (failuresForQuestion == null) {
                    failuresForQuestion = new ArrayList<>();
                    failuresByQuestionStableId.put(questionStableId, failuresForQuestion);
                }
                failuresForQuestion.add(failure);
            }
        }
        return failuresByQuestionStableId;
    }
}
