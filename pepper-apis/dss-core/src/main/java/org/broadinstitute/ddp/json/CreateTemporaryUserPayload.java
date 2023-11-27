package org.broadinstitute.ddp.json;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Value;

import javax.validation.constraints.NotBlank;

@Value
@AllArgsConstructor
public class CreateTemporaryUserPayload {
    @NotBlank
    @SerializedName("auth0ClientId")
    String auth0ClientId;

    @SerializedName("auth0Domain")
    String auth0Domain;

    @SerializedName("languageCode")
    String languageCode;

    public CreateTemporaryUserPayload(final String auth0ClientId, final String auth0Domain) {
        this(auth0ClientId, auth0Domain, null);
    }
}
