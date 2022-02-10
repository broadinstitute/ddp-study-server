package org.broadinstitute.ddp.model.event;

import org.broadinstitute.ddp.copy.CopyExecutor;
import org.broadinstitute.ddp.db.dao.CopyConfigurationDao;
import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
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

    public CopyAnswerEventAction(EventConfiguration eventConfiguration, long copyConfigurationId) {
        super(eventConfiguration, null);
        this.copyConfigurationId = copyConfigurationId;
    }

    @Override
    public void doAction(PexInterpreter interpreter, Handle handle, EventSignal signal) {
        CopyConfiguration config = handle.attach(CopyConfigurationDao.class)
                .findCopyConfigById(copyConfigurationId)
                .orElseThrow(() -> new DDPException("Could not find copy configuration with id " + copyConfigurationId));

        var executor = new CopyExecutor();
        if (signal.getEventTriggerType() == EventTriggerType.ACTIVITY_STATUS) {
            var activitySignal = (ActivityInstanceStatusChangeSignal) signal;
            long instanceId = activitySignal.getActivityInstanceIdThatChanged();
            executor.withTriggeredInstanceId(instanceId);
            LOG.info("Using activity instance {} as the triggered instance for copying", instanceId);
        }

        executor.execute(handle, signal.getOperatorId(), signal.getParticipantId(), config);
        LOG.info("Finished executing copy configuration {} for event signal {}", copyConfigurationId, signal);
    }

    public long getCopyConfigurationId() {
        return copyConfigurationId;
    }
}
