package org.broadinstitute.ddp.model.event;

import java.time.Instant;
import java.util.HashMap;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.QueuedEventDao;
import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.jdbi.v3.core.Handle;

@Slf4j
@Getter
public class PdfGenerationEventAction extends EventAction {
    private final Long pdfGenerationDocumentConfigurationId;

    public PdfGenerationEventAction(EventConfiguration eventConfiguration, EventConfigurationDto dto) {
        super(eventConfiguration, dto);
        pdfGenerationDocumentConfigurationId = dto.getPdfGenerationDocumentConfigurationId();
    }

    @Override
    public void doAction(PexInterpreter pexInterpreter, Handle handle, EventSignal eventSignal) {
        if (!eventConfiguration.dispatchToHousekeeping()) {
            throw new DDPException("PdfGenerationEventActions are currently only supported as asynchronous events."
                    + "Please set dispatch_to_housekeeping to true");
        }
        QueuedEventDao queuedEventDao = handle.attach(QueuedEventDao.class);
        Integer delayBeforePosting = eventConfiguration.getPostDelaySeconds();
        if (delayBeforePosting == null) {
            delayBeforePosting = 0;
        }
        long postAfter = Instant.now().getEpochSecond() + delayBeforePosting;

        Long queuedEventId = queuedEventDao.insertNotification(eventConfiguration.getEventConfigurationId(),
                postAfter,
                eventSignal.getParticipantId(),
                eventSignal.getOperatorId(),
                new HashMap<>());

        log.info("Inserted queued event {} for configuration {}", queuedEventId,
                eventConfiguration.getEventConfigurationId());
    }
}
