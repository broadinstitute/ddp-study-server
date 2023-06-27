package org.broadinstitute.ddp.route;

import static java.lang.String.format;

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

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
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
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudyCached;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.FormActivitySettingDto;
import org.broadinstitute.ddp.db.dto.LanguageDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserActivityInstanceSummary;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.json.PutAnswersResponse;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.json.workflow.WorkflowActivityResponse;
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
import spark.Request;
import spark.Response;
import spark.Route;

@Slf4j
@AllArgsConstructor
public class PutFormAnswersRoute implements Route {
    private final WorkflowService workflowService;
    private final ActivityInstanceService actInstService;
    private final ActivityValidationService actValidationService;
    private final PexInterpreter interpreter;
    private final AddressService addressService;

    @Override
    public Object handle(Request request, Response response) {
        String userGuid = request.params(PathParam.USER_GUID);
        String studyGuid = request.params(PathParam.STUDY_GUID);
        String instanceGuid = request.params(PathParam.INSTANCE_GUID);

        DDPAuth ddpAuth = RouteUtil.getDDPAuth(request);
        String operatorGuid = StringUtils.defaultIfBlank(ddpAuth.getOperator(), userGuid);
        boolean isStudyAdmin = ddpAuth.hasAdminAccessToStudy(studyGuid);

        log.info("Completing form for user {}, operator {} (isStudyAdmin={}), activity instance {}, study {}",
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
                            log.info(msg);
                            throw ResponseUtil.haltError(response, 422, new ApiError(ErrorCodes.ACTIVITY_INSTANCE_IS_READONLY, msg));
                        }
                        if (ActivityInstanceUtil.isInstanceReadOnly(activityDef, instanceDto)) {
                            String msg = "Activity instance " + instanceGuid + " is read-only, cannot update activity";
                            log.info(msg);
                            throw ResponseUtil.haltError(response, 422, new ApiError(ErrorCodes.ACTIVITY_INSTANCE_IS_READONLY, msg));
                        }
                    }

                    FormInstance form = loadFormInstance(
                            response, handle, userGuid, operatorGuid,
                            studyGuid, instanceGuid, preferredUserLangDto, instanceSummary);
                    if (!form.isComplete()) {
                        String msg = "The status cannot be set to COMPLETE because the question requirements are not met";
                        log.info(msg);
                        throw ResponseUtil.haltError(response, 422, new ApiError(ErrorCodes.QUESTION_REQUIREMENTS_NOT_MET, msg));
                    }

                    Map<Long, List<ActivityInstanceDto>> hiddenAndDisabledChildInstances = null;
                    if (parentInstanceDto == null) {
                        hiddenAndDisabledChildInstances = checkStateOfChildInstances(
                                response, handle, userGuid, operatorGuid, studyGuid, instanceGuid,
                                form, preferredUserLangDto, instanceSummary);
                    }

                    // FIXME: address doesn't get saved until this PUT call finishes, so we couldn't check address here.
                    // checkAddressRequirements(handle, userGuid, form);

                    User participantUser = instanceSummary.getParticipantUser();
                    User operatorUser = participantUser;
                    if (!userGuid.equals(operatorGuid)) {
                        operatorUser = handle.attach(UserDao.class).findUserByGuid(operatorGuid)
                                .orElseThrow(() -> new DDPException("Could not find operator with guid " + operatorGuid));
                    }

                    var instanceStatusDao = handle.attach(ActivityInstanceStatusDao.class);

