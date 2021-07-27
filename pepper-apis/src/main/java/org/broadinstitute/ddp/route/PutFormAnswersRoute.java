package org.broadinstitute.ddp.route;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import org.broadinstitute.ddp.db.DBUtils;
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
import org.broadinstitute.ddp.model.activity.instance.FormBlock;
import org.broadinstitute.ddp.model.activity.instance.FormComponent;
import org.broadinstitute.ddp.model.activity.instance.FormInstance;
import org.broadinstitute.ddp.model.activity.instance.FormSection;
import org.broadinstitute.ddp.model.activity.instance.MailingAddressComponent;
import org.broadinstitute.ddp.model.activity.instance.NestedActivityBlock;
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
import org.broadinstitute.ddp.service.AddressService;
import org.broadinstitute.ddp.service.DsmAddressValidationStatus;
import org.broadinstitute.ddp.service.WorkflowService;
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
    private final ActivityInstanceService actInstService;
    private final ActivityValidationService actValidationService;
    private final PexInterpreter interpreter;
    private final AddressService addressService;

    public PutFormAnswersRoute(
            WorkflowService workflowService,
            ActivityInstanceService actInstService,
            ActivityValidationService actValidationService,
            PexInterpreter interpreter,
            AddressService addressService
    ) {
        this.workflowService = workflowService;
        this.actInstService = actInstService;
        this.actValidationService = actValidationService;
        this.interpreter = interpreter;
        this.addressService = addressService;
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
                            response, handle, userGuid, operatorGuid,
                            studyGuid, instanceGuid, preferredUserLangDto, instanceSummary);
                    if (!form.isComplete()) {
                        String msg = "The status cannot be set to COMPLETE because the question requirements are not met";
                        LOG.info(msg);
                        throw ResponseUtil.haltError(response, 422, new ApiError(ErrorCodes.QUESTION_REQUIREMENTS_NOT_MET, msg));
                    }

                    Map<Long, List<ActivityInstanceDto>> hiddenChildInstances = null;
                    if (parentInstanceDto == null) {
                        hiddenChildInstances = checkStateOfChildInstances(
                                response, handle, userGuid, operatorGuid, studyGuid, instanceGuid,
                                form, preferredUserLangDto, instanceSummary);
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

                    Optional<FormActivitySettingDto> formActivitySettingDto = handle.attach(JdbiFormActivitySetting.class)
                            .findSettingDtoByInstanceGuid(instanceGuid);
                    // if this is the first submit for the activity instance, then do snapshots
                    if (formActivitySettingDto.isPresent() && instanceDto.getFirstCompletedAt() == null) {
                        snapshotSubstitutions(handle, studyGuid, operatorGuid, form,
                                formActivitySettingDto.get().shouldSnapshotSubstitutionsOnSubmit());
                        if (formActivitySettingDto.get().shouldSnapshotAddressOnSubmit()) {
                            var address = addressService.snapshotAddress(handle, userGuid, operatorGuid, form.getInstanceId());
                            LOG.info("Created snapshotted address with guid {} for user {} and activity instance {}",
                                    address.getGuid(), userGuid, instanceGuid);
                        }
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

                    cleanupHiddenAnswers(handle, userGuid, form);
                    cleanupHiddenChildInstances(handle, activityStore, userGuid, form, hiddenChildInstances);

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

    /**
     * If this is the first submit for the activity instance and should snapshot flag is true,
     * then save a snapshot of substitutions.
     */
    private void snapshotSubstitutions(Handle handle, String studyGuid, String operatorGuid,
                                       FormInstance form, boolean shouldSnapshotSubstitutionsOnSubmit) {
        if (shouldSnapshotSubstitutionsOnSubmit) {
            handle.attach(ActivityInstanceDao.class).saveSubstitutions(
                    form.getInstanceId(),
                    I18nContentRenderer.newValueProvider(
                            handle, form.getParticipantUserId(), operatorGuid, studyGuid).getSnapshot());
        }
    }

    private FormInstance loadFormInstance(Response response,
                                          Handle handle,
                                          String userGuid,
                                          String operatorGuid,
                                          String studyGuid,
                                          String instanceGuid,
                                          LanguageDto preferredLangDto,
                                          UserActivityInstanceSummary instanceSummary) {
        long langCodeId = preferredLangDto.getId();
        String isoLangCode = preferredLangDto.getIsoCode();
        Optional<ActivityInstance> activityInstance = actInstService.buildInstanceFromDefinition(
                handle, userGuid, operatorGuid, studyGuid, instanceGuid, isoLangCode, instanceSummary);
        if (activityInstance.isEmpty()) {
            String msg = String.format("Could not find activity instance %s for user %s using language %s",
                    instanceGuid, userGuid, isoLangCode);
            LOG.warn(msg);
            throw ResponseUtil.haltError(response, 404, new ApiError(ErrorCodes.ACTIVITY_NOT_FOUND, msg));
        }
        return (FormInstance) activityInstance.get();
    }

    // For a parent instance, check if there are any child instances that are not hidden and if those are complete.
    // For efficiency, we also keep track of which child instances are hidden so we can clean them up later.
    private Map<Long, List<ActivityInstanceDto>> checkStateOfChildInstances(Response response,
                                                                            Handle handle,
                                                                            String userGuid,
                                                                            String operatorGuid,
                                                                            String studyGuid,
                                                                            String instanceGuid,
                                                                            FormInstance form,
                                                                            LanguageDto preferredLangDto,
                                                                            UserActivityInstanceSummary instanceSummary) {
        Map<String, List<ActivityInstanceDto>> childInstances = new HashMap<>();
        Map<Long, List<ActivityInstanceDto>> hiddenChildInstances = new HashMap<>();
        instanceSummary.getInstancesStream()
                .filter(instance -> instanceGuid.equals(instance.getParentInstanceGuid()))
                .forEach(instance -> childInstances
                        .computeIfAbsent(instance.getActivityCode(), key -> new ArrayList<>())
                        .add(instance));

        for (FormSection section : form.getAllSections()) {
            for (FormBlock block : section.getBlocks()) {
                if (block.getBlockType() != BlockType.ACTIVITY) {
                    continue;
                }

                var nestedActivityBlock = (NestedActivityBlock) block;
                List<ActivityInstanceDto> childInstanceDtos = childInstances
                        .getOrDefault(nestedActivityBlock.getActivityCode(), new ArrayList<>());
                if (!nestedActivityBlock.isShown()) {
                    if (!childInstanceDtos.isEmpty()) {
                        long childActivityId = childInstanceDtos.get(0).getActivityId();
                        hiddenChildInstances.put(childActivityId, childInstanceDtos);
                    }
                    continue;
                }

                for (var childInstanceDto : childInstanceDtos) {
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

        return hiddenChildInstances;
    }

    public void cleanupHiddenAnswers(Handle handle, String userGuid, FormInstance form) {
        Set<Long> answerIdsToDelete = form.collectHiddenAnswers()
                .stream()
                .map(Answer::getAnswerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        handle.attach(AnswerDao.class).deleteAnswers(answerIdsToDelete);
        LOG.info("Deleted {} hidden answers for user {} and activity instance {}",
                answerIdsToDelete.size(), userGuid, form.getGuid());
    }

    private void cleanupHiddenChildInstances(Handle handle,
                                             ActivityDefStore activityStore,
                                             String userGuid,
                                             FormInstance form,
                                             Map<Long, List<ActivityInstanceDto>> hiddenChildInstances) {
        if (hiddenChildInstances == null || hiddenChildInstances.isEmpty()) {
            return;
        }

        var answerDao = handle.attach(AnswerDao.class);
        var instanceDao = handle.attach(ActivityInstanceDao.class);
        for (var entry : hiddenChildInstances.entrySet()) {
            var childInstanceDtos = entry.getValue();
            if (childInstanceDtos.isEmpty()) {
                continue;
            }
            var childActivity = activityStore.findActivityDto(handle, entry.getKey())
                    .orElseThrow(() -> new DDPException("Could not find child activity with id " + entry.getKey()));
            if (!childActivity.canDeleteInstances()) {
                continue;
            }

            childInstanceDtos.sort(Comparator.comparing(ActivityInstanceDto::getCreatedAtMillis));
            Set<Long> deletableInstanceIds = new HashSet<>();
            Set<Long> deleteOnlyAnswers = new HashSet<>();

            for (int i = 0; i < childInstanceDtos.size(); i++) {
                boolean isFirstInstance = (i == 0);
                boolean canDelete = ActivityInstanceUtil.computeCanDelete(
                        childActivity.canDeleteInstances(),
                        childActivity.getCanDeleteFirstInstance(),
                        isFirstInstance);
                if (canDelete) {
                    // Should delete the instance and all its answers.
                    deletableInstanceIds.add(childInstanceDtos.get(i).getId());
                } else {
                    // Can't delete the instance itself, but let's at the very least cleanup its answers.
                    deleteOnlyAnswers.add(childInstanceDtos.get(i).getId());
                }
            }

            DBUtils.checkDelete(deletableInstanceIds.size(), instanceDao.deleteAllByIds(deletableInstanceIds));
            LOG.info("Deleted {} hidden child instances for user {}, parent instance {}, child activity {}",
                    deletableInstanceIds.size(), userGuid, form.getGuid(), childActivity.getActivityCode());

            answerDao.deleteAllByInstanceIds(deleteOnlyAnswers);
            LOG.info("Cleared answers for {} hidden child instances for user {}, parent instance {}, child activity {}",
                    deleteOnlyAnswers.size(), userGuid, form.getGuid(), childActivity.getActivityCode());
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
