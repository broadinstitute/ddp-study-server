package org.broadinstitute.ddp.json;

import javax.validation.constraints.Size;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.broadinstitute.ddp.model.address.MailAddress;

@Value
@AllArgsConstructor
public class VerifyAddressPayload {
    @SerializedName("studyGuid")
    String studyGuid;

    @Size(max = 100)
    @SerializedName("name")
    String name;

    @Size(max = 100)
    @SerializedName("street1")
    String street1;

    @Size(max = 100)
    @SerializedName("street2")
    String street2;

    @Size(max = 100)
    @SerializedName("city")
    String city;

    @Size(max = 100)
    @SerializedName("state")
    String state;

    @Size(max = 100)
    @SerializedName("country")
    String country;

    @Size(max = 100)
    @SerializedName("zip")
    String zip;

    @Size(max = 100)
    @SerializedName("phone")
    String phone;

    public MailAddress toMailAddress() {
        return new MailAddress(
                name, street1, street2,
                city, state, country,
                zip, phone, null, null, null, false);
    }
}
