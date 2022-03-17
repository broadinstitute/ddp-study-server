package org.broadinstitute.dsm.db.dto.user;

import java.util.Optional;

import com.google.gson.annotations.SerializedName;
import lombok.Setter;

@Setter
public class UserDto {
    @SerializedName ("user_id")
    private int userId;
    private String name;
    private String email;
    private String phoneNumber;

    public UserDto(int userId, String name, String email, String phoneNumber) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.phoneNumber = phoneNumber;
    }

    public int getUserId() {
        return this.userId;
    }

    public Optional<String> getName() {
        return Optional.ofNullable(name);
    }

    public Optional<String> getEmail() {
        return Optional.ofNullable(email);
    }

    public Optional<String> getPhoneNumber() {
        return Optional.ofNullable(phoneNumber);
    }
}
