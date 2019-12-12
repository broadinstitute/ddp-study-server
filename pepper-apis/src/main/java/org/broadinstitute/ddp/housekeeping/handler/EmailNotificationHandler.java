package org.broadinstitute.ddp.housekeeping.handler;

import static org.broadinstitute.ddp.constants.NotificationTemplateVariables.DDP_BASE_WEB_URL;
import static org.broadinstitute.ddp.constants.NotificationTemplateVariables.DDP_PARTICIPANT_FIRST_NAME;
import static org.broadinstitute.ddp.constants.NotificationTemplateVariables.DDP_PARTICIPANT_GUID;
import static org.broadinstitute.ddp.constants.NotificationTemplateVariables.DDP_PARTICIPANT_LAST_NAME;
import static org.broadinstitute.ddp.constants.NotificationTemplateVariables.DDP_SALUTATION;
import static org.broadinstitute.ddp.constants.NotificationTemplateVariables.DDP_STUDY_GUID;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.sendgrid.Attachments;
import com.sendgrid.Email;
import com.sendgrid.Mail;
import com.sendgrid.Method;
import com.sendgrid.Personalization;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiProfile;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dto.NotificationTemplateSubstitutionDto;
import org.broadinstitute.ddp.db.dto.UserProfileDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.housekeeping.message.HousekeepingMessageHandler;
import org.broadinstitute.ddp.housekeeping.message.NotificationMessage;
import org.broadinstitute.ddp.model.event.NotificationType;
import org.broadinstitute.ddp.model.event.PdfAttachment;
import org.broadinstitute.ddp.model.pdf.PdfConfiguration;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.monitoring.PointsReducerFactory;
import org.broadinstitute.ddp.monitoring.StackdriverCustomMetric;
import org.broadinstitute.ddp.monitoring.StackdriverMetricsTracker;
import org.broadinstitute.ddp.service.PdfBucketService;
import org.broadinstitute.ddp.service.PdfGenerationService;
import org.broadinstitute.ddp.service.PdfService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmailNotificationHandler implements HousekeepingMessageHandler<NotificationMessage> {

    private static final Logger LOG = LoggerFactory.getLogger(EmailNotificationHandler.class);

    // These are constants in sendgrid. Do not alter.
    private static final String ATTACHMENT_DISPOSITION = "attachment";
    private static final String ATTACHMENT_PDF_TYPE = "application/pdf";

    private final SendGrid sendGrid;
    private final PdfService pdfService;
    private final PdfBucketService pdfBucketService;
    private final PdfGenerationService pdfGenerationService;

    public EmailNotificationHandler(SendGrid sendGrid, PdfService pdfService,
                                    PdfBucketService pdfBucketService, PdfGenerationService pdfGenerationService) {
        this.sendGrid = sendGrid;
        this.pdfService = pdfService;
        this.pdfBucketService = pdfBucketService;
        this.pdfGenerationService = pdfGenerationService;
    }

    /**
     * Find the currently active version of the template via sendgrid's API
     */
    private String getTemplateVersion(SendGrid sg, String templateId) {
        Request templatesRequest = new Request();
        templatesRequest.setMethod(Method.GET);
        templatesRequest.setEndpoint("templates/" + templateId);
        Response templatesResponse = null;
        try {
            templatesResponse = sg.api(templatesRequest);
        } catch (IOException e) {
            throw new MessageHandlingException("Error looking up version of template " + templateId, e, true);
        }

        JsonElement templates = new Gson().fromJson(templatesResponse.getBody(), new TypeToken<JsonObject>() {
        }.getType());
        String versionUsed = null;
        for (JsonElement templateVersion : templates.getAsJsonObject().get("versions").getAsJsonArray()) {
            Number versionValue = templateVersion.getAsJsonObject().get("active").getAsNumber().intValue();
            if (versionValue.intValue() == 1) {
                versionUsed = templateVersion.getAsJsonObject().get("id").getAsString();
            }
        }
        return versionUsed;
    }

    /**
     * Sends the given template to the list of addresses
     */
    @Override
    public void handleMessage(NotificationMessage notificationMessage) {
        String participantGuid = notificationMessage.getParticipantGuid();

        boolean hasParticipantGuid = StringUtils.isNotBlank(participantGuid);
        if (hasParticipantGuid
                && notificationMessage.getNotificationType() == NotificationType.EMAIL
                && messageShouldBeIgnoredForParticipant(notificationMessage)) {
            return;
        }

        Mail mail = new Mail();
        String templateId = notificationMessage.getTemplateKey();

        Email fromEmail = new Email();
        fromEmail.setName(notificationMessage.getFromName());
        fromEmail.setEmail(notificationMessage.getFromEmail());

        String studyGuid = notificationMessage.getStudyGuid();

        boolean skippingPdfs = false;
        boolean hasPdfConfiguration = false;
        if (hasParticipantGuid) {
            hasPdfConfiguration = TransactionWrapper.withTxn(TransactionWrapper.DB.APIS, apisHandle -> {
                String umbrellaGuid = apisHandle.attach(JdbiUmbrellaStudy.class)
                        .getUmbrellaGuidForStudyGuid(studyGuid);

                List<PdfAttachment> pdfAttachments = apisHandle.attach(EventDao.class)
                        .getPdfAttachmentsForEvent(notificationMessage.getEventConfigurationId());

                for (PdfAttachment pdfAttachment : pdfAttachments) {
                    long pdfConfigId = pdfAttachment.getPdfConfigId();
                    PdfConfiguration pdfConfig = pdfService.findFullConfigForUser(apisHandle, pdfConfigId, participantGuid, studyGuid);
                    String blobName = PdfBucketService.getBlobName(umbrellaGuid,
                            studyGuid,
                            participantGuid,
                            pdfConfig.getConfigName(),
                            pdfConfig.getVersion().getVersionTag());

                    try {
                        InputStream pdfStream = pdfBucketService.getPdfFromBucket(blobName).orElse(null);
                        if (pdfStream == null) {
                            // todo: remove the generateIfMissing feature
                            LOG.info("Could not find {} in bucket {}, generating", blobName, pdfBucketService.getBucketName());
                            byte[] pdfBytes = pdfGenerationService.generateFlattenedPdfForConfiguration(
                                    pdfConfig,
                                    participantGuid,
                                    apisHandle);
                            pdfStream = new ByteArrayInputStream(pdfBytes);
                            pdfBucketService.sendPdfToBucket(blobName, new ByteArrayInputStream(pdfBytes));
                            LOG.info("Uploaded pdf to bucket {} with filename {}", pdfBucketService.getBucketName(), blobName);
                        }
                        Attachments attachment = new Attachments.Builder(pdfConfig.getFilename() + ".pdf", pdfStream)
                                .withDisposition(ATTACHMENT_DISPOSITION)
                                .withType(ATTACHMENT_PDF_TYPE)
                                .build();
                        mail.addAttachments(attachment);
                    } catch (IOException | DDPException e) {
                        throw new MessageHandlingException("Error generating or retrieving PDF from bucket "
                                + pdfBucketService.getBucketName() + " for blob name " + blobName, e, true);
                    }
                }

                return !pdfAttachments.isEmpty();
            });
        } else {
            skippingPdfs = true;
        }

        Personalization personalization = new Personalization();
        for (String toAddress : notificationMessage.getDistributionList()) {
            Email to = new Email();
            to.setName(toAddress);
            to.setEmail(toAddress);
            personalization.addTo(to);
            if (skippingPdfs && hasPdfConfiguration) {
                LOG.warn("Skipping PDF configuration for {} since PDF substitution can't happen without a participant.",
                        toAddress);
            }
        }

        // general approach to personalizations: we add them for every template, but the template
        // might not make use of them.  So they're kind of special/global/magic reserved words in our templates
        personalization.addSubstitution(DDP_SALUTATION,
                generateSalutation(
                        notificationMessage.getParticipantFirstName(),
                        notificationMessage.getParticipantLastName(),
                        notificationMessage.getDefaultSalutation()
                ));
        personalization.addSubstitution(DDP_BASE_WEB_URL, notificationMessage.getWebBaseUrl());
        personalization.addSubstitution(DDP_STUDY_GUID, studyGuid);
        personalization.addSubstitution(DDP_PARTICIPANT_GUID, participantGuid);
        personalization.addSubstitution(DDP_PARTICIPANT_FIRST_NAME, notificationMessage.getParticipantFirstName());
        personalization.addSubstitution(DDP_PARTICIPANT_LAST_NAME, notificationMessage.getParticipantLastName());

        for (NotificationTemplateSubstitutionDto sub : notificationMessage.getTemplateSubstitutions()) {
            personalization.addSubstitution(sub.getVariableName(), sub.getValue());
        }

        mail.setFrom(fromEmail);
        mail.setTemplateId(templateId);
        mail.addPersonalization(personalization);

        Request request = new Request();
        request.setMethod(Method.POST);
        request.setEndpoint("mail/send");
        Response emailSendResponse = null;
        String versionUsed = getTemplateVersion(sendGrid, templateId);
        try {
            request.setBody(mail.build());
            emailSendResponse = sendGrid.api(request);
        } catch (IOException e) {
            // should retry since the send call failed
            throw new MessageHandlingException("Error sending template " + templateId + " to " + StringUtils.join(
                    notificationMessage.getDistributionList(), " "), e, true);
        }

        if (!(
                emailSendResponse.getStatusCode() == HttpStatus.SC_ACCEPTED
                        || emailSendResponse.getStatusCode() == HttpStatus.SC_OK)) {
            throw new MessageHandlingException("Attempt to send template " + templateId + " to " + StringUtils.join(
                    notificationMessage.getDistributionList().toArray(new String[] {}))
                    + " failed with " + emailSendResponse.getStatusCode()
                    + ":" + emailSendResponse.getBody(), true);
        } else {
            LOG.info("Sent template {} version {} to {}", templateId, versionUsed, StringUtils.join(
                    notificationMessage.getDistributionList(), " "));

            new StackdriverMetricsTracker(StackdriverCustomMetric.EMAILS_SENT,
                    studyGuid,
                    PointsReducerFactory.buildSumReducer()).addPoint(1, Instant.now().toEpochMilli());
        }
    }

    public static String generateSalutation(String firstName, String lastName, String defaultSalutation) {
        if (StringUtils.isNotEmpty(firstName)
                && StringUtils.isNotEmpty(lastName)) {
            return String.format("Dear %s %s,", firstName, lastName);
        } else {
            return defaultSalutation;
        }
    }

    /**
     * Checks if a the notification message should be ignored. This can be the case when a user
     * exited the study or explicitly mentioned that he/she doesn't want to receive notifications
     *
     * @param notificationMessage A message to get the user/study information from
     * @return A boolean value indicating whether the message should be ignored
     */
    private static boolean messageShouldBeIgnoredForParticipant(NotificationMessage notificationMessage) {
        Optional<EnrollmentStatusType> status = TransactionWrapper.withTxn(
                TransactionWrapper.DB.APIS,
                handle -> {
                    return handle.attach(JdbiUserStudyEnrollment.class).getEnrollmentStatusByUserAndStudyGuids(
                            notificationMessage.getParticipantGuid(),
                            notificationMessage.getStudyGuid()
                    );
                }
        );
        if (status.isPresent() && !status.get().shouldReceiveCommunications()) {
            LOG.info(
                    "The participant {} in study {} should not receive communications because of status {}",
                    notificationMessage.getParticipantGuid(),
                    notificationMessage.getStudyGuid(),
                    status.get()
            );
            return true;
        }
        UserProfileDto profile = TransactionWrapper.withTxn(
                TransactionWrapper.DB.APIS,
                handle -> handle.attach(JdbiProfile.class).getUserProfileByUserGuid(
                        notificationMessage.getParticipantGuid()
                )
        );
        Optional<Boolean> doNotContact = Optional.ofNullable(profile == null ? null : profile.getDoNotContact());
        if (doNotContact.orElse(false)) {
            LOG.info(
                    "The participant {} elected not to receive notifications, so nothing will be sent",
                    notificationMessage.getParticipantGuid()
            );
            return true;
        }
        return false;
    }
}
