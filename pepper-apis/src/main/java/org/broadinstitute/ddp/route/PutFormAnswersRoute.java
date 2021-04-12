package org.broadinstitute.ddp.route;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.analytics.GoogleAnalyticsMetrics;
import org.broadinstitute.ddp.analytics.GoogleAnalyticsMetricsTracker;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.content.I18nContentRenderer;
import org.broadinstitute.ddp.db.ActivityDefStore;
import org.broadinstitute.ddp.db.FormInstanceDao;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceStatusDao;
import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.db.dao.DataExportDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiFormActivitySetting;
import org.broadinstitute.ddp.db.dao.JdbiMailAddress;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.FormActivitySettingDto;
import org.broadinstitute.ddp.db.dto.LanguageDto;
import org.broadinstitute.ddp.db.dto.UserActivityInstanceSummary;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.json.PutAnswersResponse;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.json.workflow.WorkflowResponse;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.instance.ActivityInstance;
import org.broadinstitute.ddp.model.activity.instance.ComponentBlock;
import org.broadinstitute.ddp.model.activity.instance.FormComponent;
import org.broadinstitute.ddp.model.activity.instance.FormInstance;
import org.broadinstitute.ddp.model.activity.instance.MailingAddressComponent;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.validation.ActivityValidationFailure;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.model.activity.types.ComponentType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.model.workflow.ActivityState;
import org.broadinstitute.ddp.model.workflow.WorkflowState;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.broadinstitute.ddp.security.DDPAuth;
import org.broadinstitute.ddp.service.ActivityInstanceService;
import org.broadinstitute.ddp.service.ActivityValidationService;
import org.broadinstitute.ddp.service.DsmAddressValidationStatus;
import org.broadinstitute.ddp.service.WorkflowService;
import org.broadinstitute.ddp.service.actvityinstancebuilder.context.AIBuilderCustomizationFlags;
import org.broadinstitute.ddp.service.actvityinstancebuilder.context.AIBuilderExtraParams;
import org.broadinstitute.ddp.util.ActivityInstanceUtil;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.RouteUtil;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

