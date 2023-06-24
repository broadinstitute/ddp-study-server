package org.broadinstitute.dsm.service.admin;

import java.util.List;

import lombok.Data;

@Data
public class AddUserRoleRequest {

    private final String email;
    private final String studyGroup;
    private final List<String> roles;

    public AddUserRoleRequest(String email, String studyGroup, List<String> roles) {
        this.email = email;
        this.studyGroup = studyGroup;
        this.roles = roles;
    }

}
