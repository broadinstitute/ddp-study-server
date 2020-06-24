package org.broadinstitute.ddp.route;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.brsanthu.googleanalytics.request.EventHit;
import org.broadinstitute.ddp.analytics.GoogleAnalyticsMetrics;
import org.broadinstitute.ddp.analytics.GoogleAnalyticsMetricsTracker;
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
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.model.study.StudySettings;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.broadinstitute.ddp.security.DDPAuth;
import org.broadinstitute.ddp.service.ActivityInstanceService;
import org.broadinstitute.ddp.service.ActivityValidationService;
import org.broadinstitute.ddp.util.RouteUtil;

import org.jdbi.v3.core.Handle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spark.Request;
import spark.Response;
import spark.Route;

public class GetActivityInstanceRoute implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(GetActivityInstanceRoute.class);
    private static final String DEFAULT_ISO_LANGUAGE_CODE = "en";

    private ActivityInstanceService actInstService;
    private ActivityValidationService actValidationService;
    private PexInterpreter interpreter;

    public GetActivityInstanceRoute(
            ActivityInstanceService actInstService,
            ActivityValidationService actValidationService,
            PexInterpreter interpreter
    ) {
        this.actInstService = actInstService;
        this.actValidationService = actValidationService;
        this.interpreter = interpreter;
    }

    @Override
    public Object handle(Request request, Response response) {
        String userGuid = request.params(PathParam.USER_GUID);
        String studyGuid = request.params(PathParam.STUDY_GUID);
        String instanceGuid = request.params(PathParam.INSTANCE_GUID);
        DDPAuth ddpAuth = RouteUtil.getDDPAuth(request);

        LOG.info("Attempting to retrieve activity instance {} for participant {} in study {}", instanceGuid, userGuid, studyGuid);

        ActivityInstance result = TransactionWrapper.withTxn(handle -> {
            ActivityInstanceDto instanceDto = RouteUtil.findAccessibleInstanceOrHalt(
                    response, handle, userGuid, studyGuid, instanceGuid);

            ContentStyle style = RouteUtil.parseContentStyleHeaderOrHalt(request, response, ContentStyle.STANDARD);
            LOG.info("Using ddp content style {} to format activity content", style);

            Optional<EnrollmentStatusType> enrollmentStatus = handle.attach(JdbiUserStudyEnrollment.class)
                    .getEnrollmentStatusByUserAndStudyGuids(userGuid, studyGuid);

            LanguageDto preferredUserLanguage = RouteUtil.getUserLanguage(request);
            String isoLangCode = preferredUserLanguage.getIsoCode();

            LOG.info("Attempting to find a translation for the following language: {}", isoLangCode);
            Optional<ActivityInstance> inst = actInstService.getTranslatedActivity(
                    handle, userGuid, instanceDto.getActivityType(), instanceGuid, isoLangCode, style
            );
            if (!inst.isPresent()) {
                String errMsg = String.format(
                        "Unable to find activity instance %s of type '%s' in '%s'",
                        instanceGuid,
                        instanceDto.getActivityType(),
                        isoLangCode
                );
                throw new DDPException(errMsg);
            }

            LOG.info("Found a translation to the '{}' language code for the activity instance with GUID {}",
                    isoLangCode, instanceGuid);
            ActivityInstance activityInstance = inst.get();
            // To-do: change this to just "if (enrollmentStatus.get() == EnrollmentStatusType.EXITED_BEFORE_ENROLLMENT)) {...}"
            // when every user registered in the system will become enrolled automatically
            // When it is implemented, the check for the enrollment status presence is not needed
            if (enrollmentStatus.isPresent() && enrollmentStatus.get().shouldMarkActivitiesReadOnly()) {
                activityInstance.makeReadonly();
            }
            // end To-do
            Long languageCodeId = preferredUserLanguage.getId();
            return validateActivityInstance(handle, activityInstance, userGuid, languageCodeId);
        });

        StudySettings studySettings = GoogleAnalyticsMetricsTracker.getStudySettingByStudyGuid(studyGuid);
        if (studySettings != null && studySettings.isAnalyticsEnabled()) {
            String gaEventLabel = studyGuid.join(":", GoogleAnalyticsMetrics.EVENT_LABEL_ACTIVITY_INSTANCE);
            EventHit eventHit = new EventHit(GoogleAnalyticsMetrics.EVENT_CATEGORY_ACTIVITY_INSTANCE,
                    GoogleAnalyticsMetrics.EVENT_ACTION_ACTIVITY_INSTANCE, gaEventLabel, 1);
            GoogleAnalyticsMetricsTracker.sendEventMetrics(studyGuid, eventHit);
        }

        return result;
    }

    private ActivityInstance validateActivityInstance(
            Handle handle, ActivityInstance activityInstance, String userGuid, long languageCodeId
    ) {
        List<ActivityValidationFailure> validationFailures = actValidationService.validate(
                handle, interpreter, userGuid, activityInstance.getGuid(), activityInstance.getActivityId(), languageCodeId
        );
        if (validationFailures.isEmpty()) {
            return activityInstance;
        }
        String msg = "Activity validation failed";
        List<String> validationErrorSummaries = validationFailures
                .stream().map(failure -> failure.getErrorMessage()).collect(Collectors.toList());
        LOG.info(msg + ", reasons: {}", validationErrorSummaries);
        ActivityInstance enrichedInstance = enrichFormQuestionsWithActivityValidationFailures(
                (FormInstance) activityInstance,
                validationFailures
        );
        return enrichedInstance;
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
