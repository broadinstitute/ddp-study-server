package org.broadinstitute.ddp.model.event;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.jdbi.v3.core.Handle;

import java.util.HashSet;
import java.util.Set;

/**
 * An action that marks all of agedUp participant's activity instances for parent as read-only.
 */
@Slf4j
public class MarkActivitiesReadOnlyForParentEventAction extends EventAction {
    private final Set<Long> targetActivityIds = new HashSet<>();

    public MarkActivitiesReadOnlyForParentEventAction(EventConfiguration eventConfiguration, EventConfigurationDto dto) {
        this(eventConfiguration, dto.getTargetActivityIds());
    }

    public MarkActivitiesReadOnlyForParentEventAction(EventConfiguration eventConfiguration, Set<Long> targetActivityIds) {
        super(eventConfiguration, null);
        if (targetActivityIds != null) {
            this.targetActivityIds.addAll(targetActivityIds);
        }
    }

    @Override
    public void doAction(PexInterpreter interpreter, Handle handle, EventSignal signal) {
        int numUpdated = handle.attach(ActivityInstanceDao.class)
                .bulkUpdateParentReadOnlyByActivityIds(signal.getParticipantId(), true, targetActivityIds);
        log.info("Marked {} activity instances read-only for agedUp participant's parent {} and target activities {}",
                numUpdated, signal.getParticipantGuid(), targetActivityIds);
    }
}
