package org.broadinstitute.ddp.json.invitation;

import javax.validation.constraints.NotEmpty;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class InvitationVerifyPayload {
    @NotEmpty
    @SerializedName("invitationId")
    String invitationGuid;
}
