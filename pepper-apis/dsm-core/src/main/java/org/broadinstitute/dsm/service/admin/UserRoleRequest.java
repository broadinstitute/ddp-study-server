package org.broadinstitute.dsm.service.admin;

import java.util.List;

import lombok.Data;

@Data
public class UserRoleRequest {

    private final List<String> users;
    private final List<String> roles;

    public UserRoleRequest(List<String> users, List<String> roles) {
        this.users = users;
        this.roles = roles;
    }
}