                    List<ActivityValidationFailure> validationFailures = actValidationService.validate(
                            handle, interpreter, userGuid, operatorGuid, instanceGuid, form.getCreatedAtMillis(),
                            form.getActivityId(), preferredUserLangDto.getId());
                    if (!validationFailures.isEmpty()) {
                        //check if study has errorPresentStatus enabled
                        StudyDto studyDto = new JdbiUmbrellaStudyCached(handle).findByStudyGuid(studyGuid);
                        if (studyDto.isErrorPresentStatusEnabled()) {
                            instanceStatusDao.updateOrInsertStatus(instanceDto, InstanceStatusType.ERROR_PRESENT,
                                    Instant.now().toEpochMilli(), operatorUser, participantUser);
                            return new PutAnswersResponse(WorkflowResponse.unknown());
                        }

                        String msg = "Activity validation failed";
                        List<String> validationErrorSummaries = validationFailures
                                .stream().map(ActivityValidationFailure::getErrorMessage).collect(Collectors.toList());
                        log.info(msg + ", reasons: {}", validationErrorSummaries);
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
                            if (address != null) {
                                log.info("Default address is snapshotted with guid {}, for user {}, activity instance {}",
                                        address.getGuid(), userGuid, instanceGuid);
                            } else {
                                String errorMsg = format("Default mail address is not found, therefore the snapshotting is not possible. "
                                        + "User %s, activity instance %s", userGuid, instanceGuid);
                                throw ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND,
                                        new ApiError(ErrorCodes.MAIL_ADDRESS_NOT_FOUND, errorMsg));
                            }
                        }
                    }

                    instanceStatusDao.updateOrInsertStatus(instanceDto, InstanceStatusType.COMPLETE,
                            Instant.now().toEpochMilli(), operatorUser, participantUser);
                    if (parentInstanceDto != null) {
                        instanceStatusDao.updateOrInsertStatus(parentInstanceDto, InstanceStatusType.IN_PROGRESS,
                                Instant.now().toEpochMilli(), operatorUser, participantUser);
                    }

                    cleanupHiddenAndDisabledAnswers(handle, userGuid, form);
                    cleanupChildInstances(handle, activityStore, userGuid, form, hiddenAndDisabledChildInstances);

                    WorkflowState fromState = new ActivityState(form.getActivityId());
                    WorkflowResponse workflowResp = workflowService
                            .suggestNextState(handle, operatorGuid, userGuid, studyGuid, fromState)
                            .map(nextState -> {
                                //special case for SOMATIC_RESULTS ... need to pass same instance in next state and NOT latest
                                if (instanceDto.getActivityCode().equalsIgnoreCase("SOMATIC_RESULTS")
                                        && fromState.matches(nextState)) {
                                    return new WorkflowActivityResponse(
                                            instanceDto.getActivityCode(),
                                            instanceDto.getGuid(),
                                            instanceDto.isAllowUnauthenticated());
                                }
                                log.info("Suggesting user {} to next state {}", userGuid, nextState);
                                return workflowService.buildStateResponse(handle, userGuid, nextState);
                            })
                            .orElse(WorkflowResponse.unknown());

                    handle.attach(DataExportDao.class).queueDataSync(userGuid, studyGuid);

                    String studyActivityCode = handle.attach(JdbiActivity.class).queryActivityById(
                            instanceDto.getActivityId()).getActivityCode();

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
                            handle, form.getInstanceId(), form.getParticipantUserId(), operatorGuid, studyGuid).getSnapshot());
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
        String isoLangCode = preferredLangDto.getIsoCode();
        Optional<ActivityInstance> activityInstance = actInstService.buildInstanceFromDefinition(
                handle, userGuid, operatorGuid, studyGuid, instanceGuid, isoLangCode, instanceSummary);
        if (activityInstance.isEmpty()) {
            String msg = format("Could not find activity instance %s for user %s using language %s",
                    instanceGuid, userGuid, isoLangCode);
            log.warn(msg);
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
        Map<Long, List<ActivityInstanceDto>> hiddenOrDisabledChildInstances = new HashMap<>();
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
                if (!nestedActivityBlock.isShown() || !nestedActivityBlock.isEnabled()) {
                    if (!childInstanceDtos.isEmpty()) {
                        long childActivityId = childInstanceDtos.get(0).getActivityId();
                        hiddenOrDisabledChildInstances.put(childActivityId, childInstanceDtos);
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
                        log.info(msg);
                        throw ResponseUtil.haltError(response, 422,
                                new ApiError(ErrorCodes.QUESTION_REQUIREMENTS_NOT_MET, msg));
                    }
                }
            }
        }

        return hiddenOrDisabledChildInstances;
    }

    public void cleanupHiddenAndDisabledAnswers(Handle handle, String userGuid, FormInstance form) {
        Set<Long> answerIdsToDelete = form.collectHiddenAndDisabledAnswers()
                .stream()
                .map(Answer::getAnswerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        handle.attach(AnswerDao.class).deleteAnswers(answerIdsToDelete);
        log.info("Deleted {} hidden answers for user {} and activity instance {}",
                answerIdsToDelete.size(), userGuid, form.getGuid());
    }

    private void cleanupChildInstances(Handle handle,
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
                        childActivity.canDeleteFirstInstance(),
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
            log.info("Deleted {} hidden child instances for user {}, parent instance {}, child activity {}",
                    deletableInstanceIds.size(), userGuid, form.getGuid(), childActivity.getActivityCode());

            answerDao.deleteAllByInstanceIds(deleteOnlyAnswers);
            log.info("Cleared answers for {} hidden child instances for user {}, parent instance {}, child activity {}",
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
                    log.info(msg);
                    throw ResponseUtil.haltError(422, new ApiError(ErrorCodes.QUESTION_REQUIREMENTS_NOT_MET, msg));
                }
            }
            if (addressRequirePhone) {
                boolean hasPhone = address
                        .map(a -> StringUtils.isNotBlank(a.getPhone()))
                        .orElse(false);
                if (!hasPhone) {
                    String msg = "Address requires a phone number";
                    log.info(msg);
                    throw ResponseUtil.haltError(422, new ApiError(ErrorCodes.QUESTION_REQUIREMENTS_NOT_MET, msg));
                }
            }
        }
    }
}
