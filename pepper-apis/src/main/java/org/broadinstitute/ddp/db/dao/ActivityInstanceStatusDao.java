package org.broadinstitute.ddp.db.dao;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dto.ActivityConditionDto;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.ActivityInstanceStatusDto;
import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.governance.Governance;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.pex.PexException;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.broadinstitute.ddp.pex.TreeWalkInterpreter;
import org.broadinstitute.ddp.service.CopyAnswerService;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface ActivityInstanceStatusDao extends SqlObject {

    Logger LOG = LoggerFactory.getLogger(ActivityInstanceStatusDao.class);

    @CreateSqlObject
    JdbiUmbrellaStudy getJdbiUmbrellaStudy();

    @CreateSqlObject
    JdbiUser getJdbiUser();

    @CreateSqlObject
    JdbiActivityInstance getJdbiActivityInstance();

    @CreateSqlObject
    JdbiActivityInstanceStatus getJdbiActivityStatus();

    @CreateSqlObject
    JdbiActivityInstanceStatusType getJdbiActivityStatusType();

    @CreateSqlObject
    ActivityStatusEventDao getActivityStatusEventDao();

    @CreateSqlObject
    JdbiActivity getJdbiActivity();

    @CreateSqlObject
    JdbiActivityCondition getJdbiActivityCondition();

    @CreateSqlObject
    JdbiEventConfigurationOccurrenceCounter getJdbiEventConfigurationOccurrenceCounter();

    @CreateSqlObject
    EventDao getEventDao();

    @CreateSqlObject
    JdbiUserStudyEnrollment getUserStudyEnrollmentDao();

    @CreateSqlObject
    UserAnnouncementDao getUserAnnouncementDao();


    @SqlQuery("select ais.*, aist.activity_instance_status_type_code from activity_instance_status as ais"
            + "  join activity_instance_status_type as aist on aist.activity_instance_status_type_id = ais.activity_instance_status_type_id"
            + " where ais.activity_instance_id = :instanceId"
            + " order by ais.updated_at desc limit 1")
    @RegisterConstructorMapper(ActivityInstanceStatusDto.class)
    Optional<ActivityInstanceStatusDto> getCurrentStatus(@Bind("instanceId") long instanceId);

    @SqlQuery("select ais.*, aist.activity_instance_status_type_code from activity_instance_status as ais"
            + "  join activity_instance_status_type as aist on aist.activity_instance_status_type_id = ais.activity_instance_status_type_id"
            + " where ais.activity_instance_id ="
            + "       (select activity_instance_id from activity_instance where activity_instance_guid = :instanceGuid)"
            + " order by ais.updated_at desc limit 1")
    @RegisterConstructorMapper(ActivityInstanceStatusDto.class)
    Optional<ActivityInstanceStatusDto> getCurrentStatus(@Bind("instanceGuid") String instanceGuid);

    @SqlQuery("select status.*, aist.activity_instance_status_type_code from activity_instance_status as status"
            + "  join activity_instance_status_type aist on aist.activity_instance_status_type_id = status.activity_instance_status_type_id"
            + " where status.activity_instance_id = :instanceId"
            + " order by status.updated_at desc")
    @RegisterConstructorMapper(ActivityInstanceStatusDto.class)
    List<ActivityInstanceStatusDto> getAllStatuses(@Bind("instanceId") long instanceId);

    @SqlQuery("select status.*, aist.activity_instance_status_type_code from activity_instance_status as status"
            + "  join activity_instance_status_type aist on aist.activity_instance_status_type_id = status.activity_instance_status_type_id"
            + "  join activity_instance ai on ai.activity_instance_id = status.activity_instance_id"
            + " where ai.activity_instance_guid = :instanceGuid"
            + " order by status.updated_at desc")
    @RegisterConstructorMapper(ActivityInstanceStatusDto.class)
    List<ActivityInstanceStatusDto> getAllStatuses(@Bind("instanceGuid") String instanceGuid);

    @SqlQuery("select status.*, aist.activity_instance_status_type_code from activity_instance_status as status "
            + "  join activity_instance ai on ai.activity_instance_id = status.activity_instance_id "
            + "  join activity_instance_status_type aist on aist.activity_instance_status_type_id = status.activity_instance_status_type_id"
            + " where ai.activity_instance_guid = :instanceGuid "
            + "   and aist.activity_instance_status_type_code = :statusType "
            + " order by status.updated_at desc limit 1")
    @RegisterConstructorMapper(ActivityInstanceStatusDto.class)
    Optional<ActivityInstanceStatusDto> getLatestStatus(@Bind("instanceGuid") String instanceGuid,
                                                        @Bind("statusType") InstanceStatusType statusType);

    /**
     * Finds the current status id of an activity instance and compares it to proposed status type id. Returns
     * whether they match.
     *
     * @param newStatusTypeId id for proposed status code
     * @param instanceId   id of activity instance
     * @return boolean indicating if current status matches proposed status
     */
    default boolean doesCurrentStatusMatchNew(long newStatusTypeId, long instanceId) {
        return getCurrentStatus(instanceId)
                .map(status -> status.getTypeId() == newStatusTypeId)
                .orElse(false);
    }

    /**
     * Convenience method to insert status based on activity instance guid.
     */
    default ActivityInstanceStatusDto insertStatus(String instanceGuid, InstanceStatusType statusType,
                                                   long epochMilliseconds, String operatorGuid) {
        long instanceId = getJdbiActivityInstance().getActivityInstanceId(instanceGuid);
        return insertStatus(instanceId, statusType, epochMilliseconds, operatorGuid);
    }

    /**
     * Inserts status based on given parameters, returns id of status. If the most recent status for this activity
     * is the same status code, it will not update (adding a new row to the db) and will return the most
     * recent status id. Also checks if there are any housekeeping events that should be queued in response to this
     * status change and queues them up for housekeeping.
     *
     * @param instanceId        id of activity instance
     * @param newStatus        new status of activity instance
     * @param epochMilliseconds time status is updated in milliseconds
     * @param operatorGuid      guid of operator
     * @return the most recent status
     */
    default ActivityInstanceStatusDto insertStatus(
            long instanceId,
            InstanceStatusType newStatus,
            long epochMilliseconds,
            String operatorGuid
    ) {
        JdbiActivityInstance jdbiInstance = getJdbiActivityInstance();
        JdbiActivityInstanceStatusType jdbiStatusType = getJdbiActivityStatusType();
        JdbiUser jdbiUser = getJdbiUser();

        long operatorId = jdbiUser.getUserIdByGuid(operatorGuid);

        Optional<ActivityInstanceStatusDto> currentStatus = getCurrentStatus(instanceId);
        long newStatusTypeId = jdbiStatusType.getStatusTypeId(newStatus);
        long completeStatusTypeId = jdbiStatusType.getStatusTypeId(InstanceStatusType.COMPLETE);

        // If current status is COMPLETE, no transitions are allowed
        if (currentStatus.isPresent() && currentStatus.get().getTypeId() == completeStatusTypeId) {
            if (newStatusTypeId == completeStatusTypeId) {
                return updateStatusTimestamp(currentStatus.get(), epochMilliseconds);
            } else {
                return currentStatus.get();
            }
        }

        if (doesCurrentStatusMatchNew(newStatusTypeId, instanceId)) {
            return updateStatusTimestamp(currentStatus.get(), epochMilliseconds);
        }

        long activityInstanceStatusId = getJdbiActivityStatus().insert(instanceId, newStatus, epochMilliseconds, operatorId);
        if (newStatusTypeId == completeStatusTypeId) {
            int rowsUpdated = getJdbiActivityInstance().updateFirstCompletedAtIfNotSet(instanceId, epochMilliseconds);
            if (rowsUpdated > 1) {
                throw new DaoException("Multiple entries updated when updating firstCompletedAt timestamp for"
                        + " activity instance with id " + instanceId);
            }
        }

        ActivityInstanceDto instanceDto = jdbiInstance.getByActivityInstanceId(instanceId)
                .orElseThrow(() -> new DaoException("Could not find activity instance with id " + instanceId));

        runPostActivityStatusChangeHooks(
                operatorId,
                instanceDto.getParticipantId(),
                instanceDto,
                newStatus.name()
        );
        addStatusTriggerEventsToQueue(operatorId, instanceDto.getParticipantId(), instanceDto, newStatus.name());

        return new ActivityInstanceStatusDto(activityInstanceStatusId, newStatusTypeId,
                instanceDto.getId(), operatorId, epochMilliseconds, newStatus);
    }

    default ActivityInstanceStatusDto updateStatusTimestamp(ActivityInstanceStatusDto statusDto, long newTimestampMillis) {
        int rowsUpdated = getJdbiActivityStatus().updateTimestampByStatusId(statusDto.getId(), newTimestampMillis);
        if (rowsUpdated != 1) {
            throw new DaoException("Could not update timestamp for activity instance status with id " + statusDto.getId());
        }
        return new ActivityInstanceStatusDto(statusDto.getId(), statusDto.getTypeId(), statusDto.getInstanceId(),
                statusDto.getOperatorId(), newTimestampMillis, statusDto.getType());
    }

    /**
     * Checks if there are any events that should be spawned as a result
     * of the change in status and if so, queues them
     *
     * @param operatorId    user id of the operator
     * @param participantId user id of the participant
     * @param instanceDto   the activity instance
     * @param status        the new status for the activity
     */
    default void addStatusTriggerEventsToQueue(long operatorId,
                                               long participantId,
                                               ActivityInstanceDto instanceDto,
                                               String status) {
        int numEventsQueued = getActivityStatusEventDao().addStatusTriggerEventsToQueue(operatorId, participantId,
                instanceDto, status);
        LOG.info("Queued {} events for operator {} on behalf of participant {}", numEventsQueued,
                operatorId, participantId);
    }

    /**
     * Runs all events that are triggered by an activity status change
     *
     * @param operatorId             Id of the operator
     * @param participantId          Id of the participant
     * @param instanceDto            The activity instance whose status transition triggers the hooks
     * @param activityInstanceStatus Activity instance status triggering hooks
     */
    default void runPostActivityStatusChangeHooks(
            long operatorId,
            long participantId,
            ActivityInstanceDto instanceDto,
            String activityInstanceStatus
    ) {
        long studyActivityId = instanceDto.getActivityId();
        Long studyId = getJdbiActivity().getStudyIdByActivityId(studyActivityId)
                .orElseThrow(() -> new DaoException("Umbrella study for the study activity " + studyActivityId + " not found"));

        List<EventConfigurationDto> eventConfigs = getEventDao()
                .getEventConfigurationsByStudyIdActivityIdAndStatus(
                        studyId,
                        studyActivityId,
                        activityInstanceStatus,
                        new HashSet<>(
                                Arrays.asList(
                                        EventActionType.ACTIVITY_INSTANCE_CREATION,
                                        EventActionType.USER_ENROLLED,
                                        EventActionType.ANNOUNCEMENT,
                                        EventActionType.COPY_ANSWER)));

        if (!eventConfigs.isEmpty()) {
            LOG.info(
                    "{} event configurations found for the study {}, study activity {} and activity instance status {}",
                    eventConfigs.size(), studyId, studyActivityId, activityInstanceStatus
            );
        } else {
            LOG.info(
                    "No event configurations found for the study {}, study activity {} and activity instance status {}",
                    studyId, studyActivityId, activityInstanceStatus
            );
            return;
        }

        PexInterpreter interp = new TreeWalkInterpreter();
        String participantGuid = getJdbiUser().findByUserId(participantId).getUserGuid();

        // Checking if any event configuration is eligible for the activity instance creation
        for (EventConfigurationDto eventConfig : eventConfigs) {
            LOG.info(
                    "Processing the event configuration with the precondition expression = {},"
                            + " activity to create = {} and max instances per user = {}",
                    eventConfig.getPreconditionExpression(),
                    eventConfig.getActivityIdToCreate(),
                    eventConfig.getMaxInstancesPerUser()
            );

            // Checking if the per-user event configuration counter is not exceeded
            int numOccurrences = getJdbiEventConfigurationOccurrenceCounter()
                    .getOrCreateNumOccurrences(eventConfig.getEventConfigurationId(), participantId);

            LOG.info(
                    "Checking if the occurrences per user ({}) hit the threshold ({}) for event configuration (id = {}), user id = {}",
                    numOccurrences,
                    eventConfig.getMaxOccurrencesPerUser(),
                    eventConfig.getEventConfigurationId(),
                    participantId
            );

            if (eventConfig.getMaxOccurrencesPerUser() != null && numOccurrences >= eventConfig.getMaxOccurrencesPerUser()) {
                LOG.info(
                        "The number of this event's configuration occurrences for the participant (id = {}) is {}"
                                + " while the allowed maximum number of occurrences per user is {}, skipping the configuration",
                        participantId, numOccurrences, eventConfig.getMaxOccurrencesPerUser()
                );
                continue;
            }

            String cancelExpr = eventConfig.getCancelExpression();
            if (StringUtils.isNotBlank(cancelExpr)) {
                LOG.info("Checking the cancel expression `{}` for the event configuration (id = {})",
                        cancelExpr, eventConfig.getEventConfigurationId());
                try {
                    boolean shouldCancel = interp.eval(cancelExpr, getHandle(), participantGuid, instanceDto.getGuid());
                    if (shouldCancel) {
                        LOG.info("Cancel expression `{}` evaluated to TRUE, skipping the configuration", cancelExpr);
                        continue;
                    }
                } catch (PexException e) {
                    throw new DaoException("Error evaluating cancel expression: " + cancelExpr, e);
                }
            }

            // Checking if a precondition expression (if exists) evaluates to TRUE
            String precondExpr = eventConfig.getPreconditionExpression();
            LOG.info(
                    "Checking the precondition expression `{}` for the event configuration (id = {})",
                    eventConfig.getPreconditionExpression(),
                    eventConfig.getEventConfigurationId()
            );
            try {
                if (precondExpr != null && !interp.eval(precondExpr, getHandle(),
                        participantGuid, instanceDto.getGuid())) {
                    LOG.info("Precondition expression {} evaluated to FALSE, skipping the configuration", precondExpr);
                    continue;
                }
            } catch (PexException e) {
                throw new DaoException("Error evaluating pex expression " + precondExpr, e);
            }

            if (eventConfig.getEventActionType() == EventActionType.ACTIVITY_INSTANCE_CREATION) {
                createActivityEvent(operatorId,
                        participantId,
                        instanceDto.getId(),
                        activityInstanceStatus,
                        studyActivityId,
                        studyId,
                        participantGuid,
                        eventConfig);
            } else if (eventConfig.getEventActionType() == EventActionType.USER_ENROLLED) {
                try {
                    getUserStudyEnrollmentDao().changeUserStudyEnrollmentStatus(
                            participantId,
                            studyId,
                            EnrollmentStatusType.ENROLLED);
                } catch (Exception e) {
                    throw new DaoException("Error updating participantId: "
                            + participantId + " in studyId: " + studyId + " to ENROLLED", e);
                }
            } else if (eventConfig.getEventActionType() == EventActionType.ANNOUNCEMENT) {
                processAnnouncementAction(getHandle(), participantId, studyId, eventConfig);
            } else if (eventConfig.getEventActionType() == EventActionType.COPY_ANSWER) {
                boolean successfulCopy = CopyAnswerService.getInstance().copyAnswerValue(eventConfig, instanceDto, operatorId,
                        getHandle());
                if (successfulCopy) {
                    LOG.info("Answer from instance GUID {} succesfully copied", instanceDto.getGuid());
                }

            }

        }
    }

    private void processAnnouncementAction(Handle handle, long participantId, long studyId, EventConfigurationDto eventConfig) {
        try {
            UserAnnouncementDao announcementDao = getUserAnnouncementDao();
            if (eventConfig.getAnnouncementCreateForProxies()) {
                List<Governance> governances = handle.attach(UserGovernanceDao.class)
                        .findActiveGovernancesByParticipantAndStudyIds(participantId, studyId)
                        .collect(Collectors.toList());
                if (!governances.isEmpty()) {
                    for (var governance : governances) {
                        long id = announcementDao.insert(governance.getProxyUserId(), studyId,
                                eventConfig.getAnnouncementMsgTemplateId(), eventConfig.getAnnouncementIsPermanent());
                        LOG.info("Created new announcement with id {} for proxy id {} (participant id {}) and study id {}",
                                id, governance.getProxyUserId(), participantId, studyId);
                    }
                } else {
                    LOG.error("Participant with id {} has no active proxies in study id {}."
                            + " Announcement with event configuration id {} will not be created.",
                            participantId, studyId, eventConfig.getEventConfigurationId());
                }
            } else {
                long id = announcementDao.insert(participantId, studyId,
                        eventConfig.getAnnouncementMsgTemplateId(), eventConfig.getAnnouncementIsPermanent());
                LOG.info("Created new announcement with id {} for participant id {} and study id {}", id, participantId, studyId);
            }
        } catch (Exception e) {
            throw new DaoException(String.format(
                    "Error while creating announcement for participant id %d, study id %d, event configuration id %d",
                    participantId, studyId, eventConfig.getEventConfigurationId()));
        }
    }

    default void createActivityEvent(long operatorId,
                                     long participantId,
                                     long activityInstanceId,
                                     String activityInstanceStatus,
                                     long studyActivityId,
                                     Long studyId,
                                     String participantGuid,
                                     EventConfigurationDto eventConfig) {
        PexInterpreter interp = new TreeWalkInterpreter();
        JdbiActivityInstance jdbiActivityInstance = getJdbiActivityInstance();

        // Checking if the maximum number of activities of this type is hit
        LOG.info(
                "Checking if the maximum number of activities (n = {}) for the study activity (id = {}) is hit",
                eventConfig.getMaxInstancesPerUser(), eventConfig.getActivityIdToCreate()
        );
        int numExistingActivities = jdbiActivityInstance.getNumActivitiesForParticipant(
                eventConfig.getActivityIdToCreate(),
                participantId
        );
        LOG.info(
                "Found {} existing activity instances for study activity {}",
                numExistingActivities, eventConfig.getActivityIdToCreate()
        );
        if (eventConfig.getMaxInstancesPerUser() != null && numExistingActivities >= eventConfig.getMaxInstancesPerUser()) {
            LOG.info(
                    "The number of existing study activities (n = {}) with id = {} is greater than or equal than"
                            + " the allowed maximum for this study activity (n = {}), skipping the configuration",
                    numExistingActivities,
                    eventConfig.getActivityIdToCreate(),
                    eventConfig.getMaxInstancesPerUser()
            );
            return;
        }

        // Checking if any of the conditions for the activity type evaluates to FALSE
        LOG.info("Checking if the activity creation expression exists for the study activity {}", studyActivityId);
        Optional<ActivityConditionDto> activityConditionDto = getJdbiActivityCondition().getById(eventConfig.getActivityIdToCreate());
        if (activityConditionDto.isPresent()) {
            String creationExpr = activityConditionDto.get().getCreationExpression();
            LOG.info("Checking the activity creation expression {}", creationExpr);
            boolean creationExprResult = true;
            try {
                if (creationExpr != null) {
                    creationExprResult = interp.eval(
                            creationExpr,
                            getHandle(),
                            participantGuid,
                            getJdbiActivityInstance().getActivityInstanceGuid(activityInstanceId)
                    );
                }
            } catch (PexException e) {
                throw new DaoException("Error evaluating pex expression " + creationExpr);
            }
            if (!creationExprResult) {
                LOG.info(
                        "The activity creation expression {} evaluated to FALSE, skipping the configuration",
                        activityConditionDto.get().getCreationExpression()
                );
                return;
            }
        } else {
            LOG.info("No activity creation expression found for the study activity {}", studyActivityId);
        }

        // All fine, creating an activity instance
        String activityInstanceGuid = jdbiActivityInstance.generateUniqueGuid();
        long newActivityInstanceId = jdbiActivityInstance.insert(
                eventConfig.getActivityIdToCreate(),
                participantId,
                activityInstanceGuid,
                false,
                Instant.now().toEpochMilli(),
                null
        );
        // Using the low-level facility to avoid infinite recursion
        getJdbiActivityStatus().insert(
                newActivityInstanceId,
                InstanceStatusType.CREATED,
                Instant.now().toEpochMilli(),
                participantId
        );
        // queue up any events associated with updating the status to created
        ActivityInstanceDto createdInstanceDto = getHandle().attach(JdbiActivityInstance.class).getByActivityInstanceId(
                newActivityInstanceId).get();
        addStatusTriggerEventsToQueue(operatorId, participantId, createdInstanceDto, InstanceStatusType.CREATED.name());


        // Incrementing the counter indicating that the event configuration has been executed
        getJdbiEventConfigurationOccurrenceCounter().incNumOccurrences(
                eventConfig.getEventConfigurationId(),
                participantId
        );
        LOG.info(
                "Performed the instantiation of the study activity with the id {} triggered by"
                        + " the activity instance {} transitioning to the status {}."
                        + " Operator = {}, participant = {}, study = {}, created activity instance id = {}",
                eventConfig.getActivityIdToCreate(), activityInstanceId, activityInstanceStatus,
                operatorId, participantId, studyId, newActivityInstanceId
        );
    }

    // Note: this should only be used in tests.
    @SqlUpdate("delete from activity_instance_status where activity_instance_id = "
            + "(select activity_instance_id from activity_instance where activity_instance_guid = ?)")
    int deleteAllByInstanceGuid(String instanceGuid);

    @SqlUpdate("delete from activity_instance_status where activity_instance_id in (<instanceIds>)")
    int deleteAllByInstanceIds(@BindList(value = "instanceIds", onEmpty = BindList.EmptyHandling.NULL) Set<Long> instanceIds);
}
