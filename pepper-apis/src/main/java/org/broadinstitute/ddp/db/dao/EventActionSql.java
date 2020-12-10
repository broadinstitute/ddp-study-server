package org.broadinstitute.ddp.db.dao;

import java.util.Optional;
import java.util.Set;

import org.broadinstitute.ddp.model.event.NotificationServiceType;
import org.broadinstitute.ddp.model.event.NotificationTemplate;
import org.broadinstitute.ddp.model.event.NotificationType;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlBatch;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface EventActionSql extends SqlObject {

    @SqlUpdate("insert into activity_instance_creation_action (activity_instance_creation_action_id, study_activity_id)"
            + " values (:actionId, :activityId)")
    int insertActivityInstanceCreationAction(@Bind("actionId") long eventActionId, @Bind("activityId") long activityId);

    @SqlUpdate("insert into copy_answer_event_action (event_action_id, copy_configuration_id) values (:actionId, :copyConfigId)")
    int insertCopyAnswerAction(@Bind("actionId") long eventActionId, @Bind("copyConfigId") long copyConfigId);

    @SqlUpdate("insert into create_invitation_event_action"
            + "        (event_action_id, contact_email_question_stable_code_id, mark_existing_as_voided)"
            + " values (:actionId, :qscId, :markExistingAsVoided)")
    int insertCreateInvitationAction(
            @Bind("actionId") long actionId,
            @Bind("qscId") long contactEmailQuestionStableCodeId,
            @Bind("markExistingAsVoided") boolean markExistingAsVoided);

    @GetGeneratedKeys
    @SqlBatch("insert into event_action_target_activity (event_action_id, activity_id) values (:actionId, :activityId)")
    long[] insertTargetActivities(@Bind("actionId") long actionId, @Bind("activityId") Set<Long> targetActivityIds);

    @SqlUpdate("delete from activity_instance_creation_action where activity_instance_creation_action_id = :actionId")
    int deleteActivityInstanceCreationAction(@Bind("actionId") long eventActionId);

    @SqlUpdate("delete from copy_answer_event_action where event_action_id = :actionId")
    int deleteCopyAnswerAction(@Bind("actionId") long eventActionId);

    //
    // User notification action
    //

    @SqlUpdate("insert into user_notification_event_action ("
            + "        user_notification_event_action_id, notification_type_id, notification_service_id, linked_activity_id)"
            + " values (:actionId,"
            + "        (select notification_type_id from notification_type where notification_type_code = :notificationType),"
            + "        (select notification_service_id from notification_service where service_code = :notificationService),"
            + "        :linkedActivityId)")
    int insertUserNotificationAction(
            @Bind("actionId") long eventActionId,
            @Bind("notificationType") NotificationType notificationType,
            @Bind("notificationService") NotificationServiceType notificationServiceType,
            @Bind("linkedActivityId") Long linkedActivityId,
            @Bind("allowExternalAttachments") Boolean allowExternalAttachments);

    @GetGeneratedKeys
    @SqlUpdate("insert into notification_template (template_key, language_code_id, is_dynamic)"
            + " values (:tmplKey, (select language_code_id from language_code where iso_language_code = :langCode), :isDynamic)")
    long insertNotificationTemplate(@Bind("tmplKey") String templateKey, @Bind("langCode") String isoLanguageCode,
                                    @Bind("isDynamic") boolean isDynamic);

    @SqlQuery("select nt.*, lc.iso_language_code from notification_template as nt"
            + "  join language_code as lc on lc.language_code_id = nt.language_code_id"
            + " where nt.template_key = :tmplKey and lc.iso_language_code = :langCode")
    @RegisterConstructorMapper(NotificationTemplate.class)
    Optional<NotificationTemplate> findNotificationTemplate(
            @Bind("tmplKey") String templateKey,
            @Bind("langCode") String isoLanguageCode);

    default long findOrInsertNotificationTemplateId(String templateKey, String langCode, boolean isDynamic) {
        return findNotificationTemplate(templateKey, langCode)
                .map(NotificationTemplate::getId)
                .orElseGet(() -> insertNotificationTemplate(templateKey, langCode, isDynamic));
    }

    @GetGeneratedKeys
    @SqlBatch("insert into user_notification_template (user_notification_event_action_id, notification_template_id)"
            + " values (:actionId, :tmplId)")
    long[] bulkAddNotificationTemplatesToAction(
            @Bind("actionId") long eventActionId,
            @Bind("tmplId") Set<Long> notificationTemplateIds);
}
