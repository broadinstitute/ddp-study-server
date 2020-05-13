package org.broadinstitute.ddp.json;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.db.dto.InvitationDto;

public class GetInvitationResponse {

    @SerializedName("invitationId")
    private String invitationGuid;
    @SerializedName("createdAt")
    private String createdAt;
    @SerializedName("voidedAt")
    private String voidedAt;
    @SerializedName("verifiedAt")
    private String verifiedAt;
    @SerializedName("acceptedAt")
    private String acceptedAt;
    @SerializedName("notes")
    private String notes;
    @SerializedName("userGuid")
    private String userGuid;
    @SerializedName("userHruid")
    private String userHruid;
    @SerializedName("userLoginEmail")
    private String userLoginEmail;

    public GetInvitationResponse(InvitationDto invitationDto, String userGuid, String userHruid, String userLoginEmail) {
        this.invitationGuid = invitationDto.getInvitationGuid();
        this.createdAt = invitationDto.getCreatedAt().toString();
        this.voidedAt = invitationDto.isVoid() ? invitationDto.getVoidedAt().toString() : null;
        this.verifiedAt = invitationDto.isVerified() ? invitationDto.getVerifiedAt().toString() : null;
        this.acceptedAt = invitationDto.isAccepted() ? invitationDto.getAcceptedAt().toString() : null;
        this.notes = invitationDto.getNotes();
        this.userGuid = userGuid;
        this.userHruid = userHruid;
        this.userLoginEmail = userLoginEmail;
    }

    public String getInvitationGuid() {
        return invitationGuid;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getVoidedAt() {
        return voidedAt;
    }

    public String getVerifiedAt() {
        return verifiedAt;
    }

    public String getAcceptedAt() {
        return acceptedAt;
    }

    public String getNotes() {
        return notes;
    }

    public String getUserGuid() {
        return userGuid;
    }

    public String getUserHruid() {
        return userHruid;
    }

    public String getUserLoginEmail() {
        return userLoginEmail;
    }
}
