package org.broadinstitute.ddp.util;

import java.time.Instant;
import java.util.Collection;

import org.broadinstitute.ddp.db.dao.ActivityInstanceStatusDao;
import org.broadinstitute.ddp.db.dao.JdbiFormActivityStatusQuery.FormQuestionRequirementStatus;
import org.broadinstitute.ddp.db.dto.ActivityInstanceStatusDto;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;

import org.jdbi.v3.core.Handle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FormActivityStatusUtil {

    private static final Logger LOG = LoggerFactory.getLogger(FormActivityStatusUtil.class);

    /**
     * Reviews the list of individual form question requirements
     * and returns the string status that should be used.  One should
     * only call this method after one has altered the data for a form
     * as this method assumes that some data has been saved and only
     * decides between "in progress" and "complete".
     */
    public static InstanceStatusType determineActivityStatus(
            Collection<FormQuestionRequirementStatus> questionRequirementStatuses
    ) {
        boolean hasUnmetStatusRequirements = questionRequirementStatuses.stream()
                .filter(qrs -> qrs.hasUnmetAnswerRequirement())
                .count() > 0;
        return hasUnmetStatusRequirements ? InstanceStatusType.IN_PROGRESS : InstanceStatusType.COMPLETE;
    }

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
        LOG.info("Changed the status of activity instance guid {} to {}", activityInstanceGuid, actualNewStatusDto.getTypeId());
    }

}
