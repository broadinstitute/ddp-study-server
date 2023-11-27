package org.broadinstitute.ddp.model.event;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.UserGovernanceDao;
import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.jdbi.v3.core.Handle;

/**
 * An action that disables all proxies for the participant and study so that they are no longer active,
 * and effectively becomes "former proxies".
 */
@Slf4j
public class RevokeProxiesEventAction extends EventAction {
    public RevokeProxiesEventAction(EventConfiguration eventConfiguration, EventConfigurationDto dto) {
        super(eventConfiguration, dto);
    }

    @Override
    public void doAction(PexInterpreter interpreter, Handle handle, EventSignal signal) {
        int numUpdated = handle.attach(UserGovernanceDao.class)
                .disableActiveProxies(signal.getParticipantId(), signal.getStudyId());
        log.info("Disabled {} proxies for participant {} in study {}",
                numUpdated, signal.getParticipantGuid(), signal.getStudyId());
    }
}
