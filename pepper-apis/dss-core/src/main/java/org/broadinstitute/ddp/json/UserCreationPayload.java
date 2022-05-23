package org.broadinstitute.ddp.json;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.model.user.UserProfile;

import javax.validation.constraints.NotBlank;
import java.util.Date;

/**
 * Payload sent during creation of a new user
 */
@Data
@AllArgsConstructor
public class UserCreationPayload {
    @SerializedName("studyGuid")
    private String studyGuid;

    @SerializedName("firstName")
    private String firstName;

    @SerializedName("lastName")
    private String lastName;

    @SerializedName("birthDate")
    private Date birthDate;

    @SerializedName("sex")
    private UserProfile.SexType sex;

    @SerializedName("centerId")
    private Long centerId;
}
