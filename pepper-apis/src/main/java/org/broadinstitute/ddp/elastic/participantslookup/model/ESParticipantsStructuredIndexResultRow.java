package org.broadinstitute.ddp.elastic.participantslookup.model;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.json.admin.participantslookup.ParticipantsLookupResultRowBase;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;

/**
 * Result row fetched during reading from ES index "participants_structures".
 */
public class ESParticipantsStructuredIndexResultRow extends ParticipantsLookupResultRowBase {

    @SerializedName("invitationId")
    protected String invitationId;

    @SerializedName("status")
    private EnrollmentStatusType status;

    public ESParticipantsStructuredIndexResultRow() {}

    public ESParticipantsStructuredIndexResultRow(ParticipantsLookupResultRowBase resultRowBase) {
        super(resultRowBase);
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
