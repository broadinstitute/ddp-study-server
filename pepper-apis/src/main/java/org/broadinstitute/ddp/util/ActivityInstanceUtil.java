package org.broadinstitute.ddp.util;

import java.time.Instant;

import org.broadinstitute.ddp.db.ActivityDefStore;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.jdbi.v3.core.Handle;

public class ActivityInstanceUtil {

    /**
     * Convenience helper to extract activity definition from the activity store.
     */
    public static FormActivityDef getActivityDef(Handle handle, ActivityDefStore activityStore,
                                           ActivityInstanceDto instanceDto, String studyGuid) {
        long activityId = instanceDto.getActivityId();
        String instanceGuid = instanceDto.getGuid();
        ActivityDto activityDto = activityStore.findActivityDto(handle, activityId)
                .orElseThrow(() -> new DDPException("Could not find activity dto for instance " + instanceGuid));
        ActivityVersionDto versionDto = activityStore
                .findVersionDto(handle, activityId, instanceDto.getCreatedAtMillis())
                .orElseThrow(() -> new DDPException("Could not find activity version for instance " + instanceGuid));
        return activityStore.findActivityDef(handle, studyGuid, activityDto, versionDto)
                .orElseThrow(() -> new DDPException("Could not find activity definition for instance " + instanceGuid));
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
                instanceDto.getReadonly());
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
        Boolean isReadonly = activityInstanceDto.getReadonly();
        if (isReadonly != null) {
            return isReadonly;
        }
        long studyActivityId = activityInstanceDto.getActivityId();

        JdbiActivity jdbiActivity = handle.attach(JdbiActivity.class);
        ActivityDto activityDto = jdbiActivity.queryActivityById(studyActivityId);

        return computeReadonly(activityDto.isWriteOnce(), activityDto.getEditTimeoutSec(),
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
}
