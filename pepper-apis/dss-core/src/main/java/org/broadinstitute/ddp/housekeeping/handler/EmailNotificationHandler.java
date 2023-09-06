package org.broadinstitute.ddp.housekeeping.handler;

import static org.broadinstitute.ddp.constants.NotificationTemplateVariables.BASE_WEB_URL;
import static org.broadinstitute.ddp.constants.NotificationTemplateVariables.DDP_BASE_WEB_URL;
import static org.broadinstitute.ddp.constants.NotificationTemplateVariables.DDP_PARTICIPANT_FIRST_NAME;
import static org.broadinstitute.ddp.constants.NotificationTemplateVariables.DDP_PARTICIPANT_GUID;
import static org.broadinstitute.ddp.constants.NotificationTemplateVariables.DDP_PARTICIPANT_LAST_NAME;
import static org.broadinstitute.ddp.constants.NotificationTemplateVariables.DDP_SALUTATION;
import static org.broadinstitute.ddp.constants.NotificationTemplateVariables.DDP_STUDY_GUID;
import static org.broadinstitute.ddp.constants.NotificationTemplateVariables.PARTICIPANT_FIRST_NAME;
import static org.broadinstitute.ddp.constants.NotificationTemplateVariables.PARTICIPANT_GUID;
import static org.broadinstitute.ddp.constants.NotificationTemplateVariables.PARTICIPANT_LAST_NAME;
import static org.broadinstitute.ddp.constants.NotificationTemplateVariables.SALUTATION;
import static org.broadinstitute.ddp.constants.NotificationTemplateVariables.STUDY_GUID;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sendgrid.helpers.mail.objects.Attachments;
import com.sendgrid.helpers.mail.objects.Email;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Personalization;
import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.client.SendGridClient;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.db.dto.NotificationTemplateSubstitutionDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.housekeeping.message.HousekeepingMessageHandler;
import org.broadinstitute.ddp.housekeeping.message.NotificationMessage;
import org.broadinstitute.ddp.model.event.NotificationType;
import org.broadinstitute.ddp.model.event.PdfAttachment;
import org.broadinstitute.ddp.model.pdf.PdfConfiguration;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.broadinstitute.ddp.monitoring.PointsReducerFactory;
import org.broadinstitute.ddp.monitoring.StackdriverCustomMetric;
import org.broadinstitute.ddp.monitoring.StackdriverMetricsTracker;
import org.broadinstitute.ddp.service.PdfBucketService;
import org.broadinstitute.ddp.service.PdfGenerationService;
import org.broadinstitute.ddp.service.PdfService;
import org.broadinstitute.ddp.util.ConfigManager;
import org.jdbi.v3.core.Handle;

@Slf4j
public class EmailNotificationHandler implements HousekeepingMessageHandler<NotificationMessage> {
    private final SendGridClient sendGrid;
    private final PdfService pdfService;
    private final PdfBucketService pdfBucketService;
    private final PdfGenerationService pdfGenerationService;
    private List<String> emailDenyPatterns;

    static String generateSalutation(String firstName, String lastName, String defaultSalutation) {
        if (StringUtils.isNotEmpty(firstName) && StringUtils.isNotEmpty(lastName)) {
            return String.format("Dear %s %s,", firstName, lastName);
        } else {
            return defaultSalutation;
        }
    }

    public EmailNotificationHandler(SendGridClient sendGrid, PdfService pdfService,
                                    PdfBucketService pdfBucketService, PdfGenerationService pdfGenerationService) {
        this.sendGrid = sendGrid;
        this.pdfService = pdfService;
        this.pdfBucketService = pdfBucketService;
        this.pdfGenerationService = pdfGenerationService;
        Config cfg = ConfigManager.getInstance().getConfig();
        this.emailDenyPatterns =  cfg.hasPath(ConfigFile.EMAIL_PATTERN_DENY_LIST) ?
                cfg.getStringList(ConfigFile.EMAIL_PATTERN_DENY_LIST) : Collections.EMPTY_LIST;
    }

