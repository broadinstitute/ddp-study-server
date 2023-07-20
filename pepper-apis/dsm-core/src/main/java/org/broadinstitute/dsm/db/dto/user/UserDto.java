package org.broadinstitute.dsm.db.dto.user;

import java.util.Optional;

import lombok.AllArgsConstructor;
import lombok.Setter;

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

    public Optional<String> getName() {
        return Optional.ofNullable(name);
    }

    public Optional<String> getEmail() {
        return Optional.ofNullable(email);
    }

    public Optional<String> getPhoneNumber() {
        return Optional.ofNullable(phoneNumber);
    }
    public Optional<Integer> getIsActive() {
        return Optional.ofNullable(isActive);
    }
}
