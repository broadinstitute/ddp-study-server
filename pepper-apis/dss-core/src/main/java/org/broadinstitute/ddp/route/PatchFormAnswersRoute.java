package org.broadinstitute.ddp.route;

import static java.util.stream.Collectors.toList;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.db.ActivityDefStore;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityInstanceStatusDao;
import org.broadinstitute.ddp.db.dao.AnswerCachedDao;
import org.broadinstitute.ddp.db.dao.DataExportDao;
import org.broadinstitute.ddp.db.dao.FileUploadDao;
import org.broadinstitute.ddp.db.dao.JdbiMatrixGroup;
import org.broadinstitute.ddp.db.dao.JdbiMatrixOption;
import org.broadinstitute.ddp.db.dao.JdbiQuestionCached;
import org.broadinstitute.ddp.db.dao.QuestionCachedDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.AnswerDto;
import org.broadinstitute.ddp.db.dto.CompositeQuestionDto;
import org.broadinstitute.ddp.db.dto.LanguageDto;
import org.broadinstitute.ddp.db.dto.MatrixGroupDto;
import org.broadinstitute.ddp.db.dto.NumericQuestionDto;
import org.broadinstitute.ddp.db.dto.DecimalQuestionDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.UserActivityInstanceSummary;
import org.broadinstitute.ddp.equation.QuestionEvaluator;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.exception.OperationNotAllowedException;
import org.broadinstitute.ddp.exception.RequiredParameterMissingException;
import org.broadinstitute.ddp.exception.UnexpectedNumberOfElementsException;
import org.broadinstitute.ddp.json.AnswerResponse;
import org.broadinstitute.ddp.json.AnswerSubmission;
import org.broadinstitute.ddp.json.PatchAnswerPayload;
import org.broadinstitute.ddp.json.PatchAnswerResponse;
import org.broadinstitute.ddp.json.errors.AnswerValidationError;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.definition.types.DecimalDef;
import org.broadinstitute.ddp.model.activity.instance.answer.ActivityInstanceSelectAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.AgreementAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.CompositeAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.broadinstitute.ddp.model.activity.instance.answer.FileAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.AnswerRow;
import org.broadinstitute.ddp.model.activity.instance.answer.FileInfo;
import org.broadinstitute.ddp.model.activity.instance.answer.NumericAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DecimalAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedPicklistOption;
import org.broadinstitute.ddp.model.activity.instance.answer.MatrixAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedMatrixCell;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.CompositeQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.Question;
import org.broadinstitute.ddp.model.activity.instance.question.TextQuestion;
import org.broadinstitute.ddp.model.activity.instance.validation.ActivityValidationFailure;
import org.broadinstitute.ddp.model.activity.instance.validation.Rule;
import org.broadinstitute.ddp.model.activity.types.ActivityType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.broadinstitute.ddp.security.DDPAuth;
import org.broadinstitute.ddp.service.ActivityValidationService;
import org.broadinstitute.ddp.service.FileUploadService;
import org.broadinstitute.ddp.service.FormActivityService;
import org.broadinstitute.ddp.util.ActivityInstanceUtil;
import org.broadinstitute.ddp.util.GsonPojoValidator;
import org.broadinstitute.ddp.util.JsonValidationError;
import org.broadinstitute.ddp.util.MiscUtil;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.RouteUtil;
import org.jdbi.v3.core.Handle;
import spark.Request;
import spark.Response;
import spark.Route;

@Slf4j
@AllArgsConstructor
public class PatchFormAnswersRoute implements Route {
    private final Gson gson = new Gson();
    private final GsonPojoValidator checker = new GsonPojoValidator();
    private final FormActivityService formService;
    private final ActivityValidationService actValidationService;
    private final FileUploadService fileService;
    private final PexInterpreter interpreter;

