package org.broadinstitute.ddp.db.dao;

import static org.broadinstitute.ddp.util.StreamUtils.throwIfEmpty;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.db.dto.NotificationDetailsDto;
import org.broadinstitute.ddp.db.dto.NotificationTemplateSubstitutionDto;
import org.broadinstitute.ddp.db.dto.QueuedEventDto;
import org.broadinstitute.ddp.db.dto.QueuedNotificationDto;
import org.broadinstitute.ddp.db.dto.QueuedPdfGenerationDto;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.event.EventConfiguration;
import org.broadinstitute.ddp.model.event.PdfAttachment;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface EventDao extends SqlObject {

    Logger LOG = LoggerFactory.getLogger(EventDao.class);

    default List<EventConfiguration> getEventConfigurationByTriggerType(EventTriggerType eventTriggerType) {
        return throwIfEmpty(getEventConfigurationDtosForTriggerType(eventTriggerType).stream(),
                () -> new DaoException(String.format("No event configurations found for trigger type:"
                        + eventTriggerType.toString())))
                .map(dto -> new EventConfiguration(dto))
                .collect(Collectors.toList());
    }

    @SqlQuery("getEventConfigurationsForTriggerType")
    @UseStringTemplateSqlLocator
    @RegisterConstructorMapper(EventConfigurationDto.class)
    List<EventConfigurationDto> getEventConfigurationDtosForTriggerType(@Bind("eventTriggerType") EventTriggerType eventTriggerType);

    /**
     * Returns the event configurations for the given
     * activity instance and status
     */
    @SqlQuery("getActivityStatusEventConfigurations")
    @UseStringTemplateSqlLocator
    @RegisterConstructorMapper(EventConfigurationDto.class)
    List<EventConfigurationDto> getEventConfigurationDtosForActivityStatus(@Bind("activityInstanceId") long activityInstanceId,
                                                                           @Bind("status") String activityStatus);


    @UseStringTemplateSqlLocator
    @SqlQuery("getActiveDispatchConfigsByStudyIdAndTrigger")
    @RegisterConstructorMapper(EventConfigurationDto.class)
    List<EventConfigurationDto> getActiveDispatchConfigsByStudyIdAndTrigger(@Bind("studyId") long studyId,
                                                                            @Bind("trigger") EventTriggerType trigger);

    @UseStringTemplateSqlLocator
    @SqlQuery("getActiveDispatchedEventConfigSummariesByStudyIdAndTriggerType")
    @RegisterConstructorMapper(EventConfigurationDto.class)
    List<EventConfigurationDto> getEventConfigSummariesByStudyIdAndTriggerType(@Bind("studyId") long studyId,
                                                                               @Bind("triggerType") EventTriggerType triggerType);

    default int addMedicalUpdateTriggeredEventsToQueue(long studyId, long participantId) {
        List<EventConfigurationDto> summaries = getEventConfigSummariesByStudyIdAndTriggerType(studyId, EventTriggerType.MEDICAL_UPDATE);

        QueuedEventDao queuedEventDao = getHandle().attach(QueuedEventDao.class);
        int numEventsQueued = 0;

        for (EventConfigurationDto summary : summaries) {
            long queuedEventId = queuedEventDao.addToQueue(summary.getEventConfigurationId(),
                    null, participantId, summary.getPostDelaySeconds());
            LOG.info("Inserted queued event id={} for eventConfigurationId={}", queuedEventId, summary.getEventConfigurationId());
            numEventsQueued++;
        }

        return numEventsQueued;
    }


    /**
     * Clients should consider shuffling returned list to avoid
     * "bad apple" messages that cause flow to block at the same
     * point in the queue and thus starve everything that comes
     * after the bad apple
     *
     * @return
     */
    default List<QueuedEventDto> getQueuedEvents() {
        List<QueuedEventDto> queuedEvents = new ArrayList<>();

        // do basic query to get generic queued event details
        List<QueuedEventDto> pendingEvents = getPendingConfigurations();
        for (QueuedEventDto pendingEvent : pendingEvents) {
            if (pendingEvent.getActionType() == EventActionType.NOTIFICATION) {
                QueuedNotificationDto queuedNotification = getNotificationDtoForQueuedEvent(pendingEvent);
                queuedEvents.add(queuedNotification);
            } else if (pendingEvent.getActionType() == EventActionType.PDF_GENERATION) {
                queuedEvents.add(getGeneratePdfDtoForQueuedEvent(pendingEvent));
            } else {
                queuedEvents.add(pendingEvent);
            }
        }
        return queuedEvents;
    }

    /**
     * Loads the notification specific information, using eventDto as a base.
     */
    default QueuedNotificationDto getNotificationDtoForQueuedEvent(QueuedEventDto eventDto) {
        // get the substitutions (if any) and wire up a new notification dto
        List<NotificationTemplateSubstitutionDto> templateSubstitutions =
                getTemplateSubstitutionsForQueuedNotification(eventDto.getQueuedEventId());
        NotificationDetailsDto notificationDetailsDto = getNotificationDetailsDtoForQueuedEvent(eventDto
                        .getQueuedEventId(),
                eventDto.getParticipantGuid());
        notificationDetailsDto.setTemplateSubstitutions(templateSubstitutions);

        return new QueuedNotificationDto(eventDto, notificationDetailsDto);
    }

    /**
     * Loads the pdf generation specific information, using eventDto as a base.
     */
    default QueuedPdfGenerationDto getGeneratePdfDtoForQueuedEvent(QueuedEventDto eventDto) {
        long pdfDocumentConfigurationId = getPdfConfigIdFromEventActionByEventConfigId(eventDto.getEventConfigurationId());
        return new QueuedPdfGenerationDto(eventDto, pdfDocumentConfigurationId);
    }

    @SqlQuery("select act.pdf_document_configuration_id"
            + "  from pdf_generation_event_action as act"
            + "  join event_configuration as e on e.event_action_id = act.event_action_id"
            + " where e.event_configuration_id = :eventConfigId")
    long getPdfConfigIdFromEventActionByEventConfigId(@Bind("eventConfigId") long eventConfigId);

    @SqlQuery("getTemplateSubstitutionsForQueuedNotification")
    @UseStringTemplateSqlLocator
    @RegisterConstructorMapper(NotificationTemplateSubstitutionDto.class)
    List<NotificationTemplateSubstitutionDto> getTemplateSubstitutionsForQueuedNotification(@Bind("queuedEventId") long queuedEventId);

    @SqlQuery("getNotificationDetailsForQueuedEvent")
    @UseStringTemplateSqlLocator
    @RegisterConstructorMapper(NotificationDetailsDto.class)
    NotificationDetailsDto getNotificationDetailsDtoForQueuedEvent(@Bind("queuedEventId") long queuedEventId,
                                                                   @Bind("userGuid") String userGuid);

    @UseStringTemplateSqlLocator
    @SqlQuery("getNotificationAttachmentDetails")
    @RegisterConstructorMapper(PdfAttachment.class)
    List<PdfAttachment> getPdfAttachmentsForEvent(@Bind("eventConfigId") long eventConfigurationId);

    /**
     * Consider using {@link #getQueuedEvents()} instead, as it has
     * more fully formed objects.
     *
     * @return
     */
    @SqlQuery("getPendingConfigurations")
    @UseStringTemplateSqlLocator
    @RegisterConstructorMapper(EventConfigurationDto.class)
    List<QueuedEventDto> getPendingConfigurations();

    @SqlQuery("getDsmNotificationConfigurationIds")
    @UseStringTemplateSqlLocator
    List<Long> getDsmNotificationConfigurationIds(
            @Bind("umbrellaStudyGuid") String umbrellaStudyGuid,
            @Bind("userGuid") String userGuid,
            @Bind("notificationEventTypeCode") String eventTypeCode
    );

    /**
     * Gets the configurations associated with notifications that
     * should be sent when someone joins a study's mailing list
     *
     * @param studyGuid guid for the study
     */
    @SqlQuery("getNotificationConfigsForMailingListByEventType")
    @UseStringTemplateSqlLocator
    @RegisterConstructorMapper(EventConfigurationDto.class)
    List<EventConfigurationDto> getNotificationConfigsForMailingListByEventType(@Bind("studyGuid") String studyGuid,
                                                                                @Bind("eventTriggerType")
                                                                                        EventTriggerType eventTriggerType);


    /**
     * Returns the notification event configurations for the given
     * study and workflow state.
     */
    @SqlQuery("getNotificationConfigsForWorkflowState")
    @UseStringTemplateSqlLocator
    @RegisterConstructorMapper(EventConfigurationDto.class)
    List<EventConfigurationDto> getNotificationConfigsForWorkflowState(@Bind("studyGuid") String studyGuid,
                                                                       @Bind("workflowStateId") long workflowStateId);

    @SqlUpdate("update event_configuration set is_active = :enable where umbrella_study_id = :studyId")
    int enableAllStudyEvents(@Bind("studyId") long studyId, @Bind("enable") boolean enable);


}
