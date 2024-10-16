package org.broadinstitute.ddp.db.dao;

import java.util.Set;

import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.notficationevent.DsmNotificationEventType;
import org.jdbi.v3.sqlobject.CreateSqlObject;

public interface EventTriggerDao {

    @CreateSqlObject
    EventTriggerSql getEventTriggerSql();

    @CreateSqlObject
    JdbiActivityStatusTrigger getJdbiActivityStatusTrigger();

    @CreateSqlObject
    JdbiWorkflowStatusTrigger getJdbiWorkflowStatusTrigger();

    default long insertStatusTrigger(long activityId, InstanceStatusType statusType) {
        long triggerId = getEventTriggerSql().insertBaseTrigger(EventTriggerType.ACTIVITY_STATUS);
        int numRowsInserted = getJdbiActivityStatusTrigger().insert(triggerId, activityId, statusType);
        if (numRowsInserted != 1) {
            throw new DaoException("Could not insert status trigger for activity id " + activityId + " and status type " + statusType);
        }
        return triggerId;
    }

    default long insertWorkflowTrigger(long workflowStateId) {
        long triggerId = getEventTriggerSql().insertBaseTrigger(EventTriggerType.WORKFLOW_STATE);
        boolean insertedRow = getJdbiWorkflowStatusTrigger().insert(triggerId, workflowStateId, false);
        if (!insertedRow) {
            throw new DaoException("Could not insert workflow trigger for workflow state id " + workflowStateId);
        }
        return triggerId;
    }

    default long insertMailingListTrigger() {
        return getEventTriggerSql().insertBaseTrigger(EventTriggerType.JOIN_MAILING_LIST);
    }

    default long insertDsmNotificationTrigger(DsmNotificationEventType dsmEventType) {
        var eventTriggerSql = getEventTriggerSql();
        long triggerId = eventTriggerSql.insertBaseTrigger(EventTriggerType.DSM_NOTIFICATION);
        DBUtils.checkInsert(1, eventTriggerSql.insertDsmNotificationTrigger(triggerId, dsmEventType));
        return triggerId;
    }

    default long insertUserNotInStudyTrigger() {
        return getEventTriggerSql().insertBaseTrigger(EventTriggerType.USER_NOT_IN_STUDY);
    }

    default long insertUserRegisteredTrigger() {
        return getEventTriggerSql().insertBaseTrigger(EventTriggerType.USER_REGISTERED);
    }

    default long insertUserStatusChangedTrigger(EnrollmentStatusType targetStatusType) {
        var supported = Set.of(EnrollmentStatusType.ENROLLED, EnrollmentStatusType.COMPLETED);
        if (!supported.contains(targetStatusType)) {
            throw new DaoException("Target status '" + targetStatusType + "' is currently not supported");
        }
        var eventTriggerSql = getEventTriggerSql();
        long triggerId = eventTriggerSql.insertBaseTrigger(EventTriggerType.USER_STATUS_CHANGED);
        DBUtils.checkInsert(1, eventTriggerSql.insertUserStatusChangedTrigger(triggerId, targetStatusType));
        return triggerId;
    }

    default long insertExitRequestTrigger() {
        return getEventTriggerSql().insertBaseTrigger(EventTriggerType.EXIT_REQUEST);
    }

    default long insertStaticTrigger(EventTriggerType type) {
        if (type.isStatic()) {
            return getEventTriggerSql().insertBaseTrigger(type);
        } else {
            throw new DaoException("Event trigger type " + type + " requires attributes other than the type");
        }
    }
}
