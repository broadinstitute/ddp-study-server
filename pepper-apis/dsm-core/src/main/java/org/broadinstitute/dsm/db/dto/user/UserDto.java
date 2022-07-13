package org.broadinstitute.dsm.db.dto.user;

import java.util.Optional;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
@AllArgsConstructor
public class UserDto {
    private long userId;
    private long dsmLegacyId;
    private String guid;
    private String shortId;
    private String firstName;
    private String lastName;
    private String name;
    private String email;
    private String phoneNumber;
    private String auth0UserId;
    private boolean isActive;

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
