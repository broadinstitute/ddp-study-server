package org.broadinstitute.ddp.db.dao;

import static org.broadinstitute.ddp.model.event.MessageDestination.PARTICIPANT_NOTIFICATION;

import java.util.Set;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dto.SendgridEmailEventActionDto;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.copy.CopyConfiguration;
import org.broadinstitute.ddp.model.event.NotificationServiceType;
import org.broadinstitute.ddp.model.event.NotificationType;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;

public interface EventActionDao extends SqlObject {

    @CreateSqlObject
    JdbiEventAction getJdbiEventAction();

    @CreateSqlObject
    JdbiMessageDestination getMessageDestinationDao();

    @CreateSqlObject
    JdbiUserAnnouncementEventAction getJdbiUserAnnouncementEventAction();

    @CreateSqlObject
    JdbiPdfGenerationEventAction getJdbiPdfGenerationEventAction();

    @CreateSqlObject
    EventActionSql getEventActionSql();

    @CreateSqlObject
    CopyConfigurationDao getCopyConfigurationDao();

    default long insertInvitationEmailNotificationAction(SendgridEmailEventActionDto sendgridTemplateDto) {
        return insertNotificationAction(sendgridTemplateDto, NotificationType.INVITATION_EMAIL);
    }

    default long insertStudyNotificationAction(SendgridEmailEventActionDto sendgridTemplateDto) {
        return insertNotificationAction(sendgridTemplateDto, NotificationType.STUDY_EMAIL);
    }

    default long insertNotificationAction(SendgridEmailEventActionDto eventAction) {
        return insertNotificationAction(eventAction, NotificationType.EMAIL);
    }

    private long insertNotificationAction(SendgridEmailEventActionDto eventAction, NotificationType notificationType) {
        long messageDestinationId = getMessageDestinationDao().findByTopic(PARTICIPANT_NOTIFICATION);
        EventActionSql eventActionSql = getEventActionSql();

        long actionId = getJdbiEventAction().insert(messageDestinationId, EventActionType.NOTIFICATION);
        DBUtils.checkInsert(1, eventActionSql.insertUserNotificationAction(
                actionId, notificationType, NotificationServiceType.SENDGRID, eventAction.getLinkedActivityId(),
                eventAction.getAllowExternalAttachments()));

        Set<Long> notificationTemplateIds = eventAction.getTemplates().stream()
                .map(tmpl -> eventActionSql.findOrInsertNotificationTemplateId(tmpl.getTemplateKey(), tmpl.getLanguageCode(),
                        tmpl.isDynamicTemplate()))
                .collect(Collectors.toSet());

        long[] numInserted = eventActionSql.bulkAddNotificationTemplatesToAction(actionId, notificationTemplateIds);
        DBUtils.checkInsert(notificationTemplateIds.size(), numInserted.length);

        return actionId;
    }

    default long insertInstanceCreationAction(long targetActivityId) {
        long actionId = getJdbiEventAction().insert(null, EventActionType.ACTIVITY_INSTANCE_CREATION);
        int numRowsInserted = getEventActionSql().insertActivityInstanceCreationAction(actionId, targetActivityId);
        if (numRowsInserted != 1) {
            throw new DaoException("Could not insert activity instance creation event for target activity id " + targetActivityId);
        }
        return actionId;
    }

    default long insertEnrolledAction() {
        return getJdbiEventAction().insert(null, EventActionType.USER_ENROLLED);
    }

    default long insertAnnouncementAction(long msgTemplateId, boolean isPermanent, boolean createForProxies) {
        long actionId = getJdbiEventAction().insert(null, EventActionType.ANNOUNCEMENT);
        int numInserted = getJdbiUserAnnouncementEventAction().insert(actionId, msgTemplateId, isPermanent, createForProxies);
        if (numInserted != 1) {
            throw new DaoException("Could not insert announcement event action with template id " + msgTemplateId);
        }
        return actionId;
    }

    default long insertCreateInvitationAction(long studyId, String contactEmailQuestionStableId, boolean markExistingAsVoided) {
        long actionId = getJdbiEventAction().insert(null, EventActionType.CREATE_INVITATION);
        long stableCodeId = getHandle().attach(JdbiQuestionStableCode.class)
                .getIdForStableId(contactEmailQuestionStableId, studyId)
                .orElseThrow(() -> new DaoException("Could not find question stable code id for stableId "
                        + contactEmailQuestionStableId + " and studyId " + studyId));
        DBUtils.checkInsert(1, getEventActionSql().insertCreateInvitationAction(actionId, stableCodeId, markExistingAsVoided));
        return actionId;
    }

    default long insertHideActivitiesAction(Set<Long> activityIds) {
        long actionId = getJdbiEventAction().insert(null, EventActionType.HIDE_ACTIVITIES);
        long[] ids = getEventActionSql().insertTargetActivities(actionId, activityIds);
        DBUtils.checkInsert(activityIds.size(), ids.length);
        return actionId;
    }

    default long insertMarkActivitiesReadOnlyAction(Set<Long> activityIds) {
        long actionId = getJdbiEventAction().insert(null, EventActionType.MARK_ACTIVITIES_READ_ONLY);
        long[] ids = getEventActionSql().insertTargetActivities(actionId, activityIds);
        DBUtils.checkInsert(activityIds.size(), ids.length);
        return actionId;
    }

    default long insertPdfGenerationAction(long pdfConfigurationId) {
        long messageDestinationId = getMessageDestinationDao().findByTopic(PARTICIPANT_NOTIFICATION);
        long actionId = getJdbiEventAction().insert(messageDestinationId, EventActionType.PDF_GENERATION);
        int numInserted = getJdbiPdfGenerationEventAction().insert(actionId, pdfConfigurationId);
        if (numInserted != 1) {
            throw new DaoException("Could not insert pdf generation event action with pdf document config id " + pdfConfigurationId);
        }
        return actionId;
    }

    default long insertCopyAnswerAction(CopyConfiguration config) {
        long actionId = getJdbiEventAction().insert(null, EventActionType.COPY_ANSWER);
        long configId = getCopyConfigurationDao().createCopyConfig(config).getId();
        int numInserted = getEventActionSql().insertCopyAnswerAction(actionId, configId);
        if (numInserted != 1) {
            throw new DaoException("Could not insert copy event action with copy configuration id " + configId);
        }
        return actionId;
    }

    default void deleteAnnouncementAction(long eventActionId) {
        int numDeleted = getJdbiUserAnnouncementEventAction().deleteById(eventActionId);
        if (numDeleted != 1) {
            throw new DaoException("Could not delete related data for announcement action id " + eventActionId);
        }

        numDeleted = getJdbiEventAction().deleteById(eventActionId);
        if (numDeleted != 1) {
            throw new DaoException("Could not delete announcement event action id " + eventActionId);
        }
    }

    default long insertStaticAction(EventActionType type) {
        if (type.isStatic()) {
            return getJdbiEventAction().insert(null, type);
        } else {
            throw new DaoException("Event action type " + type + " requires attributes other than the type");
        }
    }
}
