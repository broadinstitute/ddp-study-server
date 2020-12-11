package org.broadinstitute.ddp.db.dao;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.constants.SqlConstants;
import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.db.dto.NotificationDetailsDto;
import org.broadinstitute.ddp.db.dto.NotificationTemplateSubstitutionDto;
import org.broadinstitute.ddp.db.dto.QueuedEventDto;
import org.broadinstitute.ddp.db.dto.QueuedNotificationDto;
import org.broadinstitute.ddp.db.dto.QueuedPdfGenerationDto;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.event.EventConfiguration;
import org.broadinstitute.ddp.model.event.NotificationTemplate;
import org.broadinstitute.ddp.model.event.PdfAttachment;
import org.jdbi.v3.core.result.LinkedHashMapRowReducer;
import org.jdbi.v3.core.result.RowView;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.statement.UseRowReducer;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface EventDao extends SqlObject {

    Logger LOG = LoggerFactory.getLogger(EventDao.class);

    default List<EventConfiguration> getAllEventConfigurationsByStudyId(long studyId) {
        return getEventConfigurationDtosByStudyId(studyId).stream()
                .map(dto -> new EventConfiguration(dto))
                .collect(Collectors.toList());
    }

    default List<EventConfiguration> getAllEventConfigurationsByStudyIdAndTriggerType(long studyId,
                                                                                      EventTriggerType eventTriggerType) {
        return getEventConfigurationDtosForStudyIdAndTriggerType(studyId, eventTriggerType).stream()
                .map(dto -> new EventConfiguration(dto))
                .collect(Collectors.toList());
    }

    default List<EventConfiguration> getSynchronousEventConfigurationsByStudyIdAndTriggerType(long studyId,
                                                                                              EventTriggerType eventTriggerType) {
        return getAllEventConfigurationsByStudyIdAndTriggerType(studyId, eventTriggerType).stream()
                .filter(eventConfiguration -> !eventConfiguration.dispatchToHousekeeping())
                .collect(Collectors.toList());
    }

    default List<EventConfiguration> getAsynchronousEventConfigurationsByStudyIdAndTriggerType(long studyId,
                                                                                               EventTriggerType eventTriggerType) {
        return getAllEventConfigurationsByStudyIdAndTriggerType(studyId, eventTriggerType).stream()
                .filter(eventConfiguration -> eventConfiguration.dispatchToHousekeeping())
                .collect(Collectors.toList());
    }

    @SqlQuery("getEventConfigurationByEventConfigurationId")
    @UseStringTemplateSqlLocator
    @RegisterConstructorMapper(EventConfigurationDto.class)
    @UseRowReducer(EventConfigurationActionReducer.class)
    Optional<EventConfigurationDto> getEventConfigurationDtoById(
            @Bind("eventConfigurationId") long eventConfigurationId);

    @SqlQuery("getEventConfigurationsByStudyId")
    @UseStringTemplateSqlLocator
    @RegisterConstructorMapper(EventConfigurationDto.class)
    @UseRowReducer(EventConfigurationActionReducer.class)
    List<EventConfigurationDto> getEventConfigurationDtosByStudyId(
            @Bind("studyId") long studyId);

    @SqlQuery("getEventConfigurationsForStudyIdAndTriggerType")
    @UseStringTemplateSqlLocator
    @RegisterConstructorMapper(EventConfigurationDto.class)
    @UseRowReducer(EventConfigurationActionReducer.class)
    List<EventConfigurationDto> getEventConfigurationDtosForStudyIdAndTriggerType(
            @Bind("studyId") long studyId,
            @Bind("eventTriggerType") EventTriggerType eventTriggerType);

    /**
     * Returns the event configurations for the given activity instance and status
     */
    @SqlQuery("getActivityStatusEventConfigurations")
    @UseStringTemplateSqlLocator
    @RegisterConstructorMapper(EventConfigurationDto.class)
    @UseRowReducer(EventConfigurationActionReducer.class)
    List<EventConfigurationDto> getEventConfigurationDtosForActivityStatus(@Bind("activityInstanceId") long activityInstanceId,
                                                                           @Bind("status") String activityStatus);


    @UseStringTemplateSqlLocator
    @SqlQuery("getActiveDispatchConfigsByStudyIdAndTrigger")
    @RegisterConstructorMapper(EventConfigurationDto.class)
    @UseRowReducer(EventConfigurationActionReducer.class)
    List<EventConfigurationDto> getActiveDispatchConfigsByStudyIdAndTrigger(@Bind("studyId") long studyId,
                                                                            @Bind("eventTriggerType") EventTriggerType eventTriggerType);

    default int addMedicalUpdateTriggeredEventsToQueue(long studyId, long participantId) {
        List<EventConfigurationDto> summaries = getEventConfigurationDtosForStudyIdAndTriggerType(studyId,
                EventTriggerType.MEDICAL_UPDATE);

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
     * Clients should consider shuffling returned list to avoid "bad apple" messages that cause flow to block at the same point in the queue
     * and thus starve everything that comes after the bad apple.
     */
    @UseStringTemplateSqlLocator
    @SqlQuery("findPublishableQueuedEvents")
    @RegisterConstructorMapper(QueuedEventDto.class)
    @RegisterConstructorMapper(QueuedNotificationDto.class)
    @RegisterConstructorMapper(NotificationDetailsDto.class)
    @RegisterConstructorMapper(NotificationTemplateSubstitutionDto.class)
    @RegisterConstructorMapper(QueuedPdfGenerationDto.class)
    @UseRowReducer(QueuedEventDtoReducer.class)
    List<QueuedEventDto> findPublishableQueuedEvents();

    @UseStringTemplateSqlLocator
    @SqlQuery("findAllQueuedEvents")
    @RegisterConstructorMapper(QueuedEventDto.class)
    @RegisterConstructorMapper(QueuedNotificationDto.class)
    @RegisterConstructorMapper(NotificationDetailsDto.class)
    @RegisterConstructorMapper(NotificationTemplateSubstitutionDto.class)
    @RegisterConstructorMapper(QueuedPdfGenerationDto.class)
    @UseRowReducer(QueuedEventDtoReducer.class)
    @RegisterConstructorMapper(QueuedEventDto.class)
    List<QueuedEventDto> findAllQueuedEvents();

    @UseStringTemplateSqlLocator
    @SqlQuery("findQueuedEventById")
    @RegisterConstructorMapper(QueuedEventDto.class)
    @RegisterConstructorMapper(QueuedNotificationDto.class)
    @RegisterConstructorMapper(NotificationDetailsDto.class)
    @RegisterConstructorMapper(NotificationTemplateSubstitutionDto.class)
    @RegisterConstructorMapper(QueuedPdfGenerationDto.class)
    @UseRowReducer(QueuedEventDtoReducer.class)
    @RegisterConstructorMapper(QueuedEventDto.class)
    Optional<QueuedEventDto> findQueuedEventById(@Bind("id") long queuedEventId);

    // Used by Study Builder do not delete
    @SqlUpdate("update event_configuration set is_active = :enable where umbrella_study_id = :studyId")
    int enableAllStudyEvents(@Bind("studyId") long studyId, @Bind("enable") boolean enable);

    @UseStringTemplateSqlLocator
    @SqlQuery("getNotificationAttachmentDetails")
    @RegisterConstructorMapper(PdfAttachment.class)
    List<PdfAttachment> getPdfAttachmentsForEvent(@Bind("eventConfigId") long eventConfigurationId);

    @UseStringTemplateSqlLocator
    @SqlQuery("getNotificationTemplatesForEvent")
    @RegisterConstructorMapper(NotificationTemplate.class)
    List<NotificationTemplate> getNotificationTemplatesForEvent(@Bind("eventConfigId") long eventConfigurationId);

    /**
     * Gets the configurations associated with notifications that should be sent when someone joins a study's mailing list
     *
     * @param studyGuid guid for the study
     */
    @SqlQuery("getNotificationConfigsForMailingListByEventType")
    @UseStringTemplateSqlLocator
    @RegisterConstructorMapper(EventConfigurationDto.class)
    @UseRowReducer(EventConfigurationActionReducer.class)
    List<EventConfigurationDto> getNotificationConfigsForMailingListByEventType(@Bind("studyGuid") String studyGuid,
                                                                                @Bind("eventTriggerType")
                                                                                        EventTriggerType eventTriggerType);


    /**
     * Returns the notification event configurations for the given study and workflow state.
     */
    @SqlQuery("getNotificationConfigsForWorkflowState")
    @UseStringTemplateSqlLocator
    @RegisterConstructorMapper(EventConfigurationDto.class)
    @UseRowReducer(EventConfigurationActionReducer.class)
    List<EventConfigurationDto> getNotificationConfigsForWorkflowState(@Bind("studyGuid") String studyGuid,
                                                                       @Bind("workflowStateId") long workflowStateId);

    //
    // reducers
    //

    class EventConfigurationActionReducer implements LinkedHashMapRowReducer<Long, EventConfigurationDto> {
        @Override
        public void accumulate(Map<Long, EventConfigurationDto> container, RowView view) {
            long eventConfigurationId = view.getColumn(SqlConstants.EventConfigurationTable.ID, Long.class);
            EventConfigurationDto dto = container.computeIfAbsent(eventConfigurationId, id -> view.getRow(EventConfigurationDto.class));

            Long userNotificationDocumentConfigurationId = view.getColumn("user_notification_document_configuration_id", Long.class);
            if (userNotificationDocumentConfigurationId != null) {
                dto.addNotificationPdfAttachment(userNotificationDocumentConfigurationId,
                        view.getColumn("generate_if_missing", Boolean.class));
            }

            Long targetActivityId = view.getColumn("target_activity_id", Long.class);
            if (targetActivityId != null) {
                dto.addTargetActivityId(targetActivityId);
            }
        }
    }

    class QueuedEventDtoReducer implements LinkedHashMapRowReducer<Long, QueuedEventDto> {
        @Override
        public void accumulate(Map<Long, QueuedEventDto> container, RowView view) {
            QueuedEventDto queuedDto;
            var actionType = EventActionType.valueOf(view.getColumn("event_action_type", String.class));

            if (actionType == EventActionType.NOTIFICATION) {
                long id = view.getColumn("queued_event_id", Long.class);
                var notificationDto = (QueuedNotificationDto) container
                        .computeIfAbsent(id, key -> view.getRow(QueuedNotificationDto.class));

                String varName = view.getColumn("substitution_variable_name", String.class);
                if (varName != null) {
                    var substitution = view.getRow(NotificationTemplateSubstitutionDto.class);
                    notificationDto.addTemplateSubstitutions(substitution);
                }

                Long attachmentId = view.getColumn("file_upload_id", Long.class);
                if (attachmentId != null) {
                    notificationDto.addAttachments(attachmentId);
                }

                queuedDto = notificationDto;
            } else if (actionType == EventActionType.PDF_GENERATION) {
                queuedDto = view.getRow(QueuedPdfGenerationDto.class);
            } else {
                queuedDto = view.getRow(QueuedEventDto.class);
            }

            container.put(queuedDto.getQueuedEventId(), queuedDto);
        }
    }
}
