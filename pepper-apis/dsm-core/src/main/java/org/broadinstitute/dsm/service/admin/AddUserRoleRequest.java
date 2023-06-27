package org.broadinstitute.dsm.service.admin;

import java.util.List;

import lombok.Data;

@Data
public class AddUserRoleRequest {

    private final List<String> users;
    private final String studyGroup;
    private final List<String> roles;

    public AddUserRoleRequest(List<String> users, String studyGroup, List<String> roles) {
        this.users = users;
        this.studyGroup = studyGroup;
        this.roles = roles;
    }

}