public class PutFormAnswersRoute implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(PutFormAnswersRoute.class);

    private final WorkflowService workflowService;
    private final ActivityInstanceService activityInstanceService;
    private final ActivityValidationService actValidationService;
    private final FormInstanceDao formInstanceDao;
    private final PexInterpreter interpreter;

    public PutFormAnswersRoute(
            WorkflowService workflowService,
            ActivityInstanceService activityInstanceService,
            ActivityValidationService actValidationService,
            FormInstanceDao formInstanceDao,
            PexInterpreter interpreter
    ) {
        this.workflowService = workflowService;
        this.activityInstanceService = activityInstanceService;
        this.actValidationService = actValidationService;
        this.formInstanceDao = formInstanceDao;
        this.interpreter = interpreter;
    }

    @Override
    public Object handle(Request request, Response response) {
        String userGuid = request.params(PathParam.USER_GUID);
        String studyGuid = request.params(PathParam.STUDY_GUID);
        String instanceGuid = request.params(PathParam.INSTANCE_GUID);

        DDPAuth ddpAuth = RouteUtil.getDDPAuth(request);
        String operatorGuid = StringUtils.defaultIfBlank(ddpAuth.getOperator(), userGuid);
        boolean isStudyAdmin = ddpAuth.hasAdminAccessToStudy(studyGuid);

        LOG.info("Completing form for user {}, operator {} (isStudyAdmin={}), activity instance {}, study {}",
                userGuid, operatorGuid, isStudyAdmin, instanceGuid, studyGuid);

        PutAnswersResponse resp = TransactionWrapper.withTxn(
                handle -> {
                    UserActivityInstanceSummary instanceSummary = RouteUtil.findUserActivityInstanceSummaryOrHalt(
                            response, handle, userGuid, studyGuid, instanceGuid, isStudyAdmin);
                    ActivityInstanceDto instanceDto = instanceSummary.getActivityInstanceByGuid(instanceGuid).get();

                    LanguageDto preferredUserLangDto = RouteUtil.getUserLanguage(request);
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
                            String msg = "Parent activity instance " + parentInstanceDto.getGuid()
                                    + " is read-only, cannot update child instance " + instanceGuid;
                            LOG.info(msg);
                            throw ResponseUtil.haltError(response, 422, new ApiError(ErrorCodes.ACTIVITY_INSTANCE_IS_READONLY, msg));
                        }
                        if (ActivityInstanceUtil.isInstanceReadOnly(activityDef, instanceDto)) {
                            String msg = "Activity instance " + instanceGuid + " is read-only, cannot update activity";
                            LOG.info(msg);
                            throw ResponseUtil.haltError(response, 422, new ApiError(ErrorCodes.ACTIVITY_INSTANCE_IS_READONLY, msg));
                        }
                    }

                    FormInstance form = loadFormInstance(
                            response, handle, userGuid, operatorGuid, studyGuid,
                            instanceGuid, preferredUserLangDto, instanceSummary);
                    if (!form.isComplete()) {
                        String msg = "The status cannot be set to COMPLETE because the question requirements are not met";
                        LOG.info(msg);
                        throw ResponseUtil.haltError(response, 422, new ApiError(ErrorCodes.QUESTION_REQUIREMENTS_NOT_MET, msg));
                    }

                    if (parentInstanceDto == null) {
                        checkChildInstancesCompleteness(
                                response, handle, userGuid, operatorGuid, studyGuid,
                                instanceGuid, preferredUserLangDto, instanceSummary);
                    }

                    // FIXME: address doesn't get saved until this PUT call finishes, so we couldn't check address here.
                    // checkAddressRequirements(handle, userGuid, form);

                    List<ActivityValidationFailure> validationFailures = actValidationService.validate(
                            handle, interpreter, userGuid, operatorGuid, instanceGuid, form.getCreatedAtMillis(),
                            form.getActivityId(), preferredUserLangDto.getId());
                    if (!validationFailures.isEmpty()) {
                        String msg = "Activity validation failed";
                        List<String> validationErrorSummaries = validationFailures
                                .stream().map(failure -> failure.getErrorMessage()).collect(Collectors.toList());
                        LOG.info(msg + ", reasons: {}", validationErrorSummaries);
                        throw ResponseUtil.haltError(response, 422, new ApiError(ErrorCodes.ACTIVITY_VALIDATION, msg));
                    }

                    boolean shouldSnapshotSubstitutions = handle.attach(JdbiFormActivitySetting.class)
                            .findSettingDtoByInstanceGuid(instanceGuid)
                            .map(FormActivitySettingDto::shouldSnapshotSubstitutionsOnSubmit)
                            .orElse(false);
                    if (instanceDto.getFirstCompletedAt() == null && shouldSnapshotSubstitutions) {
                        // This is the first submit for the activity instance, so save a snapshot of substitutions.
                        handle.attach(ActivityInstanceDao.class).saveSubstitutions(
                                form.getInstanceId(),
                                I18nContentRenderer.newValueProvider(handle, form.getParticipantUserId()).getSnapshot());
                    }

                    User participantUser = instanceSummary.getParticipantUser();
                    User operatorUser = participantUser;
                    if (!userGuid.equals(operatorGuid)) {
                        operatorUser = handle.attach(UserDao.class).findUserByGuid(operatorGuid)
                                .orElseThrow(() -> new DDPException("Could not find operator with guid " + operatorGuid));
                    }

                    var instanceStatusDao = handle.attach(ActivityInstanceStatusDao.class);
                    instanceStatusDao.updateOrInsertStatus(instanceDto, InstanceStatusType.COMPLETE,
                            Instant.now().toEpochMilli(), operatorUser, participantUser);
                    if (parentInstanceDto != null) {
                        instanceStatusDao.updateOrInsertStatus(parentInstanceDto, InstanceStatusType.IN_PROGRESS,
                                Instant.now().toEpochMilli(), operatorUser, participantUser);
                    }

                    // Cleanup hidden answers.
                    Set<Long> answerIdsToDelete = form.collectHiddenAnswers()
                            .stream()
                            .map(Answer::getAnswerId)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet());
                    handle.attach(AnswerDao.class).deleteAnswers(answerIdsToDelete);
                    LOG.info("Deleted {} hidden answers for user {} and activity instance {}",
                            answerIdsToDelete.size(), userGuid, instanceGuid);

                    WorkflowState fromState = new ActivityState(form.getActivityId());
                    WorkflowResponse workflowResp = workflowService
                            .suggestNextState(handle, operatorGuid, userGuid, studyGuid, fromState)
                            .map(nextState -> {
                                LOG.info("Suggesting user {} to next state {}", userGuid, nextState);
                                return workflowService.buildStateResponse(handle, userGuid, nextState);
                            })
                            .orElse(WorkflowResponse.unknown());

                    handle.attach(DataExportDao.class).queueDataSync(userGuid, studyGuid);

                    String studyActivityCode = handle.attach(JdbiActivity.class).queryActivityById(
                            instanceDto.getActivityId()).getActivityCode();

                    GoogleAnalyticsMetricsTracker.getInstance().sendAnalyticsMetrics(
                            studyGuid, GoogleAnalyticsMetrics.EVENT_CATEGORY_PUT_ANSWERS,
                            GoogleAnalyticsMetrics.EVENT_ACTION_PUT_ANSWERS, GoogleAnalyticsMetrics.EVENT_LABEL_PUT_ANSWERS,
                            studyActivityCode, 1);

                    return new PutAnswersResponse(workflowResp);
                }
        );

        response.status(200);
        return resp;
    }

    private FormInstance loadFormInstance(Response response,
                                          Handle handle,
                                          String userGuid,
                                          String operatorGuid,
                                          String studyGuid,
                                          String instanceGuid,
                                          LanguageDto preferredLangDto,
                                          UserActivityInstanceSummary instanceSummary) {
        String isoLangCode = preferredLangDto.getIsoCode();
        Optional<ActivityInstance> activityInstance = activityInstanceService.buildInstanceFromDefinition(
                handle, userGuid, operatorGuid, studyGuid, instanceGuid, isoLangCode,
                AIBuilderExtraParams.create().setInstanceSummary(instanceSummary),
                AIBuilderCustomizationFlags.create()
                        .setCreateFormInstance(true)
                        .setAddChildren(true)
                        .setRenderFormTitleSubtitle(true)
                        .setUpdateBlockStatuses(true));
        if (activityInstance.isEmpty()) {
            String msg = String.format("Could not find activity instance %s for user %s using language %s",
                    instanceGuid, userGuid, isoLangCode);
            LOG.warn(msg);
            throw ResponseUtil.haltError(response, 404, new ApiError(ErrorCodes.ACTIVITY_NOT_FOUND, msg));
        }
        return (FormInstance) activityInstance.get();
    }

    private void checkChildInstancesCompleteness(Response response,
                                                 Handle handle,
                                                 String userGuid,
                                                 String operatorGuid,
                                                 String studyGuid,
                                                 String instanceGuid,
                                                 LanguageDto preferredLangDto,
                                                 UserActivityInstanceSummary instanceSummary) {
        // For a parent instance, check if there are any child instances and if those are complete.
        List<ActivityInstanceDto> childInstances = instanceSummary.getInstancesStream()
                .filter(instance -> instanceGuid.equals(instance.getParentInstanceGuid()))
                .collect(Collectors.toList());
        for (var childInstanceDto : childInstances) {
            if (childInstanceDto.getStatusType() != InstanceStatusType.COMPLETE) {
                // Child instance is not finished but it might not have required questions, so check it.
                FormInstance childForm = loadFormInstance(
                        response, handle, userGuid, operatorGuid, studyGuid,
                        childInstanceDto.getGuid(), preferredLangDto, instanceSummary);
                if (!childForm.isComplete()) {
                    String msg = "Status for instance " + instanceGuid + " cannot be set to COMPLETE because the"
                            + " question requirements are not met for child instance " + childInstanceDto.getGuid();
                    LOG.info(msg);
                    throw ResponseUtil.haltError(response, 422,
                            new ApiError(ErrorCodes.QUESTION_REQUIREMENTS_NOT_MET, msg));
                }
            }
        }
    }

    void checkAddressRequirements(Handle handle, String participantGuid, FormInstance form) {
        boolean addressRequireVerified = false;
        boolean addressRequirePhone = false;

        for (var section : form.getAllSections()) {
            for (var block : section.getBlocks()) {
                if (block.isShown() && block.getBlockType() == BlockType.COMPONENT) {
                    FormComponent comp = ((ComponentBlock) block).getFormComponent();
                    if (comp.getComponentType() == ComponentType.MAILING_ADDRESS) {
                        MailingAddressComponent addrComp = (MailingAddressComponent) comp;
                        addressRequireVerified = addressRequireVerified || addrComp.shouldRequireVerified();
                        addressRequirePhone = addressRequirePhone || addrComp.shouldRequirePhone();
                    }
                }
            }
        }

        if (addressRequireVerified || addressRequirePhone) {
            Optional<MailAddress> address = handle.attach(JdbiMailAddress.class).findDefaultAddressForParticipant(participantGuid);
            if (addressRequireVerified) {
                boolean isVerified = address
                        .map(a -> DsmAddressValidationStatus.addressValidStatuses().contains(a.getStatusType()))
                        .orElse(false);
                if (!isVerified) {
                    String msg = "Address needs to be verified";
                    LOG.info(msg);
                    throw ResponseUtil.haltError(422, new ApiError(ErrorCodes.QUESTION_REQUIREMENTS_NOT_MET, msg));
                }
            }
            if (addressRequirePhone) {
                boolean hasPhone = address
                        .map(a -> StringUtils.isNotBlank(a.getPhone()))
                        .orElse(false);
                if (!hasPhone) {
                    String msg = "Address requires a phone number";
                    LOG.info(msg);
                    throw ResponseUtil.haltError(422, new ApiError(ErrorCodes.QUESTION_REQUIREMENTS_NOT_MET, msg));
                }
            }
        }
    }
}
