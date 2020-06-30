package org.broadinstitute.ddp.housekeeping;

import static org.broadinstitute.ddp.Housekeeping.DDP_EVENT_CONFIGURATION_ID;
import static org.broadinstitute.ddp.Housekeeping.DDP_EVENT_ID;
import static org.broadinstitute.ddp.Housekeeping.DDP_EVENT_TYPE;
import static org.broadinstitute.ddp.Housekeeping.DDP_HOUSEKEEPING_VERSION;
import static org.broadinstitute.ddp.Housekeeping.DDP_IGNORE_AFTER;
import static org.broadinstitute.ddp.Housekeeping.DDP_MESSAGE_ID;
import static org.broadinstitute.ddp.Housekeeping.DDP_STUDY_GUID;
import static org.broadinstitute.ddp.constants.NotificationTemplateVariables.DDP_PROXY_FIRST_NAME;
import static org.broadinstitute.ddp.constants.NotificationTemplateVariables.DDP_PROXY_LAST_NAME;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import com.auth0.exception.Auth0Exception;
import com.auth0.json.mgmt.users.User;
import com.google.gson.Gson;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.typesafe.config.Config;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.constants.NotificationTemplateVariables;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dao.StudyLanguageDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dao.UserGovernanceDao;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.db.dto.LanguageDto;
import org.broadinstitute.ddp.db.dto.NotificationTemplateSubstitutionDto;
import org.broadinstitute.ddp.db.dto.QueuedEventDto;
import org.broadinstitute.ddp.db.dto.QueuedNotificationDto;
import org.broadinstitute.ddp.db.dto.QueuedPdfGenerationDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.exception.MessageBuilderException;
import org.broadinstitute.ddp.exception.NoSendableEmailException;
import org.broadinstitute.ddp.housekeeping.message.NotificationMessage;
import org.broadinstitute.ddp.housekeeping.message.PdfGenerationMessage;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.event.NotificationTemplate;
import org.broadinstitute.ddp.model.event.NotificationType;
import org.broadinstitute.ddp.model.governance.Governance;
import org.broadinstitute.ddp.model.study.StudyLanguage;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.broadinstitute.ddp.util.Auth0Util;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PubSubMessageBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(PubSubMessageBuilder.class);

    private Gson gson;
    private Config cfg;

    public PubSubMessageBuilder(Config cfg) {
        this.gson = GsonUtil.standardGson();
        this.cfg = cfg;
    }

    public PubsubMessage createMessage(String ddpMessageId,
                                       QueuedEventDto pendingEvent,
                                       Handle apisHandle) throws MessageBuilderException {
        if (StringUtils.isBlank(ddpMessageId)) {
            throw new RuntimeException("ddpMessageId is required");
        }
        String messageJson = null;
        String participantGuid = pendingEvent.getParticipantGuid();
        String studyGuid = pendingEvent.getStudyGuid();
        if (pendingEvent.getActionType() == EventActionType.NOTIFICATION) {
            QueuedNotificationDto queuedNotificationDto = (QueuedNotificationDto) pendingEvent;
            if (NotificationType.EMAIL == queuedNotificationDto.getNotificationType()
                    || NotificationType.STUDY_EMAIL == queuedNotificationDto.getNotificationType()
                    || NotificationType.INVITATION_EMAIL == queuedNotificationDto.getNotificationType()) {
                Collection<String> sendToList = new HashSet<>();

                // todo techdebt: set email field in QueuedEventDao instead of querying it here. make a list of
                // size one for now.

                String userPreferredLangCode = null;
                if (NotificationType.STUDY_EMAIL == queuedNotificationDto.getNotificationType()) {
                    sendToList.add(queuedNotificationDto.getStudyFromEmail());
                    // No user language since email is sent to study staff instead. Use study default language instead.
                    userPreferredLangCode = null;
                } else if (StringUtils.isNotBlank(queuedNotificationDto.getToEmail())) {
                    // if there's a non-user email address specified, use it
                    sendToList.add(queuedNotificationDto.getToEmail());
                    // Likely a non-user, so use study default language instead.
                    userPreferredLangCode = null;
                } else {
                    // otherwise, lookup address information for the auth0 account
                    UserDao userDao = apisHandle.attach(UserDao.class);
                    String auth0UserId = userDao.findUserByGuid(participantGuid).map(user -> user.getAuth0UserId()).orElse(null);

                    if (auth0UserId == null) {
                        List<Governance> governances = apisHandle.attach(UserGovernanceDao.class)
                                .findActiveGovernancesByParticipantAndStudyGuids(participantGuid, studyGuid)
                                .collect(Collectors.toList());
                        if (governances.isEmpty()) {
                            throw new NoSendableEmailException(String.format(
                                    "Cannot send email to participant %s with no auth0 account and no proxies in study %s",
                                    participantGuid, studyGuid));
                        }

                        Governance gov = null;
                        if (pendingEvent.getOperatorUserId() != null) {
                            gov = governances.stream()
                                    .filter(governance -> governance.getProxyUserId() == pendingEvent.getOperatorUserId())
                                    .findFirst().orElse(null);
                        }

                        if (gov != null) {
                            LOG.info("Operator {} is a proxy for participant {} in study {}, will send email to that user",
                                    gov.getProxyUserGuid(), participantGuid, studyGuid);
                        } else if (governances.size() == 1) {
                            gov = governances.get(0);
                            LOG.info("Will send email to proxy {} for participant {} in study {}",
                                    gov.getProxyUserGuid(), participantGuid, studyGuid);
                        } else {
                            gov = governances.get(0);
                            LOG.error("Multiple proxies found for participant {} in study {}, will send email to the first proxy {}",
                                    participantGuid, studyGuid, gov.getProxyUserGuid());
                        }

                        auth0UserId = userDao.findUserById(gov.getProxyUserId()).map(user -> user.getAuth0UserId()).orElse(null);

                        // add personalizations for proxy
                        UserProfile profile = apisHandle.attach(UserProfileDao.class)
                                .findProfileByUserId(gov.getProxyUserId()).orElse(null);
                        if (profile != null) {
                            queuedNotificationDto.addTemplateSubstitutions(
                                    new NotificationTemplateSubstitutionDto(DDP_PROXY_FIRST_NAME, profile.getFirstName()),
                                    new NotificationTemplateSubstitutionDto(DDP_PROXY_LAST_NAME, profile.getLastName()));
                            // User proxy's preferred language since email will be sent to proxy.
                            userPreferredLangCode = profile.getPreferredLangCode();
                        }
                    }

                    User auth0User = null;
                    try {
                        var mgmtClient = Auth0Util.getManagementClientForStudy(apisHandle, pendingEvent.getStudyGuid());
                        auth0User = new Auth0Util(mgmtClient.getDomain()).getAuth0User(auth0UserId, mgmtClient.getToken());
                    } catch (Auth0Exception e) {
                        throw new MessageBuilderException("Could not get auth0 user " + auth0UserId, e);
                    }
                    if (auth0User != null) {
                        if (StringUtils.isNotBlank(auth0User.getEmail())) {
                            sendToList.add(auth0User.getEmail());
                        } else {
                            throw new DDPException("Cannot send email to ddp user " + participantGuid
                                    + " because they have no email address");
                        }
                    } else {
                        throw new MessageBuilderException("Cannot send email to ddp user " + participantGuid
                                + " because they have no auth0 account");
                    }

                    if (userPreferredLangCode == null) {
                        // Either email is not being sent to proxy or proxy does not have preferred language,
                        // so try getting it from participant's own profile.
                        userPreferredLangCode = apisHandle.attach(UserProfileDao.class)
                                .findProfileByUserGuid(participantGuid)
                                .map(UserProfile::getPreferredLangCode)
                                .orElse(null);
                    }
                }

                String fromName = queuedNotificationDto.getStudyFromName();
                String fromEmail = queuedNotificationDto.getStudyFromEmail();
                if (NotificationType.STUDY_EMAIL == queuedNotificationDto.getNotificationType()) {
                    fromName = cfg.getString(ConfigFile.Sendgrid.FROM_NAME);
                    fromEmail = cfg.getString(ConfigFile.Sendgrid.FROM_EMAIL);
                }

                NotificationTemplate template = determineEmailTemplate(
                        apisHandle,
                        queuedNotificationDto.getEventConfigurationId(),
                        queuedNotificationDto.getStudyGuid(),
                        userPreferredLangCode);
                String templateKey = template.getTemplateKey();
                String templateLanguage = template.getLanguageCode();
                LOG.info("Using notification template with key={} and language={} for queued event {}",
                        templateKey, templateLanguage, queuedNotificationDto.getQueuedEventId());

                // Override the activity instance substitution for email if it has a linked activity.
                boolean shouldSearchForActivityInstance = (queuedNotificationDto.getParticipantGuid() != null
                        && queuedNotificationDto.getLinkedActivityId() != null);

                if (shouldSearchForActivityInstance) {
                    String activityInstanceGuid = apisHandle.attach(JdbiActivityInstance.class)
                            .findLatestInstanceGuidByUserGuidAndActivityId(
                                    queuedNotificationDto.getParticipantGuid(), queuedNotificationDto.getLinkedActivityId())
                            .orElse(null);
                    if (activityInstanceGuid != null) {
                        List<NotificationTemplateSubstitutionDto> filtered = queuedNotificationDto.getTemplateSubstitutions().stream()
                                .filter(dto -> !NotificationTemplateVariables.DDP_ACTIVITY_INSTANCE_GUID.equals(dto.getVariableName()))
                                .collect(Collectors.toList());
                        filtered.add(new NotificationTemplateSubstitutionDto(
                                NotificationTemplateVariables.DDP_ACTIVITY_INSTANCE_GUID, activityInstanceGuid));
                        queuedNotificationDto.getTemplateSubstitutions().clear();
                        queuedNotificationDto.getTemplateSubstitutions().addAll(filtered);
                    } else {
                        LOG.error("Could not find latest activity instance for notification template substitution:"
                                        + " queuedEventId={}, userGuid={}, studyGuid={}, linkedActivityId={}, templateKey={}",
                                queuedNotificationDto.getQueuedEventId(), queuedNotificationDto.getParticipantGuid(), studyGuid,
                                queuedNotificationDto.getLinkedActivityId(), templateKey);
                    }
                }

                queuedNotificationDto.addTemplateSubstitutions(new NotificationTemplateSubstitutionDto(
                        NotificationTemplateVariables.DDP_PARTICIPANT_HRUID, queuedNotificationDto.getParticipantHruid()));

                NotificationMessage notificationMessage = new NotificationMessage(
                        queuedNotificationDto.getNotificationType(),
                        queuedNotificationDto.getNotificationServiceType(),
                        templateKey,
                        template.isDynamic(),
                        sendToList,
                        queuedNotificationDto.getParticipantFirstName(),
                        queuedNotificationDto.getParticipantLastName(),
                        participantGuid,
                        studyGuid,
                        fromName,
                        fromEmail,
                        queuedNotificationDto.getApiKey(),
                        queuedNotificationDto.getDefaultSalutation(),
                        queuedNotificationDto.getTemplateSubstitutions(),
                        queuedNotificationDto.getWebBaseUrl(),
                        queuedNotificationDto.getEventConfigurationId()
                );

                messageJson = gson.toJson(notificationMessage);
            } else {
                throw new MessageBuilderException("Unknown notification type "
                        + queuedNotificationDto.getNotificationType()
                        + " for queued event " + pendingEvent.getQueuedEventId());
            }

        } else if (pendingEvent.getActionType() == EventActionType.PDF_GENERATION) {
            QueuedPdfGenerationDto queuedPdfGenerationDto = (QueuedPdfGenerationDto) pendingEvent;
            PdfGenerationMessage pdfGenerationMessage = new PdfGenerationMessage(
                    participantGuid,
                    studyGuid,
                    queuedPdfGenerationDto.getEventConfigurationId(),
                    queuedPdfGenerationDto.getPdfDocumentConfigurationId());
            messageJson = gson.toJson(pdfGenerationMessage);
        } else {
            throw new MessageBuilderException("message has not been determined for queued event " + pendingEvent
                    .getQueuedEventId());
        }
        PubsubMessage.Builder messageBuilder = PubsubMessage.newBuilder()
                .setData(ByteString.copyFromUtf8(messageJson))
                .putAttributes(DDP_EVENT_ID, pendingEvent.getDdpEventId())
                .putAttributes(DDP_EVENT_TYPE, pendingEvent.getActionType().name())
                .putAttributes(DDP_MESSAGE_ID, ddpMessageId)
                .putAttributes(DDP_EVENT_CONFIGURATION_ID, Long.toString(pendingEvent.getEventConfigurationId()))
                .putAttributes(DDP_STUDY_GUID, studyGuid)
                .putAttributes(DDP_HOUSEKEEPING_VERSION, "1.0");
        if (pendingEvent.getMaxOccurrencesPerUser() != null) {
            messageBuilder.putAttributes(DDP_IGNORE_AFTER, Integer.toString(pendingEvent.getMaxOccurrencesPerUser()));
        }

        messageBuilder.build();
        return messageBuilder.build();
    }

    NotificationTemplate determineEmailTemplate(Handle handle, long eventConfigId, String studyGuid, String userPreferredLangCode) {
        List<NotificationTemplate> templates = handle.attach(EventDao.class).getNotificationTemplatesForEvent(eventConfigId);
        if (templates.isEmpty()) {
            throw new DDPException("Event configuration with id " + eventConfigId + " is missing notification templates");
        } else if (templates.size() == 1) {
            return templates.get(0);    // There's only one configured so use it.
        }

        NotificationTemplate preferredTemplate = null;
        if (userPreferredLangCode != null) {
            preferredTemplate = templates.stream()
                    .filter(tmpl -> tmpl.getLanguageCode().equalsIgnoreCase(userPreferredLangCode))
                    .findFirst()
                    .orElse(null);
        }

        if (preferredTemplate == null) {
            List<StudyLanguage> studyLanguages = handle.attach(StudyLanguageDao.class).findLanguages(studyGuid);
            LanguageDto languageDto = studyLanguages.stream()
                    .filter(StudyLanguage::isDefault)
                    .findFirst()
                    .map(StudyLanguage::toLanguageDto)
                    .orElse(null);
            if (languageDto == null && !studyLanguages.isEmpty()) {
                languageDto = studyLanguages.get(0).toLanguageDto();
                LOG.warn("Study {} does not have a default language, will fallback to {}", studyGuid, languageDto.getIsoCode());
            } else if (languageDto == null) {
                languageDto = LanguageStore.getOrComputeDefault(handle);
                LOG.warn("Study {} does not have any languages, will fallback to {}", studyGuid, languageDto.getIsoCode());
            }

            String langCode = languageDto.getIsoCode();
            preferredTemplate = templates.stream()
                    .filter(tmpl -> tmpl.getLanguageCode().equalsIgnoreCase(langCode))
                    .findFirst()
                    .orElseThrow(() -> new DDPException(String.format(
                            "Could not find notification template for event configuration id %d and study language %s",
                            eventConfigId, langCode)));
        }

        return preferredTemplate;
    }
}
