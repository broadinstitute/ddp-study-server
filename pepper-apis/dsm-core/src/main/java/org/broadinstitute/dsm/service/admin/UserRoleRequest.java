package org.broadinstitute.dsm.service.admin;

import java.util.List;

import lombok.Data;

@Data
public class UserRoleRequest {

    private final List<String> users;

    public UserRoleRequest(List<String> users) {
        this.users = users;
    }
}
