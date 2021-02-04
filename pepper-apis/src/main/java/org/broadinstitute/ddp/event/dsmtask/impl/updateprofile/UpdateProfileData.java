package org.broadinstitute.ddp.event.dsmtask.impl.updateprofile;

import com.google.gson.annotations.SerializedName;

/**
 * Data which could be edited ina user's profile
 * by DsmTask of type 'UPDATE_PROFILE'
 */
public class UpdateProfileData {

    @SerializedName("email")
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
