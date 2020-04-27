package org.broadinstitute.ddp.export.json.structured;

import java.util.List;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;

public class ActivityInstanceRecord {

    @SerializedName("activityCode")
    private String activityCode;
    @SerializedName("activityVersion")
    private String versionTag;
    @SerializedName("guid")
    private String activityInstanceGuid;
    @SerializedName("status")
    private InstanceStatusType status;
    @SerializedName("createdAt")
    private long createdAtMillis;
    @SerializedName("completedAt")
    private Long completedAtMillis;
    @SerializedName("lastUpdatedAt")
    private Long lastUpdatedAtMillis;
    @SerializedName("questionsAnswers")
    private List<QuestionRecord> questionsAnswers;

    public ActivityInstanceRecord(
            String versionTag,
            String activityCode,
            String activityInstanceGuid,
            InstanceStatusType status,
            long createdAtMillis,
            Long completedAtMillis,
            Long lastUpdatedAtMillis,
            List<QuestionRecord> questionsAnswers
    ) {
        this.versionTag = versionTag;
        this.activityCode = activityCode;
        this.activityInstanceGuid = activityInstanceGuid;
        this.status = status;
        this.createdAtMillis = createdAtMillis;
        this.completedAtMillis = completedAtMillis;
        this.lastUpdatedAtMillis = lastUpdatedAtMillis;
        this.questionsAnswers = questionsAnswers;
    }
}
