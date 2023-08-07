package org.broadinstitute.dsm.service.admin;

import java.util.List;

import lombok.Data;

@Data
public class SetUserRoleRequest {

    private final List<String> users;
    private final List<String> roles;

    public SetUserRoleRequest(List<String> users, List<String> roles) {
        this.users = users;
        this.roles = roles;
    }
}