    /**
     * Sends the given template to the list of addresses
     */
    @Override
    public void handleMessage(NotificationMessage message) {
        String studyGuid = message.getStudyGuid();
        String participantGuid = message.getParticipantGuid();
        boolean hasParticipantGuid = StringUtils.isNotBlank(participantGuid);
        if (hasParticipantGuid && message.getNotificationType() == NotificationType.EMAIL) {
            boolean shouldIgnore = TransactionWrapper.withTxn(TransactionWrapper.DB.APIS,
                    apisHandle -> messageShouldBeIgnored(apisHandle, studyGuid, participantGuid));
            if (shouldIgnore) {
                return;
            }
        }

        //skip emails for PlayWright generated users / Email deny list
        if (!emailDenyPatterns.isEmpty()) {
            boolean hasDenyListEmail = false;
            List<String> nonDenyEmailList = new ArrayList<>();
            for (String toAddress : message.getDistributionList()) {
                boolean isDenyEmail = false;
                Email toEmail = new Email(toAddress, toAddress);
                for (String emailPattern : emailDenyPatterns) {
                    Pattern pattern = Pattern.compile(emailPattern);
                    Matcher matcher = pattern.matcher(toEmail.getEmail());
                    if (matcher.find()) {
                        isDenyEmail = true;
                        hasDenyListEmail = true; //to handle email DL with a PW test user and other emails
                        break;
                    }
                }
                if (!isDenyEmail) {
                    nonDenyEmailList.add(toEmail.getEmail());
                }
            }
            if (hasDenyListEmail && nonDenyEmailList.isEmpty()) {
                log.debug("Skipping sending email to PW user/Deny email :: {} ", message.getDistributionList());
                return;
            }
        }

        String templateId = message.getTemplateKey();
        Email fromEmail = new Email(message.getFromEmail(), message.getFromName());

        Mail mail = new Mail();
        mail.setFrom(fromEmail);
        mail.setTemplateId(templateId);

        boolean skippingPdfs = false;
        boolean hasPdfConfiguration = false;
        if (hasParticipantGuid) {
            List<Attachments> attachments = TransactionWrapper.withTxn(TransactionWrapper.DB.APIS, apisHandle ->
                    buildAttachments(apisHandle, studyGuid, participantGuid, message.getEventConfigurationId()));
            if (!attachments.isEmpty()) {
                attachments.forEach(mail::addAttachments);
                hasPdfConfiguration = true;
            }
        } else {
            skippingPdfs = true;
        }

        Personalization personalization = new Personalization();
        for (String toAddress : message.getDistributionList()) {
            Email toEmail = new Email(toAddress, toAddress);
            personalization.addTo(toEmail);
            if (skippingPdfs && hasPdfConfiguration) {
                log.warn("Skipping PDF configuration for {} since PDF substitution can't happen without a participant.",
                        toAddress);
            }
        }

        if (message.isDynamicTemplate()) {
            //add dynamic data
            Map<String, Object> dynamicData = getDynamicData(message);
            dynamicData.forEach(personalization::addDynamicTemplateData);
        } else {
            //legacy template
            buildSubstitutions(message).forEach(personalization::addSubstitution);
        }
        mail.addPersonalization(personalization);

        var versionResult = sendGrid.getTemplateActiveVersionId(templateId);
        if (versionResult.hasThrown() || versionResult.getStatusCode() != 200) {
            String msg = String.format("[%s] error looking up version of template '%s': %s",
                    versionResult.getStatusCode(), templateId, versionResult.getError());
            if (versionResult.hasThrown()) {
                throw new MessageHandlingException(msg, versionResult.getThrown(), true);
            } else {
                throw new MessageHandlingException(msg, true);
            }
        }

        String versionUsed = versionResult.getBody();
        String distributionList = StringUtils.join(message.getDistributionList(), " ");

        var sendResult = sendGrid.sendMail(mail);

        // should retry since the send call failed
        sendResult.rethrowIfThrown(e -> new MessageHandlingException(
                "Error sending template " + templateId + " to " + distributionList, e, true));

        if (sendResult.getStatusCode() == 200 || sendResult.getStatusCode() == 202) {
            log.info("Sent template {} version {} to {}", templateId, versionUsed, distributionList);
            new StackdriverMetricsTracker(StackdriverCustomMetric.EMAILS_SENT, studyGuid,
                    PointsReducerFactory.buildSumReducer()).addPoint(1, Instant.now().toEpochMilli());
        } else {
            throw new MessageHandlingException("Attempt to send template " + templateId + " to " + distributionList
                    + " failed with " + sendResult.getStatusCode() + ":" + sendResult.getError(), true);
        }
    }

    Map<String, Object> getDynamicData(NotificationMessage message) {
        Map<String, Object> dynamicData = new HashMap<>();

        String salutation = generateSalutation(
                message.getParticipantFirstName(),
                message.getParticipantLastName(),
                message.getDefaultSalutation());

        dynamicData.put(SALUTATION, salutation);
        dynamicData.put(BASE_WEB_URL, message.getWebBaseUrl());
        dynamicData.put(STUDY_GUID, message.getStudyGuid());
        dynamicData.put(PARTICIPANT_GUID, message.getParticipantGuid());
        dynamicData.put(PARTICIPANT_FIRST_NAME, message.getParticipantFirstName());
        dynamicData.put(PARTICIPANT_LAST_NAME, message.getParticipantLastName());

        for (NotificationTemplateSubstitutionDto sub : message.getTemplateSubstitutions()) {
            //hack to translate use current (legacy) substitutions from variables like "-ddp.abc.xyz-" to "abc_xyz" in dynamic templates
            String variableName = sub.getVariableName();
            if (variableName.startsWith("-")) {
                variableName = variableName.substring(1);
            }
            if (variableName.startsWith("ddp.")) {
                variableName = variableName.substring(4);
            }
            if (variableName.contains(".")) {
                variableName = variableName.replace(".", "_");
            }
            if (variableName.endsWith("-")) {
                variableName = variableName.substring(0, variableName.length() - 1);
            }
            dynamicData.put(variableName, sub.getValue());
        }

        return dynamicData;
    }


