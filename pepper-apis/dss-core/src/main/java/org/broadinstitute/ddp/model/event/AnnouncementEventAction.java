package org.broadinstitute.ddp.model.event;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.UserAnnouncementDao;
import org.broadinstitute.ddp.db.dao.UserGovernanceDao;
import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.governance.Governance;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.jdbi.v3.core.Handle;

@Slf4j
public class AnnouncementEventAction extends EventAction {
    private final long messageTemplateId;
    private final boolean isPermanent;
    private final boolean createForProxies;

    public AnnouncementEventAction(EventConfiguration eventConfiguration, EventConfigurationDto dto) {
        super(eventConfiguration, dto);
        messageTemplateId = dto.getAnnouncementMsgTemplateId();
        isPermanent = dto.isAnnouncementPermanent();
        createForProxies = dto.shouldCreateAnnouncementForProxies();
    }

    public long getMessageTemplateId() {
        return messageTemplateId;
    }

    @Override
    public void doAction(PexInterpreter pexInterpreter, Handle handle, EventSignal eventSignal) {
        UserAnnouncementDao announcementDao = handle.attach(UserAnnouncementDao.class);
        long participantId = eventSignal.getParticipantId();
        long studyId = eventSignal.getStudyId();

        try {
            if (createForProxies) {
                List<Governance> governances;
                try (Stream<Governance> governanceStream = handle.attach(UserGovernanceDao.class)
                        .findActiveGovernancesByParticipantAndStudyIds(participantId, studyId)) {
                    governances = governanceStream.collect(Collectors.toList());
                }
                if (!governances.isEmpty()) {
                    for (var governance : governances) {
                        long id = announcementDao.insert(governance.getProxyUserId(), studyId, messageTemplateId, isPermanent);
                        log.info("Created new announcement with id {} for proxy id {} (participant id {}) and study id {}",
                                id, governance.getProxyUserId(), participantId, studyId);
                    }
                } else {
                    log.error("Participant with id {} has no active proxies in study id {}."
                                    + " Announcement with event configuration id {} will not be created.",
                            participantId, studyId, eventConfiguration.getEventConfigurationId());
                }
            } else {
                long id = announcementDao.insert(participantId, studyId, messageTemplateId, isPermanent);
                log.info("Created new announcement with id {} for participant id {} and study id {}", id, participantId, studyId);
            }
        } catch (Exception e) {
            throw new DDPException(String.format(
                    "Error while creating announcement for participant id %d, study id %d, event configuration id %d",
                    participantId, studyId, eventConfiguration.getEventConfigurationId()), e);
        }
    }
}
