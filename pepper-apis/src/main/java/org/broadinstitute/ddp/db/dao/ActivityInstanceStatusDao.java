package org.broadinstitute.ddp.db.dao;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.ActivityInstanceStatusDto;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.event.ActivityInstanceStatusChangeSignal;
import org.broadinstitute.ddp.model.event.EventSignal;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.service.EventService;
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
     * @param newStatus         new status of activity instance
     * @param epochMilliseconds time status is updated in milliseconds
     * @param operatorGuid      guid of operator
     * @return the most recent status
     */
    default ActivityInstanceStatusDto insertStatus(long instanceId, InstanceStatusType newStatus, long epochMilliseconds,
                                                   String operatorGuid) {
        long operatorId = getJdbiUser().getUserIdByGuid(operatorGuid);

        ActivityInstanceDto instanceDto = getJdbiActivityInstance().getByActivityInstanceId(instanceId)
                .orElseThrow(() -> new DaoException("Could not find activity instance with id " + instanceId));

        User participantUser = getHandle().attach(UserDao.class).findUserById(instanceDto.getParticipantId())
                .orElseThrow(() -> new DaoException("Cound not find User participant with id:" + instanceDto.getParticipantId()));
        updateOrInsertStatus(instanceDto, newStatus, epochMilliseconds, operatorId, participantUser);
        return getCurrentStatus(instanceDto.getId()).orElse(null);
    }

    /**
     * Inserts status based on given parameters, returns id of status. If the most recent status for this activity
     * is the same status code, it will not update (adding a new row to the db) and will return the most
     * recent status id. Also checks if there are any housekeeping events that should be queued in response to this
     * status change and queues them up for housekeeping.
     *
     * @return the most recent status
     */
    default void updateOrInsertStatus(ActivityInstanceDto instanceDto, InstanceStatusType newStatusType, long epochMilliseconds,
                                      long operatorId, User participantUser) {

        Optional<ActivityInstanceStatusDto> instanceStatusOptional = getCurrentStatus(instanceDto.getId());

        if (instanceStatusOptional.isPresent()) {
            ActivityInstanceStatusDto instanceStatus = instanceStatusOptional.get();
            if (instanceStatus.getType() == newStatusType) {
                updateStatusTimestamp(instanceStatusOptional.get(), epochMilliseconds);
                return;
            }
            if (instanceStatus.getType() == InstanceStatusType.COMPLETE) {
                return;
            }
        }


        getJdbiActivityStatus().insert(instanceDto.getId(), newStatusType, epochMilliseconds, operatorId);
        if (newStatusType == InstanceStatusType.COMPLETE) {
            int rowsUpdated = getJdbiActivityInstance().updateFirstCompletedAtIfNotSet(instanceDto.getId(), epochMilliseconds);
            if (rowsUpdated > 1) {
                throw new DaoException("Multiple entries updated when updating firstCompletedAt timestamp for"
                        + " activity instance with id " + instanceDto.getId());
            }
        }

        EventSignal eventSignal = new ActivityInstanceStatusChangeSignal(
                operatorId,
                participantUser.getId(),
                participantUser.getGuid(),
                instanceDto.getId(),
                instanceDto.getActivityId(),
                instanceDto.getStudyId(),
                newStatusType);

        EventService.getInstance().processAllActionsForEventSignal(
                getHandle(),
                eventSignal);

    }

    default ActivityInstanceStatusDto updateStatusTimestamp(ActivityInstanceStatusDto statusDto, long newTimestampMillis) {
        int rowsUpdated = getJdbiActivityStatus().updateTimestampByStatusId(statusDto.getId(), newTimestampMillis);
        if (rowsUpdated != 1) {
            throw new DaoException("Could not update timestamp for activity instance status with id " + statusDto.getId());
        }
        return new ActivityInstanceStatusDto(statusDto.getId(), 0, statusDto.getInstanceId(),
                statusDto.getOperatorId(), newTimestampMillis, statusDto.getType());
    }


    // Note: this should only be used in tests.
    @SqlUpdate("delete from activity_instance_status where activity_instance_id = "
            + "(select activity_instance_id from activity_instance where activity_instance_guid = ?)")
    int deleteAllByInstanceGuid(String instanceGuid);

    @SqlUpdate("delete from activity_instance_status where activity_instance_id in (<instanceIds>)")
    int deleteAllByInstanceIds(@BindList(value = "instanceIds", onEmpty = BindList.EmptyHandling.NULL) Set<Long> instanceIds);
}