    /**
     * Checks if the notification message should be ignored. This can be the case when a user exited the study or
     * explicitly mentioned that they do not want to receive notifications.
     *
     * @param apisHandle      the database handle
     * @param studyGuid       the study guid
     * @param participantGuid the participant guid
     * @return true if message should be ignored, otherwise false
     */
    boolean messageShouldBeIgnored(Handle apisHandle, String studyGuid, String participantGuid) {
        EnrollmentStatusType status = apisHandle.attach(JdbiUserStudyEnrollment.class)
                .getEnrollmentStatusByUserAndStudyGuids(participantGuid, studyGuid)
                .orElse(null);
        if (status != null && !status.shouldReceiveCommunications()) {
            log.info("The participant {} in study {} should not receive communications because of status {}",
                    participantGuid, studyGuid, status);
            return true;
        }

        boolean doNotContact = apisHandle.attach(UserProfileDao.class)
                .findProfileByUserGuid(participantGuid)
                .map(UserProfile::getDoNotContact)
                .orElse(false);
        if (doNotContact) {
            log.info("The participant {} elected not to receive notifications, so nothing will be sent", participantGuid);
            return true;
        }

        return false;
    }

    // General approach to personalizations: we add them for every template, but the template
    // might not make use of them. So they're kind of special/global/magic reserved words in our templates.
    Map<String, String> buildSubstitutions(NotificationMessage message) {
        Map<String, String> subs = new HashMap<>();

        subs.put(DDP_SALUTATION,
                generateSalutation(
                        message.getParticipantFirstName(),
                        message.getParticipantLastName(),
                        message.getDefaultSalutation()
                ));

        subs.put(DDP_BASE_WEB_URL, message.getWebBaseUrl());
        subs.put(DDP_STUDY_GUID, message.getStudyGuid());
        subs.put(DDP_PARTICIPANT_GUID, message.getParticipantGuid());
        subs.put(DDP_PARTICIPANT_FIRST_NAME, message.getParticipantFirstName());
        subs.put(DDP_PARTICIPANT_LAST_NAME, message.getParticipantLastName());

        for (NotificationTemplateSubstitutionDto sub : message.getTemplateSubstitutions()) {
            subs.put(sub.getVariableName(), sub.getValue());
        }

        return subs;
    }

    List<Attachments> buildAttachments(Handle apisHandle, String studyGuid, String participantGuid, long eventConfigId) {
        String umbrellaGuid = apisHandle.attach(JdbiUmbrellaStudy.class)
                .getUmbrellaGuidForStudyGuid(studyGuid);
        List<PdfAttachment> pdfAttachments = apisHandle.attach(EventDao.class)
                .getPdfAttachmentsForEvent(eventConfigId);

        List<Attachments> attachments = new ArrayList<>();

        for (PdfAttachment pdfAttachment : pdfAttachments) {
            long pdfConfigId = pdfAttachment.getPdfConfigId();
            PdfConfiguration pdfConfig = pdfService.findFullConfigForUser(apisHandle, pdfConfigId, participantGuid, studyGuid);

            String blobName = PdfBucketService.getBlobName(umbrellaGuid,
                    studyGuid,
                    participantGuid,
                    pdfConfig.getConfigName(),
                    pdfConfig.getVersion().getVersionTag());
            InputStream pdfStream =  null;
            try {
                pdfStream = pdfAttachment.shouldAlwaysGenerate() ? null : pdfBucketService.getPdfFromBucket(blobName).orElse(null);
                if (pdfStream == null) {
                    if (!pdfAttachment.shouldAlwaysGenerate()) {
                        log.info("Could not find {} in bucket {}, generating", blobName, pdfBucketService.getBucketName());
                    }
                    pdfStream = pdfGenerationService.generateFlattenedPdfForConfiguration(
                            pdfConfig,
                            participantGuid,
                            apisHandle);
                    pdfBucketService.sendPdfToBucket(blobName, pdfStream);
                    log.info("Uploaded pdf to bucket {} with filename {}", pdfBucketService.getBucketName(), blobName);
                    pdfStream = pdfBucketService.getPdfFromBucket(blobName).orElse(null);
                }
                String name = pdfConfig.getFilename() + ".pdf";
                // Implementation of newPdfAttachment reads stream and saves locally as string
                // we can close stream when done
                attachments.add(SendGridClient.newPdfAttachment(name, pdfStream));
            } catch (IOException | DDPException e) {
                throw new MessageHandlingException("Error generating or retrieving PDF from bucket "
                        + pdfBucketService.getBucketName() + " for blob name " + blobName, e, true);
            } finally {
                if (pdfStream != null) {
                    try {
                        pdfStream.close();
                    } catch (IOException e) {
                        log.warn("Could not close stream", e);
                    }
                }
            }
        }

        return attachments;
    }
}
