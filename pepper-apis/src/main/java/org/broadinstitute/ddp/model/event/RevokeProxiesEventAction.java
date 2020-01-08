package org.broadinstitute.ddp.model.event;

import org.broadinstitute.ddp.db.dao.UserGovernanceDao;
import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An action that disables all proxies for the participant and study so that they are no longer active,
 * and effectively becomes "former proxies".
 */
public class RevokeProxiesEventAction extends EventAction {

    private static final Logger LOG = LoggerFactory.getLogger(RevokeProxiesEventAction.class);

    public RevokeProxiesEventAction(EventConfiguration eventConfiguration, EventConfigurationDto dto) {
        super(eventConfiguration, dto);
    }

    @Override
    public void doAction(PexInterpreter interpreter, Handle handle, EventSignal signal) {
        int numUpdated = handle.attach(UserGovernanceDao.class)
                .disableActiveProxies(signal.getParticipantId(), signal.getStudyId());
        LOG.info("Disabled {} proxies for participant {} in study {}",
                numUpdated, signal.getParticipantGuid(), signal.getStudyId());
    }
}
