package org.broadinstitute.ddp.model.event;

import org.apache.commons.lang.NotImplementedException;
import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.jdbi.v3.core.Handle;

public class PdfGenerationEventAction extends EventAction {
    Long pdfDocumentConfigurationId;
    Boolean generateIfMissing;

    public PdfGenerationEventAction(EventConfiguration eventConfiguration, EventConfigurationDto dto) {
        super(eventConfiguration, dto);
        pdfDocumentConfigurationId = dto.getPdfDocumentConfigurationId();
        generateIfMissing = dto.getGenerateIfMissing();
    }

    @Override
    public void doAction(PexInterpreter pexInterpreter, Handle handle, EventSignal eventSignal) {
        throw new NotImplementedException("Not implemented until DDP-4294");
    }
}
