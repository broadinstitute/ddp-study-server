package org.broadinstitute.ddp.model.event;

import java.time.Instant;
import java.util.HashMap;

import org.broadinstitute.ddp.db.dao.QueuedEventDao;
import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PdfGenerationEventAction extends EventAction {
    private static final Logger LOG = LoggerFactory.getLogger(PdfGenerationEventAction.class);

    private Long pdfGenerationDocumentConfigurationId;

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
                new HashMap<>(),
                null);

        LOG.info("Inserted queued event {} for configuration {}", queuedEventId,
                eventConfiguration.getEventConfigurationId());
    }

    public Long getPdfGenerationDocumentConfigurationId() {
        return pdfGenerationDocumentConfigurationId;
    }
}
