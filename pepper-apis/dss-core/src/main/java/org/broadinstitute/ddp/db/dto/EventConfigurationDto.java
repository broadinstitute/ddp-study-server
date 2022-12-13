package org.broadinstitute.ddp.db.dto;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.experimental.Accessors;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.dsm.DsmNotificationEventType;
import org.broadinstitute.ddp.model.event.NotificationServiceType;
import org.broadinstitute.ddp.model.event.NotificationType;
import org.broadinstitute.ddp.model.event.PdfAttachment;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

/**
 * A DTO representing the event configuration, left joined with possible trigger and action configuration info
 */
@Value
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class EventConfigurationDto {
    long eventConfigurationId;
    String label;
    EventTriggerType eventTriggerType;
    EventActionType eventActionType;
    Integer postDelaySeconds;

    @Accessors(fluent = true)
    @ColumnName("dispatch_to_housekeeping")
    boolean dispatchToHousekeeping;
    String preconditionExpression;
    String cancelExpression;
    Integer maxOccurrencesPerUser;
    int executionOrder;
    String gcpTopic; // FIXME should not be a base field of EventAction

    /**
     * Triggers
     **/
    /* ACTIVITY_STATUS */
    InstanceStatusType instanceStatusType;
    Long activityStatusTriggerStudyActivityId;

    /* WORKFLOW_STATE */
    Long workflowStateId;
    Boolean triggerAutomatically;

    /* DSM_NOTIFICATION */
    DsmNotificationEventType dsmNotificationEventType;

    /* USER_STATUS_CHANGE */
    EnrollmentStatusType userStatusChangedTargetStatusType;

    /* UPDATE_USER_STATUS */
    EnrollmentStatusType updateUserStatusTargetStatusType;

    /**
     * Actions
     **/
    /* ANNOUNCEMENT */
    Long announcementMsgTemplateId;

    @Accessors(fluent = true)
    @ColumnName("announcement_is_permanent")
    Boolean isAnnouncementPermanent;

    @Accessors(fluent = true)
    @ColumnName("announcement_create_for_proxies")
    Boolean shouldCreateAnnouncementForProxies;

    /* NOTIFICATION */
    // Currently, notification templates are not needed yet in event configuration
    // but is used in queued notification instead.
    NotificationType notificationType;
    NotificationServiceType notificationServiceType;
    Long linkedActivityId;

    /* PDF_GENERATION */
    Long pdfGenerationDocumentConfigurationId;

    /* ACTIVITY_INSTANCE_CREATION */
    Long activityInstanceCreationStudyActivityId;

    /* COPY_ANSWER */
    Long copyActionCopyConfigurationId;

    /* CREATE_INVITATION */
    Long contactEmailQuestionStableCodeId;
    String contactEmailQuestionStableId;

    @Accessors(fluent = true)
    @ColumnName("mark_existing_invitations_as_voided")
    Boolean shouldMarkExistingInvitationsAsVoided;

    String customWorkflowName;
    String customWorkflowStatus;

    @Accessors(fluent = true)
    boolean createFromAnswer;
    String sourceQuestionStableId;
    String targetQuestionStableId;

    List<PdfAttachment> notificationPdfAttachments = new ArrayList<>();

    /* MARK_ACTIVITIES_READ_ONLY, HIDE_ACTIVITIES */
    Set<Long> targetActivityIds = new HashSet<>();

    /* CREATE_KIT kit_type_id */
    Long kitTypeId;

    public void addNotificationPdfAttachment(Long pdfDocumentConfigurationId, Boolean alwaysGenerate) {
        notificationPdfAttachments.add(new PdfAttachment(pdfDocumentConfigurationId, alwaysGenerate));
    }

    public void addTargetActivityId(long activityId) {
        targetActivityIds.add(activityId);
    }
}
