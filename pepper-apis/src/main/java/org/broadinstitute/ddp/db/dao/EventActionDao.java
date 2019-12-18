package org.broadinstitute.ddp.db.dao;

import static org.broadinstitute.ddp.model.event.MessageDestination.PARTICIPANT_NOTIFICATION;

import java.util.Optional;

import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dto.CopyAnswerEventActionDto;
import org.broadinstitute.ddp.db.dto.SendgridEmailEventActionDto;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.event.CopyAnswerTarget;
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
    JdbiActivityInstanceCreationAction getJdbiActivityInstanceCreationAction();

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
    JdbiCopyAnswerEventAction getJdbiCopyAnswerEventAction();


    default long insertStudyNotificationAction(SendgridEmailEventActionDto sendgridTemplateDto) {
        return _insertNotificationAction(sendgridTemplateDto, NotificationType.STUDY_EMAIL);
    }

    default long insertNotificationAction(SendgridEmailEventActionDto eventAction) {
        return _insertNotificationAction(eventAction, NotificationType.EMAIL);
    }

    default long _insertNotificationAction(SendgridEmailEventActionDto eventAction, NotificationType notificationType) {
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

    default long insertInstanceCreationAction(long targetActivityId) {
        long actionId = getJdbiEventAction().insert(null, EventActionType.ACTIVITY_INSTANCE_CREATION);
        int numRowsInserted = getJdbiActivityInstanceCreationAction().insert(actionId, targetActivityId);
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

    default long insertPdfGenerationAction(long pdfConfigurationId) {
        long messageDestinationId = getMessageDestinationDao().findByTopic(PARTICIPANT_NOTIFICATION);
        long actionId = getJdbiEventAction().insert(messageDestinationId, EventActionType.PDF_GENERATION);
        int numInserted = getJdbiPdfGenerationEventAction().insert(actionId, pdfConfigurationId);
        if (numInserted != 1) {
            throw new DaoException("Could not insert pdf generation event action with pdf document config id " + pdfConfigurationId);
        }
        return actionId;
    }

    default long insertCopyAnswerAction(long studyId, String copySourceStableCode, CopyAnswerTarget copyTarget) {
        long actionId = getJdbiEventAction().insert(null, EventActionType.COPY_ANSWER);
        JdbiQuestionStableCode jdbiQuestionStableCode = getHandle().attach(JdbiQuestionStableCode.class);
        Optional<Long> stableCodeDbId = jdbiQuestionStableCode.getIdForStableId(copySourceStableCode, studyId);
        if (!stableCodeDbId.isPresent()) {
            throw new DaoException("Could not find question stable db id for stable id:" + copySourceStableCode
                    + " and studyId " + studyId);
        }
        int numInserted = getJdbiCopyAnswerEventAction().insert(actionId, stableCodeDbId.get(), copyTarget);
        if (numInserted != 1) {
            throw new DaoException("Could not insert copy event action with target:" + copyTarget + " and source stable id: "
                    + copySourceStableCode);
        }
        return actionId;
    }

    default CopyAnswerEventActionDto findCopyAnswerAction(long eventId) {
        return getJdbiCopyAnswerEventAction().findById(eventId);
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
}
