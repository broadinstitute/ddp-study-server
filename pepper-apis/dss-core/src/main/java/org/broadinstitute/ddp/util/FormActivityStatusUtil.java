package org.broadinstitute.ddp.util;

import java.time.Instant;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.ActivityInstanceStatusDao;
import org.broadinstitute.ddp.db.dto.ActivityInstanceStatusDto;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.jdbi.v3.core.Handle;

@Slf4j
public class FormActivityStatusUtil {
    /**
     * Sets the status of this form activity
     */
    public static void updateFormActivityStatus(
            Handle handle,
            InstanceStatusType newStatus,
            String activityInstanceGuid,
            String operatorGuid
    ) {
        ActivityInstanceStatusDao instanceStatusDao = handle.attach(ActivityInstanceStatusDao.class);
        log.info("Will attempt to set newStatus of activity instance guid {} to {}", activityInstanceGuid, newStatus);
        ActivityInstanceStatusDto actualNewStatusDto = instanceStatusDao.insertStatus(
                activityInstanceGuid, newStatus, Instant.now().toEpochMilli(), operatorGuid
        );
        log.info("Changed the status of activity instance guid {} to {}", activityInstanceGuid, actualNewStatusDto.getType());
    }
}
