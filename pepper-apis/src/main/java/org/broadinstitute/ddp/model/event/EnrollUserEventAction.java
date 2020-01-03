package org.broadinstitute.ddp.model.event;

import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.jdbi.v3.core.Handle;

public class EnrollUserEventAction extends EventAction {
    public EnrollUserEventAction(EventConfiguration eventConfiguration, EventConfigurationDto dto) {
        super(eventConfiguration, dto);
    }

    @Override
    public void doAction(PexInterpreter pexInterpreter, Handle handle, EventSignal eventSignal) {
        JdbiUserStudyEnrollment jdbiUserStudyEnrollment = handle.attach(JdbiUserStudyEnrollment.class);

        jdbiUserStudyEnrollment.changeUserStudyEnrollmentStatus(
                eventSignal.getParticipantId(),
                eventSignal.getStudyId(),
                EnrollmentStatusType.ENROLLED);
    }
}

