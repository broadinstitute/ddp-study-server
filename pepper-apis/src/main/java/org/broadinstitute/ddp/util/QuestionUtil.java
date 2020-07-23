package org.broadinstitute.ddp.util;

import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.jdbi.v3.core.Handle;

public class QuestionUtil {

    public static boolean isReadonly(Handle handle, QuestionDto dto, String activityInstanceGuid) {
        InstanceStatusType statusType = handle.attach(JdbiActivityInstance.class)
                .getByActivityInstanceGuid(activityInstanceGuid)
                .map(ActivityInstanceDto::getStatusType)
                .orElseThrow(() -> new DaoException(String.format(
                        "Could not find activity instance with guid=%s while getting text question with id=%d and stableId=%s",
                        activityInstanceGuid, dto.getId(), dto.getStableId())));

        return dto.isWriteOnce() && InstanceStatusType.COMPLETE.equals(statusType);
    }
}
