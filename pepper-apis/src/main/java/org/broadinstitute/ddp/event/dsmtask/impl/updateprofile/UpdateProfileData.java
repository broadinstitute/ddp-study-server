package org.broadinstitute.ddp.event.dsmtask.impl.updateprofile;

import com.google.gson.annotations.SerializedName;

public class UpdateProfileData {

    @SerializedName("event")
    private final String email;
    @SerializedName("firstName")
    private final String firstName;
    @SerializedName("lastName")
    private final String lastName;

    public UpdateProfileData(String email, String firstName, String lastName) {
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }
}
