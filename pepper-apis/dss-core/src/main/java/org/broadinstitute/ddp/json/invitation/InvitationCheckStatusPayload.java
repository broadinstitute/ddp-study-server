package org.broadinstitute.ddp.json.invitation;

import java.util.HashMap;
import java.util.Map;
import javax.validation.constraints.NotEmpty;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class InvitationCheckStatusPayload {
    public static final String QUALIFICATION_ZIP_CODE = "zipCode";

    @NotEmpty
    @SerializedName("auth0ClientId")
    private String auth0ClientId;

    @NotEmpty
    @SerializedName("invitationId")
    private String invitationGuid;

    @SerializedName("recaptchaToken")
    private String recaptchaToken;

    @SerializedName("qualificationDetails")
    private final Map<String, Object> qualificationDetails = new HashMap<>();
}
