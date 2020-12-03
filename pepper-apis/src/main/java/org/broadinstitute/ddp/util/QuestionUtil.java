package org.broadinstitute.ddp.util;

import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.jdbi.v3.core.Handle;

public class QuestionUtil {

    /**
     * Checks if a question is read-only
     *
     * @param handle               JDBI handle
     * @param dto                  QuestionDto
     * @param activityInstanceGuid GUID of the activity instance to check
     * @return The result of the check
     s*/
    public static boolean isReadonly(Handle handle, QuestionDto dto, String activityInstanceGuid) {
        boolean isWriteOnce = dto.isWriteOnce();
        if (!isWriteOnce) {
            return false;
        }

        ActivityInstanceDto activityInstanceDto = handle.attach(JdbiActivityInstance.class)
                .getByActivityInstanceGuid(activityInstanceGuid).orElseThrow(() -> new DaoException(String.format(
                        "Could not find activity instance with guid=%s while getting question with id=%d and stableId=%s",
                        activityInstanceGuid, dto.getId(), dto.getStableId())));

        InstanceStatusType statusType = activityInstanceDto.getStatusType();
        if (InstanceStatusType.COMPLETE.equals(statusType)) {
            return true;
        }

        // If it is not the first instance - block editing
        var instanceDao = handle.attach(ActivityInstanceDao.class);
        Long previousInstanceId = instanceDao.findMostRecentInstanceBeforeCurrent(activityInstanceDto.getId())
                .orElse(null);
        return previousInstanceId != null;
    }
}
