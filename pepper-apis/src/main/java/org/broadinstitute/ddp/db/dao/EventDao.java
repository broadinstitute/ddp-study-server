package org.broadinstitute.ddp.db.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.constants.SqlConstants;
import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.db.dto.NotificationDetailsDto;
import org.broadinstitute.ddp.db.dto.NotificationTemplateSubstitutionDto;
import org.broadinstitute.ddp.db.dto.QueuedEventDto;
import org.broadinstitute.ddp.db.dto.QueuedNotificationDto;
import org.broadinstitute.ddp.db.dto.QueuedPdfGenerationDto;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.event.PdfAttachment;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface EventDao extends SqlObject {

    Logger LOG = LoggerFactory.getLogger(EventDao.class);

    /**
     * Returns the event configurations for the given
     * activity instance and status
     */
    @SqlQuery("getActivityStatusEventConfigurations")
    @UseStringTemplateSqlLocator
    @RegisterRowMapper(EventConfigurationDtoMapper.class)
    List<EventConfigurationDto> getEventConfigurationsForActivityStatus(@Bind("activityInstanceId") long activityInstanceId,
                                                                        @Bind("status") String activityStatus);

    /**
     * Returns the event configurations for the given study id, activity id and activity instance status
     * This method is used by runPostActivityStatusChangeHooks() when a certain activityId in a studyId
     * changes its status to activityInstanceStatus and a check is performed to find if there's an
     * event configuration triggering the activity instance creation
     */
    @SqlQuery("getEventConfigurations")
    @UseStringTemplateSqlLocator
    @RegisterConstructorMapper(EventConfigurationDto.class)
    List<EventConfigurationDto> getEventConfigurationsByStudyIdActivityIdAndStatus(
            @Bind("studyId") long studyId,
            @Bind("activityId") long activityId,
            @Bind("activityInstanceStatus") String activityInstanceStatus,
            @BindList(value = "actionTypes", onEmpty = BindList.EmptyHandling.NULL) Set<EventActionType> actionTypes);

    @UseStringTemplateSqlLocator
    @SqlQuery("getActiveDispatchConfigsByStudyIdAndTrigger")
    @RegisterRowMapper(EventConfigurationDtoMapper.class)
    List<EventConfigurationDto> getActiveDispatchConfigsByStudyIdAndTrigger(@Bind("studyId") long studyId,
                                                                            @Bind("trigger") EventTriggerType trigger);

    @UseStringTemplateSqlLocator
    @SqlQuery("getActiveDispatchedEventConfigSummariesByStudyIdAndTriggerType")
    @RegisterRowMapper(EventConfigurationDtoMapper.class)
    List<EventConfigurationDto> getEventConfigSummariesByStudyIdAndTriggerType(@Bind("studyId") long studyId,
                                                                               @Bind("triggerType") EventTriggerType triggerType);

    default int addMedicalUpdateTriggeredEventsToQueue(long studyId, long participantId) {
        List<EventConfigurationDto> summaries = getEventConfigSummariesByStudyIdAndTriggerType(studyId, EventTriggerType.MEDICAL_UPDATE);

        QueuedEventDao queuedEventDao = getHandle().attach(QueuedEventDao.class);
        int numEventsQueued = 0;

        for (EventConfigurationDto summary : summaries) {
            long queuedEventId = queuedEventDao.addToQueue(summary.getEventConfigurationId(),
                    null, participantId, summary.getSecondsToWaitBeforePosting());
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
    @RegisterRowMapper(EventConfigDtoMapper.class)
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
    @RegisterRowMapper(EventConfigurationDtoMapper.class)
    List<EventConfigurationDto> getNotificationConfigsForMailingListByEventType(@Bind("studyGuid") String studyGuid,
                                                                                @Bind("eventTriggerType")
                                                                                        EventTriggerType eventTriggerType);


    /**
     * Returns the notification event configurations for the given
     * study and workflow state.
     */
    @SqlQuery("getNotificationConfigsForWorkflowState")
    @UseStringTemplateSqlLocator
    @RegisterRowMapper(EventConfigurationDtoMapper.class)
    List<EventConfigurationDto> getNotificationConfigsForWorkflowState(@Bind("studyGuid") String studyGuid,
                                                                       @Bind("workflowStateId") long workflowStateId);

    @SqlUpdate("update event_configuration set is_active = :enable where umbrella_study_id = :studyId")
    int enableAllStudyEvents(@Bind("studyId") long studyId, @Bind("enable") boolean enable);

    class EventConfigurationDtoMapper implements RowMapper<EventConfigurationDto> {

        @Override
        public EventConfigurationDto map(ResultSet rs, StatementContext ctx) throws SQLException {
            EventTriggerType eventTriggerType = EventTriggerType.valueOf(rs.getString(SqlConstants
                    .EventTriggerTypeTable.TYPE_CODE));
            return new EventConfigurationDto(eventTriggerType,
                    (Integer) rs.getObject(SqlConstants.EventConfigurationTable.POST_DELAY_SECONDS),
                    rs.getLong(SqlConstants.EventConfigurationTable.ID),
                    EventActionType.valueOf(rs.getString(SqlConstants.EventActionTypeTable.TYPE)));
        }
    }

    class EventConfigDtoMapper implements RowMapper<QueuedEventDto> {

        @Override
        public QueuedEventDto map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new QueuedEventDto(
                    rs.getLong(SqlConstants.EventConfigurationTable.ID),
                    rs.getLong(SqlConstants.QueuedEventTable.ID),
                    rs.getLong(SqlConstants.QueuedEventTable.OPERATOR_USER_ID),
                    rs.getString(ConfigFile.SqlQuery.PARTICIPANT_GUID),
                    rs.getString(ConfigFile.SqlQuery.PARTICIPANT_HRUID),
                    EventActionType.valueOf(rs.getString(SqlConstants.EventActionTypeTable.TYPE)),
                    "1.0",
                    (Integer) rs.getObject(SqlConstants.EventConfigurationTable.MAX_OCCURRENCES_PER_USER),
                    rs.getString(SqlConstants.MessageDestinationTable.PUBSUB_TOPIC),
                    rs.getString(ConfigFile.SqlQuery.PEX_PRECONDITION),
                    rs.getString(ConfigFile.SqlQuery.PEX_CANCEL_CONDITION),
                    rs.getString(ConfigFile.SqlQuery.STUDY_GUID)
            );
        }
    }

}
