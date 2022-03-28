package org.broadinstitute.ddp.json;

import java.util.List;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class JoinMailingListPayload {
    @NotNull
    @SerializedName("firstName")
    String firstName;

    @NotNull
    @SerializedName("lastName")
    String lastName;

    @NotEmpty
    @Email
    @SerializedName("emailAddress")
    String emailAddress;

    @SerializedName("studyGuid")
    String studyGuid;
    
    @SerializedName("info")
    List<String> info;

    @SerializedName("umbrellaGuid")
    String umbrellaGuid;

    @SerializedName("isoLanguageCode")
    String languageCode;

    public JoinMailingListPayload(
            String firstName, String lastName, String email, String studyGuid,
            List<String> info, String umbrellaGuid
    ) {
        this(firstName, lastName, email, studyGuid, info, umbrellaGuid, null);
    }
}