    @Override
    public Object handle(Request request, Response response) {
        long startTime = System.currentTimeMillis();
        String participantGuid = request.params(PathParam.USER_GUID);
        String studyGuid = request.params(PathParam.STUDY_GUID);
        String instanceGuid = request.params(PathParam.INSTANCE_GUID);

        DDPAuth ddpAuth = RouteUtil.getDDPAuth(request);
        String operatorGuid = StringUtils.defaultIfBlank(ddpAuth.getOperator(), participantGuid);
        boolean isStudyAdmin = ddpAuth.hasAdminAccessToStudy(studyGuid);

        log.info("Attempting to patch answers for activity instance {} for participant {} in study {} by operator {} (isStudyAdmin={})",
                instanceGuid, participantGuid, studyGuid, operatorGuid, isStudyAdmin);

        PatchAnswerResponse result = TransactionWrapper.withTxn(handle -> {
            UserActivityInstanceSummary instanceSummary = RouteUtil.findUserActivityInstanceSummaryOrHalt(
                    response, handle, participantGuid, studyGuid, instanceGuid, isStudyAdmin);
            ActivityInstanceDto instanceDto = instanceSummary.getActivityInstanceByGuid(instanceGuid).get();

            if (!ActivityType.FORMS.equals(instanceDto.getActivityType())) {
                String msg = "Activity " + instanceGuid + " is not a form activity that accepts answers";
                log.info(msg);
                throw ResponseUtil.haltError(response, 400, new ApiError(ErrorCodes.INVALID_REQUEST, msg));
            }

            PatchAnswerPayload payload = parseBodyPayload(request);
            if (payload == null) {
                String msg = "Unable to process request body";
                throw ResponseUtil.haltError(response, 400, new ApiError(ErrorCodes.BAD_PAYLOAD, msg));
            }

            PatchAnswerResponse res = new PatchAnswerResponse();
            List<AnswerSubmission> submissions = payload.getSubmissions();
            if (submissions == null || submissions.isEmpty()) {
                log.info("No answer submissions to process");
                return res;
            }

            ActivityDefStore activityStore = ActivityDefStore.getInstance();
            FormActivityDef activityDef = ActivityInstanceUtil.getActivityDef(handle, activityStore, instanceDto, studyGuid);

            ActivityInstanceDto parentInstanceDto = null;
            FormActivityDef parentActivityDef = null;
            String parentInstanceGuid = instanceDto.getParentInstanceGuid();
            if (parentInstanceGuid != null) {
                parentInstanceDto = instanceSummary
                        .getActivityInstanceByGuid(parentInstanceGuid)
                        .orElseThrow(() -> new DDPException("Could not find parent instance " + parentInstanceGuid));
                parentActivityDef = ActivityInstanceUtil.getActivityDef(handle, activityStore, parentInstanceDto, studyGuid);
            }

            if (!isStudyAdmin) {
                if (parentInstanceDto != null && ActivityInstanceUtil.isInstanceReadOnly(parentActivityDef, parentInstanceDto)) {
                    String msg = "Parent activity instance with GUID " + parentInstanceDto.getGuid()
                            + " is read-only, cannot submit answer(s) for child instance " + instanceGuid;
                    log.info(msg);
                    throw ResponseUtil.haltError(response, 422, new ApiError(ErrorCodes.ACTIVITY_INSTANCE_IS_READONLY, msg));
                }
                if (ActivityInstanceUtil.isInstanceReadOnly(activityDef, instanceDto)) {
                    String msg = "Activity instance with GUID " + instanceGuid + " is read-only, cannot submit answer(s) for it";
                    log.info(msg);
                    throw ResponseUtil.haltError(response, 422, new ApiError(ErrorCodes.ACTIVITY_INSTANCE_IS_READONLY, msg));
                }
            }

            var answerDao = new AnswerCachedDao(handle);
            var questionDao = new QuestionCachedDao(handle);
            var jdbiQuestion = questionDao.getJdbiQuestion();

            LanguageDto preferredUserLangDto = RouteUtil.getUserLanguage(request);

            User operatorUser = instanceSummary.getParticipantUser();
            if (!participantGuid.equals(operatorGuid)) {
                operatorUser = handle.attach(UserDao.class).findUserByGuid(operatorGuid)
                        .orElseThrow(() -> new DDPException("Could not find operator with guid " + operatorGuid));
            }

            try {
                Map<String, List<Rule>> failedRulesByQuestion = new HashMap<>();
                for (AnswerSubmission submission : submissions) {
                    String questionStableId = extractQuestionStableId(submission, response);
                    QuestionDef questionDef = activityDef.getQuestionByStableId(questionStableId);

                    if (questionDef == null) {
                        log.warn("Could not find questiondef with id: " + questionStableId);
                    }

                    Optional<QuestionDto> optDto = jdbiQuestion.findDtoByStableIdAndInstanceGuid(questionStableId, instanceGuid);
                    QuestionDto questionDto = extractQuestionDto(response, questionStableId, optDto);
                    // @TODO might be able to get rid of this query and a bunch of *CachedDao classes
                    // if can figure out how to create Rule objects from RuleDef
                    Question question = questionDao.getQuestionByActivityInstanceAndDto(questionDto,
                            instanceGuid, instanceDto.getCreatedAtMillis(), false, preferredUserLangDto.getId());

                    if (!isStudyAdmin && question.isReadonly()) {
                        String msg = "Question with stable id " + questionStableId + " is read-only, cannot update question";
                        log.info(msg);
                        throw ResponseUtil.haltError(response, 422, new ApiError(ErrorCodes.QUESTION_IS_READONLY, msg));
                    }

                    //validation to check if question is a composite child
                    Optional<Long> parentQuestionId = jdbiQuestion
                            .findCompositeParentIdByChildId(question.getQuestionId());
                    if (parentQuestionId.isPresent()) {
                        log.warn("Passed question stable ID : " + questionStableId + " is a Composite child question. "
                                + "Only entire Composite question answer can be updated ");
                        throw ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND, new ApiError(
                                ErrorCodes.QUESTION_NOT_FOUND, "Only entire Composite question answer can be updated"));
                    }

                    Answer answer = convertAnswer(handle, response, instanceGuid, questionStableId,
                            submission.getAnswerGuid(), studyGuid, participantGuid, questionDto, submission.getValue());
                    if (answer == null) {
                        String msg = "Answer value does not have expected format for question stable id " + questionStableId;
                        log.info(msg);
                        throw ResponseUtil.haltError(response, 400, new ApiError(ErrorCodes.BAD_PAYLOAD, msg));
                    }
                    // does this very-specific check need to be here?
                    if (questionDef.getQuestionType() == QuestionType.TEXT
                            && ((TextQuestion) question).getInputType() == TextInputType.EMAIL
                            && answer.getValue() != null
                    ) {
                        String value = (String) answer.getValue();
                        if (StringUtils.isNotBlank(value) && !MiscUtil.isEmailFormatValid(value)) {
                            throw ResponseUtil.haltError(response, 422, new ApiError(ErrorCodes.MALFORMED_EMAIL, "Invalid email"));
                        }
                    }

                    // Run constraint checks before processing validation rules.
                    checkAnswerConstraints(response, answer);
                    if (answer.getQuestionType() == QuestionType.FILE && answer.getValue() != null) {
                        verifyFileUpload(handle, response, instanceDto, (FileAnswer) answer);
                    }

                    List<Rule> failures = validateAnswer(answer, instanceGuid, question);
                    if (!failures.isEmpty()) {
                        List<Rule> failedRules = failedRulesByQuestion.get(questionStableId);
                        if (failedRules == null) {
                            failedRules = new ArrayList<>();
                            failedRulesByQuestion.put(questionStableId, failedRules);
                        }
                        failedRules.addAll(failures);
                    } else {
                        // Attempt to figure out the answer to update if one exists.
                        Long answerId = null;
                        String answerGuid = answer.getAnswerGuid();
                        if (answerGuid == null) {
                            List<AnswerDto> answerDtos = answerDao.getAnswerSql().findDtosByInstanceGuidAndQuestionId(instanceGuid,
                                    questionDto.getId());
                            if (answerDtos.size() > 1) {
                                String errMsg = String.format(
                                        "Question %s is expected to have 1 answer but found %d answers instead",
                                        questionStableId,
                                        answerDtos.size()
                                );
                                log.error(errMsg);
                                throw ResponseUtil.haltError(response, 500, new ApiError(ErrorCodes.SERVER_ERROR, errMsg));
                            } else if (answerDtos.size() == 1) {
                                AnswerDto answerDto = answerDtos.get(0);
                                answerId = answerDto.getId();
                                answerGuid = answerDto.getGuid();
                            }
                        } else {
                            AnswerDto answerDto = answerDao.getAnswerSql().findDtoByGuid(answer.getAnswerGuid()).orElse(null);
                            if (answerDto == null || answerDto.getActivityInstanceId() != instanceDto.getId()) {
                                // Halt if provided answer guid is not found or it doesn't belong to instance
                                String msg = "Answer with guid " + answer.getAnswerGuid() + " is not found";
                                throw ResponseUtil.haltError(response, 404, new ApiError(ErrorCodes.ANSWER_NOT_FOUND, msg));
                            } else {
                                answerId = answerDto.getId();
                                answerGuid = answerDto.getGuid();
                            }
                        }

                        if (answerId == null) {
                            // Did not provide answer guid and no answer exist yet so create one
                            answerGuid = answerDao.createAnswer(operatorUser.getId(), instanceDto.getId(), answer, questionDef)
                                    .getAnswerGuid();
                            log.info("Created answer with guid {} for question stable id {}", answerGuid, questionStableId);
                        } else {
                            answerDao.updateAnswer(operatorUser.getId(), answerId, answer, questionDef);
                            log.info("Updated answer with guid {} for question stable id {}", answerGuid, questionStableId);
                        }

                        res.addAnswer(new AnswerResponse(questionStableId, answerGuid));
                    }
                }
                if (!failedRulesByQuestion.isEmpty()) {
                    String msg = "One or more answer submission(s) failed validation for their question(s)";
                    log.info(msg);
                    throw ResponseUtil.haltError(response, 422, new AnswerValidationError(msg, failedRulesByQuestion));
                }
            } catch (NoSuchElementException e) {
                log.warn(e.getMessage());
                throw ResponseUtil.haltError(response, 400, new ApiError(ErrorCodes.NO_SUCH_ELEMENT, e.getMessage()));
            } catch (UnexpectedNumberOfElementsException e) {
                log.warn(e.getMessage());
                throw ResponseUtil.haltError(response, 400, new ApiError(ErrorCodes.UNEXPECTED_NUMBER_OF_ELEMENTS, e.getMessage()));
            } catch (OperationNotAllowedException e) {
                log.warn(e.getMessage());
                throw ResponseUtil.haltError(response, 422, new ApiError(ErrorCodes.OPERATION_NOT_ALLOWED, e.getMessage()));
            } catch (RequiredParameterMissingException e) {
                log.warn(e.getMessage());
                throw ResponseUtil.haltError(response, 400, new ApiError(ErrorCodes.REQUIRED_PARAMETER_MISSING, e.getMessage()));
            }

            enrichWithEquations(handle, instanceGuid, res);

            res.setBlockVisibilities(formService.getBlockVisibilitiesAndEnabled(handle, instanceSummary, activityDef, participantGuid,
                    operatorGuid, instanceGuid));

            List<ActivityValidationFailure> failures = getActivityValidationFailures(
                    handle, participantGuid, operatorGuid, instanceDto, preferredUserLangDto.getId()
            );
            if (!failures.isEmpty()) {
                log.info("Activity validation failed, reasons: {}", createValidationFailureSummaries(failures));
                return enrichPayloadWithValidationFailures(res, failures);
            }

            var instanceStatusDao = handle.attach(ActivityInstanceStatusDao.class);
            instanceStatusDao.updateOrInsertStatus(instanceDto, InstanceStatusType.IN_PROGRESS,
                    Instant.now().toEpochMilli(), operatorUser, instanceSummary.getParticipantUser());
            if (parentInstanceDto != null) {
                instanceStatusDao.updateOrInsertStatus(parentInstanceDto, InstanceStatusType.IN_PROGRESS,
                        Instant.now().toEpochMilli(), operatorUser, instanceSummary.getParticipantUser());
            }
            handle.attach(DataExportDao.class).queueDataSync(participantGuid, studyGuid);

            return res;
        });

