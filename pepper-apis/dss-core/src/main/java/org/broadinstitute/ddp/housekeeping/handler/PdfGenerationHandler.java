package org.broadinstitute.ddp.housekeeping.handler;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.housekeeping.message.HousekeepingMessageHandler;
import org.broadinstitute.ddp.housekeeping.message.PdfGenerationMessage;
import org.broadinstitute.ddp.model.pdf.PdfConfiguration;
import org.broadinstitute.ddp.service.PdfBucketService;
import org.broadinstitute.ddp.service.PdfGenerationService;
import org.broadinstitute.ddp.service.PdfService;

@Slf4j
@AllArgsConstructor
public class PdfGenerationHandler implements HousekeepingMessageHandler<PdfGenerationMessage> {
    private final PdfService pdfService;
    private final PdfBucketService pdfBucketService;
    private final PdfGenerationService pdfGenerationService;

    @Override
    public void handleMessage(PdfGenerationMessage pdfGenerationMessage) {
        String participantGuid = pdfGenerationMessage.getParticipantGuid();
        if (StringUtils.isNotBlank(participantGuid)) {
            TransactionWrapper.useTxn(TransactionWrapper.DB.APIS, apisHandle -> {
                String studyGuid = pdfGenerationMessage.getStudyGuid();
                long configId = pdfGenerationMessage.getPdfDocumentConfigurationId();
                try {
                    PdfConfiguration pdfConfig = pdfService.findFullConfigForUser(apisHandle, configId, participantGuid, studyGuid);
                    String uploadedFilename = pdfService.generateAndUpload(
                            apisHandle,
                            pdfGenerationService,
                            pdfBucketService,
                            pdfConfig,
                            participantGuid,
                            studyGuid);
                    log.info("Uploaded pdf to bucket {} with filename {}", pdfBucketService.getBucketName(), uploadedFilename);
                } catch (Exception e) {
                    throw new MessageHandlingException("Error generating or uploading PDF with id=" + configId
                            + " for user " + participantGuid, e, true);
                }
            });
        } else {
            log.error("No participant guid found in pdf generation message, skipping");
        }
    }
}
