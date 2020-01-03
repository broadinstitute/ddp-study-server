package org.broadinstitute.ddp.model.event;

import org.broadinstitute.ddp.db.dao.UserAnnouncementDao;
import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnnouncementEventAction extends EventAction {
    private static final Logger LOG = LoggerFactory.getLogger(EventAction.class);
    private long messageTemplateId;

    public AnnouncementEventAction(EventConfiguration eventConfiguration, EventConfigurationDto dto) {
        super(eventConfiguration, dto);
        messageTemplateId = dto.getMessageTemplateId();
    }

    @Override
    public void doAction(PexInterpreter pexInterpreter, Handle handle, EventSignal eventSignal) {
        UserAnnouncementDao userAnnouncementDao = handle.attach(UserAnnouncementDao.class);
        long id = userAnnouncementDao.insert(
                eventSignal.getParticipantId(),
                eventSignal.getStudyId(),
                messageTemplateId);
        LOG.info("Inserted new announcement with id {} for participant id {} and study id {}",
                id,
                eventSignal.getParticipantId(),
                eventSignal.getStudyId());
    }
}

