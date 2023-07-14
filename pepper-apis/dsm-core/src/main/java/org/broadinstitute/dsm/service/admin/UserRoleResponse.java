package org.broadinstitute.dsm.service.admin;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class UserRoleResponse {
    private List<UserInfo> users;

    public UserRoleResponse() {
        this.users = new ArrayList<>();
    }

    public void addUser(UserInfo userInfo) {
        this.users.add(userInfo);
    }
}
