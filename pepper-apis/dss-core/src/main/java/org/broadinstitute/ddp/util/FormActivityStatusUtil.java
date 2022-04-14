package org.broadinstitute.ddp.util;

import java.time.Instant;

import org.broadinstitute.ddp.db.dao.ActivityInstanceStatusDao;
import org.broadinstitute.ddp.db.dto.ActivityInstanceStatusDto;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FormActivityStatusUtil {

    private static final Logger LOG = LoggerFactory.getLogger(FormActivityStatusUtil.class);

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
        LOG.info("Will attempt to set newStatus of activity instance guid {} to {}", activityInstanceGuid, newStatus);
        ActivityInstanceStatusDto actualNewStatusDto = instanceStatusDao.insertStatus(
                activityInstanceGuid, newStatus, Instant.now().toEpochMilli(), operatorGuid
        );
        LOG.info("Changed the status of activity instance guid {} to {}", activityInstanceGuid, actualNewStatusDto.getType());
    }
}
