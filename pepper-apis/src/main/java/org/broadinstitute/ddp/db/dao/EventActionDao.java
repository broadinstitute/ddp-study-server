package org.broadinstitute.ddp.db.dao;

import static org.broadinstitute.ddp.model.event.MessageDestination.PARTICIPANT_NOTIFICATION;

import java.util.Set;

import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dto.SendgridEmailEventActionDto;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.event.CopyConfiguration;
import org.broadinstitute.ddp.model.event.NotificationServiceType;
import org.broadinstitute.ddp.model.event.NotificationType;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;

public interface EventActionDao extends SqlObject {

    @CreateSqlObject
    JdbiNotificationTemplate getNotificationTemplate();

    @CreateSqlObject
    JdbiLanguageCode getLanguageCodeDao();

    @CreateSqlObject
    JdbiEventAction getJdbiEventAction();

    @CreateSqlObject
    JdbiMessageDestination getMessageDestinationDao();

    @CreateSqlObject
    JdbiNotificationService getNotificationServiceDao();

    @CreateSqlObject
    JdbiNotificationType getNotificationTypeDao();

    @CreateSqlObject
    JdbiUserNotificationEventAction getNotificationEventActionDao();

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
        long notificationServiceId = getNotificationServiceDao().findByType(NotificationServiceType.SENDGRID);
        long notificationTypeId = getNotificationTypeDao().findByType(notificationType);
        long languageCodeId = getLanguageCodeDao().getLanguageCodeId(eventAction.getLanguageCode());

        JdbiNotificationTemplate notificationTemplateDao = getNotificationTemplate();
        JdbiEventAction eventActionDao = getJdbiEventAction();
        JdbiUserNotificationEventAction notificationEventActionDao = getNotificationEventActionDao();

        String templateKey = eventAction.getTemplateKey();
        long notificationTemplateId = notificationTemplateDao
                .findByKeyAndLanguage(templateKey, languageCodeId)
                .orElseGet(() -> notificationTemplateDao.insert(templateKey, languageCodeId));

        long eventActionId = eventActionDao.insert(messageDestinationId, EventActionType.NOTIFICATION);

        int numRowsInserted = notificationEventActionDao.insert(eventActionId, notificationTypeId,
                notificationServiceId, notificationTemplateId, eventAction.getLinkedActivityId());
        if (numRowsInserted != 1) {
            throw new DaoException("Could not insert user notification event");
        }

        return eventActionId;
    }

    default long insertInstanceCreationAction(long targetActivityId, CopyConfiguration config) {
        long actionId = getJdbiEventAction().insert(null, EventActionType.ACTIVITY_INSTANCE_CREATION);
        Long configId = config == null ? null : getCopyConfigurationDao().createCopyConfig(config).getId();
        int numRowsInserted = getEventActionSql().insertActivityInstanceCreationAction(actionId, targetActivityId, configId);
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
