package org.broadinstitute.dsm.service.admin;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.broadinstitute.dsm.exception.DsmInternalError;

@AllArgsConstructor
@Data
public class UserRequest {

    private final List<UserRequest.User> addUsers;
    private final List<String> removeUsers;

    @Data
    public static class User {
        private String email;
        private final String name;
        private final String phone;
        private final List<String> roles;

        public User(String email, String name, String phone, List<String> roles) {
            this.email = email;
            this.name = name;
            this.phone = phone;
            this.roles = roles;
        }

        public UserDto asUserDto() {
            return new UserDto(name, email, phone);
        }

        public UserDto asUpdatedUserDto(UserDto userDto) {
            if (!email.equalsIgnoreCase(userDto.getEmailOrThrow())) {
                throw new DsmInternalError("Assert: email addresses do not match");
            }
            if (!StringUtils.isBlank(name)) {
                userDto.setName(name);
            }
            if (!StringUtils.isBlank(phone)) {
                userDto.setPhoneNumber(phone);
            }
            return userDto;
        }
    }
}