        response.status(200);
        log.info("Processing PatchFormAnswersRoute took: {} ms", System.currentTimeMillis() - startTime);
        return result;
    }

    private void enrichWithEquations(final Handle handle, final String instanceGuid, final PatchAnswerResponse response) {
        var questionEvaluator = new QuestionEvaluator(handle, instanceGuid);

        new QuestionCachedDao(handle).getJdbiEquationQuestion().findEquationsByActivityInstanceGuid(instanceGuid)
                .stream()
                .map(questionEvaluator::evaluate)
                .filter(Objects::nonNull)
                .forEach(response::addEquation);
    }

    private QuestionDto extractQuestionDto(Response response, String questionStableId, Optional<QuestionDto> optQuestionDto) {
        if (optQuestionDto.isEmpty()) {
            String msg = "Question with stable id " + questionStableId + " is not found in form activity";
            throw ResponseUtil.haltError(response, 404, new ApiError(ErrorCodes.QUESTION_NOT_FOUND, msg));
        }
        return optQuestionDto.get();
    }

    private String extractQuestionStableId(AnswerSubmission submission, Response response) {
        String stableId = submission.getQuestionStableId();
        if (StringUtils.isBlank(stableId)) {
            String msg = "An answer submission is missing a question stable id";
            log.info(msg);
            throw ResponseUtil.haltError(response, 400, new ApiError(ErrorCodes.BAD_PAYLOAD, msg));
        }
        return stableId;
    }

    /**
     * Parse the JSON payload.
     *
     * @param request the incoming request
     * @return payload object, or null if failed parsing
     */
    private PatchAnswerPayload parseBodyPayload(Request request) {
        PatchAnswerPayload payload = null;
        try {
            JsonElement json = gson.fromJson(request.body(), JsonElement.class);
            if (json != null && json.isJsonObject() && json.getAsJsonObject()
                    .has(PatchAnswerPayload.ANSWERS_LIST_KEY)) {
                JsonElement answersList = json.getAsJsonObject().get(PatchAnswerPayload.ANSWERS_LIST_KEY);
                if (answersList != null && answersList.isJsonArray()) {
                    payload = gson.fromJson(json, PatchAnswerPayload.class);
                }
            }
        } catch (JsonSyntaxException e) {
            log.info("Unable to parse request payload: " + e.getMessage());
        }
        return payload;
    }

    /**
     * Convert given data to an answer object.
     *
     * @param handle       the jdbi handle
     * @param instanceGuid the activity instance guid
     * @param stableId     the question stable id
     * @param guid         the answer guid, or null
     * @param value        the answer value
     * @return answer object, or null if no answer value given
     */
    private Answer convertAnswer(Handle handle, Response response, String instanceGuid, String stableId, String guid,
                                 String studyGuid, String userGuid, QuestionDto questionDto, JsonElement value) {
        switch (questionDto.getType()) {
            case BOOLEAN:
                return convertBoolAnswer(stableId, guid, instanceGuid, value);
            case PICKLIST:
                return convertPicklistAnswer(stableId, guid, instanceGuid, value);
            case MATRIX:
                return convertMatrixAnswer(handle, stableId, guid, instanceGuid, value);
            case TEXT:
                return convertTextAnswer(stableId, guid, instanceGuid, value);
            case ACTIVITY_INSTANCE_SELECT:
                return convertActivityInstanceSelectAnswer(handle, response, studyGuid, userGuid, questionDto, guid, instanceGuid, value);
            case DATE:
                return convertDateAnswer(stableId, guid, instanceGuid, value);
            case FILE:
                return convertFileAnswer(handle, response, stableId, guid, instanceGuid, value);
            case NUMERIC:
                return convertNumericAnswer(handle, (NumericQuestionDto) questionDto, guid, instanceGuid, value);
            case DECIMAL:
                return convertDecimalAnswer(handle, (DecimalQuestionDto) questionDto, guid, instanceGuid, value);
            case AGREEMENT:
                return convertAgreementAnswer(stableId, guid, instanceGuid, value);
            case COMPOSITE:
                return convertCompositeAnswer(handle, response, instanceGuid, studyGuid, userGuid,
                        (CompositeQuestionDto) questionDto, guid, value);
            default:
                throw new RuntimeException("Unhandled question type " + questionDto.getType());
        }
    }

    /**
     * Convert given data to boolean answer.
     *
     * @param stableId the question stable id
     * @param guid     the answer guid, or null
     * @param value    the answer value
     * @return boolean answer object, or null if value is not boolean
     */
    private BoolAnswer convertBoolAnswer(String stableId, String guid, String activityInstanceGuid, JsonElement value) {
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
            boolean boolValue = value.getAsJsonPrimitive().getAsBoolean();
            return new BoolAnswer(null, stableId, guid, boolValue, activityInstanceGuid);
        } else {
            return null;
        }
    }

    /**
     * Convert given data to picklist answer.
     *
     * @param stableId the question stable id
     * @param guid     the answer guid, or null
     * @param value    the answer value
     * @return picklist answer object, or null if value is not a list of options
     */
    private PicklistAnswer convertPicklistAnswer(String stableId, String guid, String actInstanceGuid, JsonElement value) {
        if (value == null || !value.isJsonArray()) {
            return null;
        }
        try {
            Type selectedOptionListType = new TypeToken<ArrayList<SelectedPicklistOption>>() {
            }.getType();
            List<SelectedPicklistOption> selected = gson.fromJson(value, selectedOptionListType);
            return new PicklistAnswer(null, stableId, guid, selected, actInstanceGuid);
        } catch (JsonSyntaxException e) {
            log.warn("Failed to convert submitted answer to a picklist answer", e);
            return null;
        }
    }

    /**
     * Convert given data to matrix answer.
     *
     *
     * @param handle    the database handle
     * @param stableId the question stable id
     * @param guid     the answer guid, or null
     * @param value    the answer value
     * @return matrix answer object, or null if value is not a list of options
     */
    private MatrixAnswer convertMatrixAnswer(Handle handle, String stableId, String guid, String actInstanceGuid,
                                             JsonElement value) {
        if (value == null || !value.isJsonArray()) {
            return null;
        }
        try {
            Type selectedOptionListType = new TypeToken<ArrayList<SelectedMatrixCell>>() {
            }.getType();
            List<SelectedMatrixCell> selected = gson.fromJson(value, selectedOptionListType);
            List<String> optionStableIds = selected.stream().map(SelectedMatrixCell::getOptionStableId).collect(Collectors.toList());
            var jdbiQuestion = new JdbiQuestionCached(handle);
            var questionId = jdbiQuestion.findIdByStableIdAndInstanceGuid(stableId, actInstanceGuid).orElseThrow();
            Map<String, Long> optionStableIdToGroupId = new HashMap<>();
            Map<Long, MatrixGroupDto> selectedGroupMap = new HashMap<>();
            handle.attach(JdbiMatrixOption.class)
                    .findOptions(questionId, optionStableIds, actInstanceGuid)
                    .forEach(dto -> optionStableIdToGroupId.put(dto.getStableId(), dto.getGroupId()));
            List<Long> groupIds = new ArrayList<>(optionStableIdToGroupId.values());
            handle.attach(JdbiMatrixGroup.class).findGroupsByIds(questionId, groupIds, actInstanceGuid).forEach(g ->
                    selectedGroupMap.put(g.getId(), g));
            selected.forEach(s -> {
                Long groupId = optionStableIdToGroupId.get(s.getOptionStableId());
                if (groupId != null) {
                    s.setGroupStableId(selectedGroupMap.get(groupId).getStableId());
                }
            });
            return new MatrixAnswer(null, stableId, guid, selected, actInstanceGuid);
        } catch (JsonSyntaxException e) {
            log.warn("Failed to convert submitted answer to a matrix answer", e);
            return null;
        }
    }

    /**
     * Converts the text answer.
     *
     * @param stableId the question stable id
     * @param guid     the answer guid, or null
     * @param value    the answer value
     * @return text answer object, or null if value is not a string
     */
    private TextAnswer convertTextAnswer(String stableId, String guid, String actInstanceGuid, JsonElement value) {
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String textValue = value.getAsJsonPrimitive().getAsString();
            return new TextAnswer(null, stableId, guid, textValue, actInstanceGuid);
        } else {
            return null;
        }
    }

    /**
     * Converts the activity instance select answer.
     *
     * @param handle   the database handle
     * @param questionDto the question dto object
     * @param guid     the answer guid, or null
     * @param value    the answer value
     * @return activity instance select answer object, or null if value is not a string
     */
    private ActivityInstanceSelectAnswer convertActivityInstanceSelectAnswer(Handle handle, Response response, String studyGuid,
                                                                             String userGuid, QuestionDto questionDto,
                                                                             String guid, String actInstanceGuid, JsonElement value) {
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String textValue = value.getAsJsonPrimitive().getAsString();
            var jdbiQuestion = new JdbiQuestionCached(handle);
            Set<String> activityCodes = new HashSet<>(jdbiQuestion
                    .getActivityCodesByActivityInstanceSelectQuestionId(questionDto.getId()));
            var instanceSummaries = handle.attach(org.broadinstitute.ddp.db.dao.ActivityInstanceDao.class)
                    .findSortedInstanceSummaries(userGuid, studyGuid, activityCodes);
            if (instanceSummaries.stream().noneMatch(summary -> summary.getGuid().equals(textValue))) {
                throw ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND, new ApiError(ErrorCodes.NOT_FOUND,
                        "Could not find activity instance with guid " + textValue));
            }
            return new ActivityInstanceSelectAnswer(null, questionDto.getStableId(), guid, textValue, actInstanceGuid);
        } else {
            return null;
        }
    }

    /**
     * Converts given data to date answer.
     *
     * @param stableId the question stable id
     * @param guid     the answer guid, or null
     * @param value    the answer value
     * @return date answer object, or null if value is not as expected
     */
    private DateAnswer convertDateAnswer(String stableId, String guid, String actInstanceGuid, JsonElement value) {
        if (value != null && value.isJsonObject()) {
            try {
                DateValue dateValue = gson.fromJson(value, DateValue.class);
                return new DateAnswer(null, stableId, guid, dateValue, actInstanceGuid);
            } catch (JsonSyntaxException e) {
                log.warn("Failed to convert submitted answer to a date answer", e);
                return null;
            }
        } else {
            log.info("Provided answer value for question stable id {} and with "
                    + "answer guid {} is not an object", stableId, guid);
            return null;
        }
    }

    private FileAnswer convertFileAnswer(Handle handle, Response response, String stableId, String guid,
                                         String instanceGuid, JsonElement value) {
        boolean isNull = (value == null || value.isJsonNull());
        if (isNull || value.isJsonArray()) {
            List<FileInfo> fileInfos = new ArrayList<>();
            if (!isNull) {
                FileUploadDao fileUploadDao = handle.attach(FileUploadDao.class);
                for (JsonElement element : value.getAsJsonArray()) {
                    FileInfo info = fileUploadDao.findFileInfoByGuid(element.getAsString()).orElse(null);
                    if (info == null) {
                        throw ResponseUtil.haltError(response, 400, new ApiError(ErrorCodes.FILE_ERROR,
                                "Could not find file upload with guid " + element.getAsString()));
                    }
                    fileInfos.add(info);
                }

            }
            return new FileAnswer(null, stableId, guid, fileInfos, instanceGuid);
        } else {
            return null;
        }
    }

    private NumericAnswer convertNumericAnswer(Handle handle, NumericQuestionDto numericDto, String guid, String actInstanceGuid,
                                               JsonElement value) {
        if (value == null || value.isJsonNull() || (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber())) {
            Long intValue = null;
            if (value != null && !value.isJsonNull()) {
                intValue = value.getAsLong();
            }
            return new NumericAnswer(null, numericDto.getStableId(), guid, intValue, actInstanceGuid);
        } else {
            return null;
        }
    }

    private DecimalAnswer convertDecimalAnswer(Handle handle, DecimalQuestionDto numericDto, String guid, String actInstanceGuid,
                                               JsonElement value) {
        DecimalDef decimalValue = null;
        if (value != null && !value.isJsonNull() && value.isJsonObject()) {
            final JsonObject jsonObject = value.getAsJsonObject();
            if (jsonObject.has("value") && jsonObject.has("scale")) {
                decimalValue = new DecimalDef(jsonObject.get("value").getAsBigInteger(), jsonObject.get("scale").getAsInt());
            }
        }
        return new DecimalAnswer(null, numericDto.getStableId(), guid, decimalValue, actInstanceGuid);
    }

    private CompositeAnswer convertCompositeAnswer(Handle handle, Response response, String instanceGuid,
                                                   String studyGuid, String userGuid, CompositeQuestionDto compositeDto,
                                                   String answerGuid, JsonElement value) {
        String parentStableId = compositeDto.getStableId();
        final Consumer<String> haltError = (String msg) -> {
            log.info(msg);
            throw ResponseUtil.haltError(response, HttpStatus.SC_BAD_REQUEST, new ApiError(ErrorCodes.BAD_PAYLOAD, msg));
        };

        if (value != null && value.isJsonArray()) {
            var compAnswer = new CompositeAnswer(null, parentStableId, answerGuid, instanceGuid);
            var jdbiQuestion = new JdbiQuestionCached(handle);
            JsonArray childAnswersJsonArray = value.getAsJsonArray();
            if (!compositeDto.isAllowMultiple() && childAnswersJsonArray.size() > 1) {
                haltError.accept("Answers to composite question with stable id: " + parentStableId
                        + " are restricted to only one row");
            }

            List<Long> childIds = jdbiQuestion.findCompositeChildIdsByParentIdAndInstanceGuid(compositeDto.getId(), instanceGuid);
            Map<String, QuestionDto> childDtos;
            try (var stream = jdbiQuestion.findQuestionDtosByIds(Set.copyOf(childIds))) {
                childDtos = stream.collect(Collectors.toMap(QuestionDto::getStableId, Function.identity()));
            }

            for (JsonElement jsonChildRowElement : childAnswersJsonArray) {
                if (jsonChildRowElement.isJsonArray()) {
                    JsonArray jsonChildRowArray = jsonChildRowElement.getAsJsonArray();
                    Set<String> stableQuestionIdsAllowedInRow = new HashSet<>(childDtos.keySet());
                    List<Answer> childAnswersRow = new ArrayList<>();

                    for (JsonElement jsonChildAnswer : jsonChildRowArray) {
                        AnswerSubmission childAnswerSubmission = gson.fromJson(jsonChildAnswer, AnswerSubmission.class);
                        if (childAnswerSubmission == null) {
                            haltError.accept("A child answer had an invalid answer");
                        }

                        String childAnswerStableId = extractQuestionStableId(childAnswerSubmission, response);
                        Optional<QuestionDto> correspondingChildQuestion = Optional.ofNullable(childDtos.get(childAnswerStableId));
                        if (correspondingChildQuestion.isEmpty()) {
                            haltError.accept("Question stable id:" + childAnswerStableId + " in child answer is not valid");
                        }

                        if (!stableQuestionIdsAllowedInRow.remove(childAnswerStableId)) {
                            haltError.accept("Question stable id:" + childAnswerStableId + "was used more than once in answer");
                        }

                        QuestionDto childQuestionDto = extractQuestionDto(response, childAnswerStableId, correspondingChildQuestion);
                        childAnswersRow.add(convertAnswer(handle, response, instanceGuid, childAnswerStableId,
                                childAnswerSubmission.getAnswerGuid(), studyGuid, userGuid, childQuestionDto,
                                childAnswerSubmission.getValue()));
                    }

                    compAnswer.addRowOfChildAnswers(childAnswersRow);
                } else {
                    haltError.accept("An composite answer submission was expected to be an array but was not");
                }
            }

            return compAnswer;
        } else {
            log.info("Provided answer value for question stable id {} and with "
                    + "answer guid {} is expected to be a JSON array", parentStableId, answerGuid);
            return null;
        }
    }

    /**
     * Converts given data to agreement answer.
     *
     * @param stableId the question stable id
     * @param guid     the answer guid, or null
     * @param value    the answer value
     * @return agreement answer object, or null if value is not as expected
     */
    private AgreementAnswer convertAgreementAnswer(String stableId, String guid, String actInstanceGuid, JsonElement value) {
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
            return new AgreementAnswer(null, stableId, guid, value.getAsJsonPrimitive().getAsBoolean(), actInstanceGuid);
        }
        return null;
    }

    /**
     * Runs constraint checks on provided answer. Halts with a 400 response if there are errors.
     *
     * @param response the spark response
     * @param answer   the answer to check
     */
    private void checkAnswerConstraints(Response response, Answer answer) {
        List<JsonValidationError> errors = checker.validateAsJson(answer);
        if (!errors.isEmpty()) {
            String errorMsg = errors.stream()
                    .map(JsonValidationError::toDisplayMessage)
                    .collect(Collectors.joining(", "));
            String msg = "Answer submission with stable id " + answer.getQuestionStableId()
                    + " and answer guid " + answer.getAnswerGuid() + " failed check: " + errorMsg;
            log.warn(msg);
            throw ResponseUtil.haltError(response, 400, new ApiError(ErrorCodes.BAD_PAYLOAD, msg));
        }
        if (QuestionType.DATE.equals(answer.getQuestionType())) {
            Optional<String> failure = ((DateAnswer) answer).getValue().checkFieldCompatibility();
            if (failure.isPresent()) {
                String msg = "Answer submission with stable id " + answer.getQuestionStableId()
                        + " and answer guid " + answer.getAnswerGuid() + " failed check: "
                        + failure.get();
                log.warn(msg);
                throw ResponseUtil.haltError(response, 400, new ApiError(ErrorCodes.BAD_PAYLOAD, msg));
            }
        }
    }

    private void verifyFileUpload(Handle handle, Response response, ActivityInstanceDto instanceDto, FileAnswer answer) {
        long participantId = instanceDto.getParticipantId();
        long studyId = instanceDto.getStudyId();
        List<FileInfo> fileInfos = answer.getValue();
        for (FileInfo info : fileInfos) {
            verifyFileInfo(info, participantId, studyId, handle, response);
        }
    }

    private void verifyFileInfo(FileInfo info, long participantId, long studyId, Handle handle, Response response) {
        long uploadId = info.getUploadId();
        var verifyResult = fileService.verifyUpload(handle, studyId, participantId, uploadId)
                .orElseThrow(() -> new DDPException("Could not find file upload with id " + uploadId));

        switch (verifyResult) {
            case NOT_UPLOADED:
                throw ResponseUtil.haltError(response, 400, new ApiError(ErrorCodes.FILE_ERROR,
                        "File has not been uploaded yet"));
            case OWNER_MISMATCH:
                throw ResponseUtil.haltError(response, 400, new ApiError(ErrorCodes.FILE_ERROR,
                        "File is not owned by participant and cannot be associated with answer"));
            case QUARANTINED:
                throw ResponseUtil.haltError(response, 400, new ApiError(ErrorCodes.FILE_ERROR,
                        "File is infected and cannot be associated with answer"));
            case SIZE_MISMATCH:
                throw ResponseUtil.haltError(response, 400, new ApiError(ErrorCodes.FILE_ERROR,
                        "File uploaded size does not match expected size"));
            case OK:
                log.info("File upload with id {} is uploaded and can be associated with participant's answer", uploadId);
                break;
            default:
                throw new DDPException("Unhandled file check result: " + verifyResult);
        }
    }

    /**
     * Ensure the given answer passes its validation rules.
     *
     * @param answer       the answer to validate
     * @param instanceGuid the activity instance guid
     * @param question     the question object
     * @return the failed validation rule info, or null if validation passed
     */
    private List<Rule> validateAnswer(Answer answer, String instanceGuid, Question question) {
        List<Answer> answersToCheck = new ArrayList<>();
        answersToCheck.add(answer);
        if (answer.getQuestionType() == QuestionType.COMPOSITE) {
            ((CompositeAnswer) answer).getValue().stream()
                    .map(AnswerRow::getValues)
                    .flatMap(Collection::stream)
                    .filter(Objects::nonNull)
                    .forEach(answersToCheck::add);
        }

        Map<String, Question> questionMap = new HashMap<>();
        questionMap.put(question.getStableId(), question);
        if (question.getQuestionType() == QuestionType.COMPOSITE) {
            ((CompositeQuestion) question).getChildren()
                    .forEach(child -> questionMap.put(child.getStableId(), child));
        }

        List<Rule> failedRules = new ArrayList<>();
        for (Answer currentAnswer : answersToCheck) {
            Question currentQuestion = questionMap.get(currentAnswer.getQuestionStableId());
            if (currentQuestion == null) {
                String msg = "Could not find question using activity instance guid " + instanceGuid
                        + " and question stable id " + currentAnswer.getQuestionStableId();
                log.warn(msg);
                throw new NoSuchElementException(msg);
            }

            // For some reason if I don't declare intermediate stream of type Stream<Rule>
            // the filter considers the parameter to be of type Object
            Stream<Rule> questionRules = currentQuestion.getValidations().stream();
            failedRules.addAll(
                    questionRules
                            .filter(rule -> !rule.getAllowSave())
                            .filter(rule -> !rule.validate(currentQuestion, currentAnswer))
                            .collect(toList())
            );
        }

        return failedRules;
    }

    private PatchAnswerResponse enrichPayloadWithValidationFailures(PatchAnswerResponse payload, List<ActivityValidationFailure> failures) {
        payload.addValidationFailures(failures);
        return payload;
    }

    private List<String> createValidationFailureSummaries(List<ActivityValidationFailure> failures) {
        return failures.stream().map(failure -> failure.getErrorMessage()).collect(toList());
    }

    private List<ActivityValidationFailure> getActivityValidationFailures(
            Handle handle, String participantGuid, String operatorGuid, ActivityInstanceDto instanceDto, long languageCodeId
    ) {
        return actValidationService.validate(
                handle, interpreter, participantGuid, operatorGuid, instanceDto.getGuid(), instanceDto.getCreatedAtMillis(),
                instanceDto.getActivityId(), languageCodeId
        );
    }
}
