package org.broadinstitute.ddp.model.event;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dao.QueuedEventDao;
import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.broadinstitute.ddp.service.EventService;
import org.jdbi.v3.core.Handle;

@Slf4j
public class UpdateUserStatusEventAction extends EventAction {
    private final EnrollmentStatusType targetStatusType;

    public UpdateUserStatusEventAction(EventConfiguration eventConfiguration, EventConfigurationDto dto) {
        super(eventConfiguration, dto);
        this.targetStatusType = dto.getUpdateUserStatusTargetStatusType();
    }

    @Override
    public void doAction(PexInterpreter pexInterpreter, Handle handle, EventSignal signal) {
        Integer delayBeforePosting = eventConfiguration.getPostDelaySeconds();
        if (delayBeforePosting != null && delayBeforePosting > 0) {
            if (!eventConfiguration.dispatchToHousekeeping()) {
                throw new DDPException("Incompatible event configuration:"
                        + " delayed UpdateUserStatus events should set dispatchToHousekeeping");
            }
            long queuedEventId = queueDelayedEvent(handle, signal);
            log.info("Queued UpdateUserStatus event with id {}", queuedEventId);
        } else {
            doActionSynchronously(handle, signal);
        }
    }

    public void doActionSynchronously(Handle handle, EventSignal signal) {
        var jdbiUserStudyEnrollment = handle.attach(JdbiUserStudyEnrollment.class);

        jdbiUserStudyEnrollment.changeUserStudyEnrollmentStatus(
                signal.getParticipantId(),
                signal.getStudyId(),
                targetStatusType);
        log.info("Changed enrollment status for participant {} in study {} to {}",
                signal.getParticipantGuid(), signal.getStudyId(), targetStatusType);

        triggerEvents(handle, new UserStatusChangedSignal(
                signal.getOperatorId(),
                signal.getParticipantId(),
                signal.getParticipantGuid(),
                signal.getOperatorGuid(),
                signal.getStudyId(),
                signal.getStudyGuid(),
                targetStatusType));
    }

    @VisibleForTesting
    long queueDelayedEvent(Handle handle, EventSignal signal) {
        return handle.attach(QueuedEventDao.class).addToQueue(
                eventConfiguration.getEventConfigurationId(),
                signal.getOperatorId(),
                signal.getParticipantId(),
                eventConfiguration.getPostDelaySeconds());
    }

    @VisibleForTesting
    void triggerEvents(Handle handle, EventSignal signal) {
        EventService.getInstance().processAllActionsForEventSignal(handle, signal);
    }
}
