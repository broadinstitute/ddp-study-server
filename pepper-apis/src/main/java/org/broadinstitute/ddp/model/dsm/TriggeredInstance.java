package org.broadinstitute.ddp.model.dsm;

import java.beans.ConstructorProperties;

import com.google.gson.annotations.SerializedName;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;


/**
 * Represents data expected by DSM for an activity instance that was triggered in an on-demand fashion.
 */
public class TriggeredInstance {

    @SerializedName("participantId")
    private String participantId;

    @SerializedName("shortId")
    private String participantHruid;

    @SerializedName("legacyShortId")
    private String legacyShortId;

    @SerializedName("survey")
    private String activityCode;

    @SerializedName("followUpInstance")
    private String instanceGuid;

    @SerializedName("surveyStatus")
    private TriggeredInstanceStatusType status;

    @SerializedName("surveyQueued")
    private long createdAtSec;

    @SerializedName("triggerId")
    private long triggerId;

    private transient String participantGuid;
    private transient String legacyAltpid;

    @ConstructorProperties({"participant_guid", "participant_hruid", "legacy_shortid", "legacy_altpid",
            "activity_code", "instance_guid", "instance_status", "created_at_sec", "trigger_id"})
    public TriggeredInstance(String participantGuid, String participantHruid, String legacyShortId, String legacyAltpid,
                             String activityCode, String instanceGuid,
                             InstanceStatusType status, long createdAtSec, long triggerId) {
        this.participantGuid = participantGuid;
        this.participantHruid = participantHruid;
        this.legacyAltpid = legacyAltpid;
        this.legacyShortId = legacyShortId;
        this.activityCode = activityCode;
        this.instanceGuid = instanceGuid;
        this.status = TriggeredInstanceStatusType.valueOf(status);
        this.createdAtSec = createdAtSec;
        this.triggerId = triggerId;
        if (StringUtils.isBlank(legacyAltpid)) {
            this.participantId = participantGuid;
        } else {
            this.participantId = legacyAltpid;
        }
    }

    public String getParticipantId() {
        return participantId;
    }

    public String getParticipantGuid() {
        return participantGuid;
    }

    public String getParticipantHruid() {
        return participantHruid;
    }

    public String getLegacyShortId() {
        return legacyShortId;
    }

    public String getLegacyAltpid() {
        return legacyAltpid;
    }

    public String getActivityCode() {
        return activityCode;
    }

    public String getInstanceGuid() {
        return instanceGuid;
    }

    public TriggeredInstanceStatusType getStatus() {
        return status;
    }

    public long getCreatedAtSec() {
        return createdAtSec;
    }

    public long getTriggerId() {
        return triggerId;
    }
}
