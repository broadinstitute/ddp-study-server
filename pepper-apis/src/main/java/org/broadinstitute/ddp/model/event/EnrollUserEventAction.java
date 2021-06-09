package org.broadinstitute.ddp.model.event;

import com.google.common.annotations.VisibleForTesting;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.broadinstitute.ddp.service.EventService;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnrollUserEventAction extends EventAction {

    private static final Logger LOG = LoggerFactory.getLogger(EnrollUserEventAction.class);

    public EnrollUserEventAction(EventConfiguration eventConfiguration, EventConfigurationDto dto) {
        super(eventConfiguration, dto);
    }

    @Override
    public void doAction(PexInterpreter pexInterpreter, Handle handle, EventSignal signal) {
        var jdbiUserStudyEnrollment = handle.attach(JdbiUserStudyEnrollment.class);

        jdbiUserStudyEnrollment.changeUserStudyEnrollmentStatus(
                signal.getParticipantId(),
                signal.getStudyId(),
                EnrollmentStatusType.ENROLLED);
        LOG.info("Changed enrollment status for participant {} in study {} to {}",
                signal.getParticipantGuid(), signal.getStudyId(), EnrollmentStatusType.ENROLLED);

        triggerEvents(handle, new UserStatusChangedSignal(
                signal.getOperatorId(),
                signal.getParticipantId(),
                signal.getParticipantGuid(),
                signal.getOperatorGuid(),
                signal.getStudyId(),
                EnrollmentStatusType.ENROLLED));
    }

    @VisibleForTesting
    void triggerEvents(Handle handle, EventSignal signal) {
        EventService.getInstance().processAllActionsForEventSignal(handle, signal);
    }
}

