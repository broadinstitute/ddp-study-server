package org.broadinstitute.ddp.json;

import java.util.List;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;

public class JoinMailingListPayload {
    @SerializedName("info")
    private List<String> info;

    @SerializedName("studyGuid")
    private String studyGuid;

    @NotNull
    @SerializedName("firstName")
    private String firstName;

    @NotNull
    @SerializedName("lastName")
    private String lastName;

    @NotEmpty
    @Email
    @SerializedName("emailAddress")
    private String emailAddress;

    @SerializedName("umbrellaGuid")
    private String umbrellaGuid;

    public JoinMailingListPayload(
            String firstName, String lastName, String email, String studyGuid,
            List<String> info, String umbrellaGuid
    ) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.emailAddress = email;
        this.studyGuid = studyGuid;
        this.info = info;
        this.umbrellaGuid = umbrellaGuid;
    }

    public List<String> getInfo() {
        return info;
    }

    public String getStudyGuid() {
        return studyGuid;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public String getUmbrellaGuid() {
        return umbrellaGuid;
    }

}
