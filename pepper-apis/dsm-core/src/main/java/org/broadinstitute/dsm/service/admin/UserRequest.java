package org.broadinstitute.dsm.service.admin;

import lombok.Data;
import org.broadinstitute.dsm.db.dto.user.UserDto;

@Data
public class UserRequest {

    private final String name;
    private final String email;
    private final String phone;
    private final String studyGroup;

    // TODO extend to include optional roles for user -DC

    public UserRequest(String name, String email, String phone, String studyGroup) {
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.studyGroup = studyGroup;
    }

    public UserDto asUserDto() {
        return new UserDto(-1, name, email, phone);
    }
}
