package org.broadinstitute.ddp.util;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.db.ActivityDefStore;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.AnswerCachedDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudyCached;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.instance.ActivityInstance;
import org.broadinstitute.ddp.model.activity.instance.FormResponse;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedPicklistOption;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.jdbi.v3.core.Handle;

public class ActivityInstanceUtil {

    /**
     * Convenience helper to extract activity definition from the activity store.
     */
    public static FormActivityDef getActivityDef(Handle handle, ActivityDefStore activityStore,
                                           ActivityInstanceDto instanceDto, String studyGuid) {
        return getActivityDef(handle, activityStore, studyGuid, instanceDto.getActivityId(),
                instanceDto.getGuid(), instanceDto.getCreatedAtMillis());
    }

    public static FormActivityDef getActivityDef(Handle handle, ActivityDefStore activityStore,
                                    String studyGuid, long activityId, String instanceGuid, long createdAtMillis) {
        ActivityDto activityDto = activityStore.findActivityDto(handle, activityId)
                .orElseThrow(() -> new DDPException("Could not find activity dto for instance " + instanceGuid));
        ActivityVersionDto versionDto = activityStore
                .findVersionDto(handle, activityId, createdAtMillis)
                .orElseThrow(() -> new DDPException("Could not find activity version for instance " + instanceGuid));
        return activityStore.findActivityDef(handle, studyGuid, activityDto, versionDto)
                .orElseThrow(() -> new DDPException("Could not find activity definition for instance " + instanceGuid));
    }

    public static FormActivityDef getActivityDef(Handle handle, ActivityDefStore activityStore, Long instanceId) {
        ActivityInstanceDto activityInstanceDto = handle.attach(JdbiActivityInstance.class).getByActivityInstanceId(instanceId).get();
        var studyGuid = new JdbiUmbrellaStudyCached(handle).findGuidByStudyId(activityInstanceDto.getStudyId());
        return ActivityInstanceUtil.getActivityDef(handle, activityStore, activityInstanceDto, studyGuid);
    }

    /**
     * Get {@link FormResponse} by {@link ActivityInstance#getGuid()}
     */
    public static Optional<FormResponse> getFormResponse(Handle handle, String activityInstGuid) {
        return handle.attach(ActivityInstanceDao.class)
                .findFormResponseWithAnswersByInstanceGuid(activityInstGuid);
    }

    /**
     * Find most recent {@link ActivityInstance} before a current one. Returns ID of a found instance
     * or null if such not exists.
     */
    public static Long getPreviousInstanceId(Handle handle, long instanceId) {
        return handle.attach(ActivityInstanceDao.class)
                .findMostRecentInstanceBeforeCurrent(instanceId)
                .orElse(null);
    }

    /**
     * Convenience helper to check read-only status of instance given the activity definition and instance dto.
     */
    public static boolean isInstanceReadOnly(FormActivityDef activityDef, ActivityInstanceDto instanceDto) {
        return ActivityInstanceUtil.isReadonly(
                activityDef.getEditTimeoutSec(),
                instanceDto.getCreatedAtMillis(),
                instanceDto.getStatusType().name(),
                activityDef.isWriteOnce(),
                instanceDto.getIsReadonly());
    }

    /**
     * Checks if an activity instance is read-only
     *
     * @param handle               JDBI handle
     * @param activityInstanceGuid GUID of the activity instance to check
     * @return The result of the check
     */
    public static boolean isReadonly(Handle handle, String activityInstanceGuid) {
        JdbiActivityInstance jdbiActivityInstance = handle.attach(JdbiActivityInstance.class);
        ActivityInstanceDto activityInstanceDto = jdbiActivityInstance.getByActivityInstanceGuid(activityInstanceGuid)
                .orElseThrow(IllegalArgumentException::new);
        // is_readonly flag in the activity instance overrides everything
        Boolean isReadonly = activityInstanceDto.getIsReadonly();
        if (isReadonly != null) {
            return isReadonly;
        }
        long studyActivityId = activityInstanceDto.getActivityId();

        JdbiActivity jdbiActivity = handle.attach(JdbiActivity.class);
        ActivityDto activityDto = jdbiActivity.queryActivityById(studyActivityId);

        return computeReadonly(activityDto.writeOnce(), activityDto.getEditTimeoutSec(),
                activityInstanceDto.getStatusType(), activityInstanceDto.getCreatedAtMillis());
    }

    /**
     * A faster version of the isReadonly that does not touch the db
     *
     * @param editTimeoutSec             The period after which the activity instance becomes stale
     * @param createdAtMillis            The milliseconds since epoch when activity instance was created
     * @param statusTypeCode             Current status of the activity instance
     * @param isActivityWriteOnce        The flag indicating whether the activity in write once
     * @param isActivityInstanceReadonly The flag indicating whether the activity instance is r/o
     * @return The result of the check
     */
    public static boolean isReadonly(
            Long editTimeoutSec,
            long createdAtMillis,
            String statusTypeCode,
            boolean isActivityWriteOnce,
            Boolean isActivityInstanceReadonly
    ) {
        // is_readonly flag in the activity instance overrides everything
        if (isActivityInstanceReadonly != null) {
            return isActivityInstanceReadonly;
        }
        return computeReadonly(isActivityWriteOnce, editTimeoutSec, InstanceStatusType.valueOf(statusTypeCode), createdAtMillis);
    }

    private static boolean computeReadonly(boolean isActivityWriteOnce, Long editTimeoutSec,
                                           InstanceStatusType statusType, long createdAtMillis) {
        // Write-once activities become read-only once they are complete
        if (isActivityWriteOnce && InstanceStatusType.COMPLETE.equals(statusType)) {
            return true;
        }

        // Stale activities also become read-only
        long millisDiff = Instant.now().toEpochMilli() - createdAtMillis;
        return editTimeoutSec != null && millisDiff >= (editTimeoutSec * 1000L);
    }

    /**
     * An activity instance can be deleted if the definition allows deleting instances. And if it's the first instance,
     * definition need to allow deleting the first instance as well.
     *
     * @param canDeleteInstance      whether instances can be deleted, based on activity definition
     * @param canDeleteFirstInstance whether the first instance can be deleted, based on activity definition, will
     *                               default to true if not set
     * @param isFirstInstance        whether the instance is the first one or not
     * @return true if activity instance can be deleted
     */
    public static boolean computeCanDelete(boolean canDeleteInstance, Boolean canDeleteFirstInstance, boolean isFirstInstance) {
        boolean canDelete = canDeleteInstance;
        if (canDeleteInstance && isFirstInstance) {
            canDelete = canDeleteFirstInstance != null ? canDeleteFirstInstance : true;
        }
        return canDelete;
    }

    public static void populateDefaultValues(Handle handle, long instanceId, long operatorId) {
        var answerDao = new AnswerCachedDao(handle);
        FormActivityDef activityDef = getActivityDef(handle, ActivityDefStore.getInstance(), instanceId);
        List<QuestionDef> questions = QuestionUtil.collectQuestions(activityDef, questionDef ->
                (questionDef instanceof PicklistQuestionDef) && !((PicklistQuestionDef) questionDef).getDefaultOptions().isEmpty());
        for (QuestionDef question : questions) {
            PicklistQuestionDef plQuestion = (PicklistQuestionDef) question;
            var answer = new PicklistAnswer(null, plQuestion.getStableId(), null,
                    plQuestion.getDefaultOptions().stream()
                            .map(plOpt -> new SelectedPicklistOption(plOpt.getStableId()))
                            .collect(Collectors.toList()));
            answerDao.createAnswer(operatorId, instanceId, answer, plQuestion);
        }
    }
}
