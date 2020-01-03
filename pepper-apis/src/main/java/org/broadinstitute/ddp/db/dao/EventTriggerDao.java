package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.jdbi.v3.sqlobject.CreateSqlObject;

public interface EventTriggerDao {

    @CreateSqlObject
    JdbiEventTrigger getJdbiEventTrigger();

    @CreateSqlObject
    JdbiActivityStatusTrigger getJdbiActivityStatusTrigger();

    @CreateSqlObject
    JdbiWorkflowStatusTrigger getJdbiWorkflowStatusTrigger();

    @CreateSqlObject
    JdbiDsmNotificationTrigger getJdbiDsmNotificationTrigger();

    @CreateSqlObject
    JdbiDsmNotificationEventType getJdbiDsmNotificationEventType();

    default long insertStatusTrigger(long activityId, InstanceStatusType statusType) {
        long triggerId = getJdbiEventTrigger().insert(EventTriggerType.ACTIVITY_STATUS);
        int numRowsInserted = getJdbiActivityStatusTrigger().insert(triggerId, activityId, statusType);
        if (numRowsInserted != 1) {
            throw new DaoException("Could not insert status trigger for activity id " + activityId + " and status type " + statusType);
        }
        return triggerId;
    }

    default long insertWorkflowTrigger(long workflowStateId) {
        long triggerId = getJdbiEventTrigger().insert(EventTriggerType.WORKFLOW_STATE);
        boolean insertedRow = getJdbiWorkflowStatusTrigger().insert(triggerId, workflowStateId, false);
        if (!insertedRow) {
            throw new DaoException("Could not insert workflow trigger for workflow state id " + workflowStateId);
        }
        return triggerId;
    }

    default long insertMailingListTrigger() {
        return getJdbiEventTrigger().insert(EventTriggerType.JOIN_MAILING_LIST);
    }

    default long insertDsmNotificationTrigger(String dsmEventType) {
        long triggerId = getJdbiEventTrigger().insert(EventTriggerType.DSM_NOTIFICATION);
        Long dsmEventTypeId = getJdbiDsmNotificationEventType().findIdByCode(dsmEventType).get();
        getJdbiDsmNotificationTrigger().insert(triggerId, dsmEventTypeId);
        return triggerId;
    }

    default long insertUserNotInStudyTrigger() {
        return getJdbiEventTrigger().insert(EventTriggerType.USER_NOT_IN_STUDY);
    }

    default long insertUserRegisteredTrigger() {
        return getJdbiEventTrigger().insert(EventTriggerType.USER_REGISTERED);
    }

    default long insertExitRequestTrigger() {
        return getJdbiEventTrigger().insert(EventTriggerType.EXIT_REQUEST);
    }

    default long insertStaticTrigger(EventTriggerType type) {
        if (type.isStatic()) {
            return getJdbiEventTrigger().insert(type);
        } else {
            throw new DaoException("Event trigger type " + type + " requires attributes other than the type");
        }
    }
}
