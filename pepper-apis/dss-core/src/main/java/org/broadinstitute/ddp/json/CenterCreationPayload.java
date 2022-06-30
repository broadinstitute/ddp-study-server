package org.broadinstitute.ddp.json;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;

/**
 * Payload sent during creation of a new user
 */
@Data
@AllArgsConstructor
public class CenterCreationPayload {
    @NonNull
    @SerializedName("name")
    private String name;

    @NonNull
    @SerializedName("address1")
    private String address1;

    @NonNull
    @SerializedName("address2")
    private String address2;

    @NonNull
    @SerializedName("cityId")
    private Long cityId;

    /* Primary contact information */

    @NonNull
    @SerializedName("email")
    private String email;

    @NonNull
    @SerializedName("phone")
    private String phone;

    @NonNull
    @SerializedName("firstName")
    private String firstName;

    @NonNull
    @SerializedName("lastName")
    private String lastName;
}
