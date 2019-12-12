package org.broadinstitute.ddp.db.dto;

public class ActivityInstanceDto {

    private long id;
    private String guid;
    private long activityId;
    private long participantId;
    private long createdAtMillis;
    private boolean isReadonly;
    private String statusTypeCode;
    private Long onDemandTriggerId;
    private long submissionId;
    private String sessionId;
    private Long firstCompletedAt;
    private boolean allowUnauthenticated;

    /**
     * Instantiate ActivityInstanceDto object.
     */
    public ActivityInstanceDto(
            long id,
            String guid,
            long activityId,
            long participantId,
            long createdAtMillis,
            Long firstCompletedAt,
            boolean isReadonly,
            String statusTypeCode,
            Long onDemandTriggerId,
            boolean allowUnauthenticated
    ) {
        this.id = id;
        this.guid = guid;
        this.activityId = activityId;
        this.participantId = participantId;
        this.createdAtMillis = createdAtMillis;
        this.firstCompletedAt = firstCompletedAt;
        this.isReadonly = isReadonly;
        this.statusTypeCode = statusTypeCode;
        this.onDemandTriggerId = onDemandTriggerId;
        this.allowUnauthenticated = allowUnauthenticated;
    }

    /**
     * Instantiate ActivityInstanceDto object.
     */
    public ActivityInstanceDto(long id,
                               String guid,
                               long activityId,
                               long participantId,
                               long createdAtMillis,
                               Long firstCompletedAt,
                               boolean isReadonly,
                               String statusTypeCode,
                               long submissionId,
                               String sessionId) {
        this.id = id;
        this.guid = guid;
        this.activityId = activityId;
        this.participantId = participantId;
        this.createdAtMillis = createdAtMillis;
        this.firstCompletedAt = firstCompletedAt;
        this.isReadonly = isReadonly;
        this.statusTypeCode = statusTypeCode;
        this.submissionId = submissionId;
        this.sessionId = sessionId;
    }

    public long getId() {
        return id;
    }

    public String getGuid() {
        return guid;
    }

    public long getActivityId() {
        return activityId;
    }

    public long getParticipantId() {
        return participantId;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    public Long getFirstCompletedAt() {
        return firstCompletedAt;
    }

    public boolean isReadonly() {
        return isReadonly;
    }

    public String getStatusTypeCode() {
        return statusTypeCode;
    }

    public Long getOnDemandTriggerId() {
        return onDemandTriggerId;
    }

    public long getSubmissionId() {
        return submissionId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public boolean isAllowUnauthenticated() {
        return allowUnauthenticated;
    }
}
