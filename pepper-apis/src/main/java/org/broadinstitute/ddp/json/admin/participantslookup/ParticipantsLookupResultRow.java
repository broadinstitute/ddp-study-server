package org.broadinstitute.ddp.json.admin.participantslookup;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;

/**
 * One participant lookup result data
 */
public class ParticipantsLookupResultRow extends ParticipantsLookupResultRowBase {

    @SerializedName("invitationId")
    protected String invitationId;

    @SerializedName("status")
    private EnrollmentStatusType status;

    @SerializedName("proxy")
    protected ParticipantsLookupResultRowBase proxy;

    public ParticipantsLookupResultRow() {
    }

    public ParticipantsLookupResultRow(ParticipantsLookupResultRowBase resultRowBase) {
        super(resultRowBase);
    }

    public ParticipantsLookupResultRowBase getProxy() {
        return proxy;
    }

    public void setProxy(ParticipantsLookupResultRowBase proxy) {
        this.proxy = proxy;
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
}
