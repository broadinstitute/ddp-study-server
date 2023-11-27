package org.broadinstitute.ddp.model.event;

import java.util.HashSet;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.jdbi.v3.core.Handle;

/**
 * An action that marks all of a participant's activity instances for specified target activities as read-only.
 */
@Slf4j
public class MarkActivitiesReadOnlyEventAction extends EventAction {
    private final Set<Long> targetActivityIds = new HashSet<>();

    public MarkActivitiesReadOnlyEventAction(EventConfiguration eventConfiguration, EventConfigurationDto dto) {
        this(eventConfiguration, dto.getTargetActivityIds());
    }

    public MarkActivitiesReadOnlyEventAction(EventConfiguration eventConfiguration, Set<Long> targetActivityIds) {
        super(eventConfiguration, null);
        if (targetActivityIds != null) {
            this.targetActivityIds.addAll(targetActivityIds);
        }
    }

    @Override
    public void doAction(PexInterpreter interpreter, Handle handle, EventSignal signal) {
        int numUpdated = handle.attach(ActivityInstanceDao.class)
                .bulkUpdateReadOnlyByActivityIds(signal.getParticipantId(), true, targetActivityIds);
        log.info("Marked {} activity instances read-only for participant {} and target activities {}",
                numUpdated, signal.getParticipantGuid(), targetActivityIds);
    }
}
