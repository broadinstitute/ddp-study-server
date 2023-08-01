package org.broadinstitute.dsm.service.admin;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.broadinstitute.dsm.db.dto.user.UserDto;

@AllArgsConstructor
@Data
public class UserInfo {
    private final String email;
    private final String name;
    private final String phone;
    private List<UserRole> roles;

    public UserInfo(String email, String name, String phone) {
        this.email = email;
        this.name = name;
        this.phone = phone;
        this.roles = new ArrayList<>();
    }

    public UserInfo(UserDto userDto) {
        this.email = userDto.getEmailOrThrow();
        this.name = userDto.getName().orElse(null);
        this.phone = userDto.getPhoneNumber().orElse(null);
        this.roles = new ArrayList<>();
    }
}
