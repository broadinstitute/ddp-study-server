package org.broadinstitute.dsm.db.dto.user;

import java.util.Optional;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
public class UserDto {
    @SerializedName ("user_id")
    private long userId;
    private String guid;
    private String firstName;
    private String lastName;
    private String name;
    private String email;
    private String phoneNumber;
    private String auth0UserId;

    public UserDto(long userId, String name, String email, String phoneNumber, String auth0UserId) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.auth0UserId = auth0UserId;
    }

    public long getUserId() {
        return this.userId;
    }

    public Optional<String> getName() {
        if (StringUtils.isBlank(name)) {
            name = firstName + " " + lastName;
        }
        return Optional.ofNullable(name);
    }

    public Optional<String> getEmail() {
        return Optional.ofNullable(email);
    }

    public Optional<String> getPhoneNumber() {
        return Optional.ofNullable(phoneNumber);
    }
}
