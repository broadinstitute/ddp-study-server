package org.broadinstitute.dsm.service.admin;

import java.util.List;

import lombok.Data;

@Data
public class UpdateUserRoleRequest {

    private final List<String> users;
    private final List<String> addRoles;
    private final List<String> removeRoles;

    public UpdateUserRoleRequest(List<String> users, List<String> addRoles, List<String> removeRoles) {
        this.users = users;
        this.addRoles = addRoles;
        this.removeRoles = removeRoles;
    }
}
