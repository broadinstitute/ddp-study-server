package org.broadinstitute.ddp.json.admin.participantslookup;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;

/**
 * One participant lookup result data
 */
public abstract class ParticipantsLookupResultRowBase extends ResultRowBase {

    @SerializedName("invitationId")
    protected String invitationId;

    @SerializedName("status")
    protected EnrollmentStatusType status;

    @SerializedName("legacyAltPid")
    protected String legacyAltPid;

    @SerializedName("legacyShortId")
    protected String legacyShortId;

    public ParticipantsLookupResultRowBase() {
    }

    public ParticipantsLookupResultRowBase(ResultRowBase resultRowBase) {
        super(resultRowBase);
        if (resultRowBase instanceof ParticipantsLookupResultRowBase) {
            ParticipantsLookupResultRowBase resultRow = (ParticipantsLookupResultRowBase)resultRowBase;
            invitationId = resultRow.invitationId;
            status = resultRow.status;
            legacyAltPid = resultRow.legacyAltPid;
            legacyShortId = resultRow.legacyShortId;
        }
    }

    public String getInvitationId() {
        return invitationId;
    }

    public void setInvitationId(String invitationId) {
        this.invitationId = invitationId;
    }

    public EnrollmentStatusType getStatus() {
        return status;
    }

    public void setStatus(EnrollmentStatusType status) {
        this.status = status;
    }

    public String getLegacyAltPid() {
        return legacyAltPid;
    }

    public void setLegacyAltPid(String legacyAltPid) {
        this.legacyAltPid = legacyAltPid;
    }

    public String getLegacyShortId() {
        return legacyShortId;
    }

    public void setLegacyShortId(String legacyShortId) {
        this.legacyShortId = legacyShortId;
    }
}
