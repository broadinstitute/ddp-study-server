package org.broadinstitute.ddp.model.event;

import org.broadinstitute.ddp.copy.CopyExecutor;
import org.broadinstitute.ddp.db.dao.CopyConfigurationDao;
import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.copy.CopyConfiguration;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CopyAnswerEventAction extends EventAction {

    private static final Logger LOG = LoggerFactory.getLogger(CopyAnswerEventAction.class);
    private long copyConfigurationId;

    public CopyAnswerEventAction(EventConfiguration eventConfiguration, EventConfigurationDto dto) {
        super(eventConfiguration, dto);
        copyConfigurationId = dto.getCopyActionCopyConfigurationId();
    }

    @Override
    public void doAction(PexInterpreter interpreter, Handle handle, EventSignal signal) {
        // FIXME The Copy Answer Action was originally written to assume that the activity that triggered the copy
        // FIXME is the source. This means we have to assume that the trigger for this action WAS an activity changing.
        // FIXME Fix so that CopyEventAction is defined in terms of source and destination. OR: change answerdao not to
        // FIXME need the activity ID, since question stable is guaranteed to be unique.

        // JdbiActivityInstance jdbiActivityInstance = handle.attach(JdbiActivityInstance.class);
        // if (signal.getEventTriggerType() != EventTriggerType.ACTIVITY_STATUS) {
        //     throw new DDPException("Currently the copy answer action requires the trigger of the action be activity status");
        // }
        // ActivityInstanceStatusChangeSignal activityInstanceStatusChangeSignal =
        //         (ActivityInstanceStatusChangeSignal) signal;
        // String activityInstanceGuid = jdbiActivityInstance
        //         .getActivityInstanceGuid(activityInstanceStatusChangeSignal.getActivityInstanceIdThatChanged());
        //
        // copyAnswerValue(signal, activityInstanceGuid, handle);

        CopyConfiguration config = handle.attach(CopyConfigurationDao.class)
                .findCopyConfigById(copyConfigurationId)
                .orElseThrow(() -> new DDPException("Could not find copy configuration with id " + copyConfigurationId));
        new CopyExecutor().execute(handle, signal.getOperatorId(), signal.getParticipantId(), config);
    }

    public long getCopyConfigurationId() {
        return copyConfigurationId;
    }
}
