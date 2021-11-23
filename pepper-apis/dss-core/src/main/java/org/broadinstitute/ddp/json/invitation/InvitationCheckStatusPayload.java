package org.broadinstitute.ddp.json.invitation;

import java.util.HashMap;
import java.util.Map;
import javax.validation.constraints.NotEmpty;

import com.google.gson.annotations.SerializedName;

public class InvitationCheckStatusPayload {

    public static final String QUALIFICATION_ZIP_CODE = "zipCode";

    @NotEmpty
    @SerializedName("auth0ClientId")
    private String auth0ClientId;

    @NotEmpty
    @SerializedName("invitationId")
    private String invitationGuid;

    @SerializedName("qualificationDetails")
    private Map<String, Object> qualificationDetails = new HashMap<>();

    @SerializedName("recaptchaToken")
    private String recaptchaToken;

    public InvitationCheckStatusPayload(String auth0ClientId, String invitationGuid, String recaptchaUserToken) {
        this.auth0ClientId = auth0ClientId;
        this.invitationGuid = invitationGuid;
        this.recaptchaToken = recaptchaUserToken;
    }

    public String getAuth0ClientId() {
        return auth0ClientId;
    }

    public String getInvitationGuid() {
        return invitationGuid;
    }

    public Map<String, Object> getQualificationDetails() {
        if (qualificationDetails == null) {
            qualificationDetails = new HashMap<>();
        }
        return qualificationDetails;
    }

    public String getRecaptchaToken() {
        return recaptchaToken;
    }
}
