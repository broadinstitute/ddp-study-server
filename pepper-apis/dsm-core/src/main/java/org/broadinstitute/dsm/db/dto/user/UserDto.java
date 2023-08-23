package org.broadinstitute.dsm.db.dto.user;

import java.util.Optional;

import lombok.AllArgsConstructor;
import lombok.Setter;
import org.broadinstitute.dsm.exception.DsmInternalError;

@Setter
@AllArgsConstructor
public class UserDto {

    private int id;
    private String name;
    private String email;
    private String phoneNumber;
    private Integer isActive;

    public UserDto(String name, String email, String phoneNumber) {
        this.name = name;
        this.email = email;
        this.phoneNumber = phoneNumber;
    }

    public UserDto() {
    }

    public int getId() {
        return this.id;
    }

    public String getIdAsString() {
        return Integer.toString(this.id);
    }

    public Optional<String> getName() {
        return Optional.ofNullable(name);
    }

    public Optional<String> getEmail() {
        return Optional.ofNullable(email);
    }

    // TODO: getEmail should always throw on a missing email since it is not nullable
    // but there are a lot of callers. Feel free to chase them all down and rename this method -DC
    public String getEmailOrThrow() {
        return getEmail().orElseThrow(() -> new DsmInternalError("User email cannot be null"));
    }

    public Optional<String> getPhoneNumber() {
        return Optional.ofNullable(phoneNumber);
    }
    public Optional<Integer> getIsActive() {
        return Optional.ofNullable(isActive);
    }

    public boolean isActive() {
        return getIsActive().orElse(0) == 1;
    }
}
