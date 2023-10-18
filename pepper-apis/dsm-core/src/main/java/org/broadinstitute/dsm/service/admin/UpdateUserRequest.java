package org.broadinstitute.dsm.service.admin;

import java.util.List;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.broadinstitute.dsm.exception.DsmInternalError;

@Data
public class UpdateUserRequest {

    private final List<UpdateUserRequest.User> users;

    public UpdateUserRequest(List<UpdateUserRequest.User> users) {
        this.users = users;
    }

    @Data
    public static class User {
        private String email;
        private final String name;
        private final String phone;

        public User(String email, String name, String phone) {
            this.email = email;
            this.name = name;
            this.phone = phone;
        }

        public UserDto asUserDto() {
            return new UserDto(name, email, phone);
        }
    }
}
